package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.ConnectionPoolRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * =============================================================================
 * CONNECTION POOL EXHAUSTION SERVICE
 * =============================================================================
 *
 * PURPOSE:
 *   Simulates database connection pool exhaustion - one of the most common
 *   performance issues in Java applications using JDBC.
 *
 * HOW IT WORKS:
 *   - A Semaphore simulates a fixed-size connection pool (like HikariCP)
 *   - Each "query" acquires a permit, holds it for the query duration, then releases
 *   - When pool is exhausted, new requests wait or timeout
 *   - Thread dumps show threads WAITING on the semaphore (looks like real pool exhaustion)
 *
 * DIAGNOSTIC VALUE:
 *   - Thread dumps: Multiple threads in TIMED_WAITING state on Semaphore.tryAcquire
 *   - Application Insights: Request timeouts, increased latency
 *   - Common real-world scenario: HikariCP, Tomcat JDBC, C3P0 pool exhaustion
 *
 * REAL-WORLD PARALLELS:
 *   - Slow database queries holding connections too long
 *   - Connection leaks (connections not returned to pool)
 *   - Undersized pool for traffic volume
 *   - Deadlocked database connections
 */
@Service
public class ConnectionPoolService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;

    // Simulated connection pool - configurable size
    private volatile Semaphore connectionPool;
    private volatile int currentPoolSize = 10;
    
    // Track active simulations
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    
    // Executor for running concurrent queries
    private final ExecutorService queryExecutor = Executors.newCachedThreadPool();
    
    // Track statistics
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger timedOutRequests = new AtomicInteger(0);
    private final AtomicInteger successfulQueries = new AtomicInteger(0);

    public ConnectionPoolService(SimulationTrackerService simulationTracker,
                                  EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
        this.connectionPool = new Semaphore(currentPoolSize);
    }

    /**
     * Triggers a connection pool exhaustion simulation.
     * Spawns multiple concurrent "queries" that hold connections.
     */
    public Simulation trigger(ConnectionPoolRequest request) {
        int poolSize = request.getPoolSize();
        int queryDurationSeconds = request.getQueryDurationSeconds();
        int concurrentQueries = request.getConcurrentQueries();
        int connectionTimeoutSeconds = request.getConnectionTimeoutSeconds();

        // Reset pool with new size if changed
        if (poolSize != currentPoolSize) {
            currentPoolSize = poolSize;
            connectionPool = new Semaphore(poolSize);
        }
        
        // Reset statistics
        timedOutRequests.set(0);
        successfulQueries.set(0);

        // Create simulation record
        Map<String, Object> params = Map.of(
                "type", SimulationType.CONNECTION_POOL_EXHAUSTION,
                "poolSize", poolSize,
                "queryDurationSeconds", queryDurationSeconds,
                "concurrentQueries", concurrentQueries,
                "connectionTimeoutSeconds", connectionTimeoutSeconds
        );
        
        int estimatedDuration = (int) Math.ceil((double) concurrentQueries / poolSize) * queryDurationSeconds;
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.CONNECTION_POOL_EXHAUSTION,
                params,
                estimatedDuration
        );

        // Initialize stop flag
        String simId = simulation.getId();
        stopFlags.put(simId, new AtomicBoolean(false));
        AtomicBoolean stopFlag = stopFlags.get(simId);

        // Log the start
        eventLogService.warn(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Connection Pool Exhaustion: %d queries competing for %d connections (%ds query, %ds timeout)",
                        concurrentQueries, poolSize, queryDurationSeconds, connectionTimeoutSeconds),
                simId,
                SimulationType.CONNECTION_POOL_EXHAUSTION,
                params
        );

        // Track completion
        AtomicInteger completedQueries = new AtomicInteger(0);
        
        // Spawn concurrent queries
        for (int i = 0; i < concurrentQueries; i++) {
            final int queryNum = i + 1;
            
            queryExecutor.submit(() -> {
                if (stopFlag.get()) return;
                
                boolean acquired = false;
                try {
                    logger.debug("[ConnectionPool] Query {} waiting for connection...", queryNum);
                    
                    // Try to acquire a "connection" from the pool
                    acquired = connectionPool.tryAcquire(connectionTimeoutSeconds, TimeUnit.SECONDS);
                    
                    if (!acquired) {
                        // Connection timeout - this is what we want to demonstrate!
                        timedOutRequests.incrementAndGet();
                        logger.info("[ConnectionPool] Query {} TIMED OUT waiting for connection", queryNum);
                        return;
                    }
                    
                    if (stopFlag.get()) {
                        connectionPool.release();
                        return;
                    }
                    
                    // Got a connection - simulate slow query
                    activeConnections.incrementAndGet();
                    logger.debug("[ConnectionPool] Query {} executing (active: {})", 
                            queryNum, activeConnections.get());
                    
                    // Simulate the slow database query
                    long endTime = System.currentTimeMillis() + (queryDurationSeconds * 1000L);
                    while (System.currentTimeMillis() < endTime && !stopFlag.get()) {
                        Thread.sleep(100); // Check stop flag periodically
                    }
                    
                    successfulQueries.incrementAndGet();
                    logger.debug("[ConnectionPool] Query {} completed", queryNum);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("[ConnectionPool] Query {} interrupted", queryNum);
                } finally {
                    if (acquired) {
                        activeConnections.decrementAndGet();
                        connectionPool.release();
                    }
                    
                    // Check if all queries completed
                    if (completedQueries.incrementAndGet() >= concurrentQueries) {
                        completeSimulation(simId);
                    }
                }
            });
        }

        return simulation;
    }

    /**
     * Completes the simulation and logs results.
     */
    private void completeSimulation(String simulationId) {
        stopFlags.remove(simulationId);
        simulationTracker.completeSimulation(simulationId);
        
        String summary = String.format("Pool exhaustion complete: %d successful, %d timed out",
                successfulQueries.get(), timedOutRequests.get());
        
        eventLogService.info(
                EventLogEntry.EventType.SIMULATION_COMPLETED,
                summary,
                simulationId,
                SimulationType.CONNECTION_POOL_EXHAUSTION,
                Map.of("successful", successfulQueries.get(), "timedOut", timedOutRequests.get())
        );
    }

    /**
     * Stops all active connection pool simulations.
     */
    public void stopAll() {
        List<String> ids = List.copyOf(stopFlags.keySet());
        for (String id : ids) {
            AtomicBoolean flag = stopFlags.get(id);
            if (flag != null) {
                flag.set(true);
            }
            simulationTracker.completeSimulation(id);
            stopFlags.remove(id);
        }
        
        // Reset pool
        connectionPool = new Semaphore(currentPoolSize);
        activeConnections.set(0);
        
        eventLogService.info(
                EventLogEntry.EventType.SIMULATION_STOPPED,
                "Connection pool simulation stopped",
                null,
                SimulationType.CONNECTION_POOL_EXHAUSTION,
                null
        );
    }

    /**
     * Gets current pool statistics.
     */
    public Map<String, Object> getPoolStats() {
        return Map.of(
                "poolSize", currentPoolSize,
                "availableConnections", connectionPool.availablePermits(),
                "activeConnections", activeConnections.get(),
                "waitingThreads", connectionPool.getQueueLength(),
                "successfulQueries", successfulQueries.get(),
                "timedOutRequests", timedOutRequests.get()
        );
    }

    /**
     * Gets all active simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.CONNECTION_POOL_EXHAUSTION);
    }
}
