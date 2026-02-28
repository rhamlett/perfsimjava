package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.ConnectionPoolRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 *   - Spawns many internal HTTP requests that each try to acquire a "connection"
 *   - A Semaphore simulates a fixed-size connection pool (like HikariCP)
 *   - When pool is exhausted, SERVLET THREADS block waiting for connections
 *   - This causes actual latency impact because servlet threads are consumed
 *
 * KEY DIFFERENCE FROM BACKGROUND EXECUTION:
 *   Real JDBC pool exhaustion blocks the servlet thread making the DB call.
 *   By using internal HTTP requests, we ensure servlet threads are the ones
 *   waiting on the semaphore, causing real latency impact.
 *
 * DIAGNOSTIC VALUE:
 *   - Thread dumps: Servlet threads in TIMED_WAITING on Semaphore.tryAcquire
 *   - Latency spikes: Probe requests queue behind blocked threads
 *   - Common real-world scenario: HikariCP, Tomcat JDBC, C3P0 pool exhaustion
 */
@Service
public class ConnectionPoolService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;

    @Value("${server.port:8080}")
    private int serverPort;

    // Simulated connection pool - configurable size
    private volatile Semaphore connectionPool;
    private volatile int currentPoolSize = 10;
    private volatile int currentQueryDuration = 30;
    private volatile int currentTimeout = 5;
    
    // Track active simulations
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    
    // Scheduler for spawning requests
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // HTTP client for internal requests (bypasses browser connection limit)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    
    // Track statistics
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger timedOutRequests = new AtomicInteger(0);
    private final AtomicInteger successfulQueries = new AtomicInteger(0);
    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    
    // Track completion (separate from pendingQueries for thread-safety)
    private final AtomicInteger completedQueries = new AtomicInteger(0);
    private volatile int totalQueriesForCompletion = 0;
    private volatile String activeSimulationId = null;

    public ConnectionPoolService(SimulationTrackerService simulationTracker,
                                  EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
        this.connectionPool = new Semaphore(currentPoolSize);
    }

    /**
     * Triggers a connection pool exhaustion simulation.
     * Spawns internal HTTP requests that block servlet threads on the pool.
     */
    public Simulation trigger(ConnectionPoolRequest request) {
        int poolSize = request.getPoolSize();
        int queryDurationSeconds = request.getQueryDurationSeconds();
        int concurrentQueries = request.getConcurrentQueries();
        int connectionTimeoutSeconds = request.getConnectionTimeoutSeconds();

        // Reset pool with new size
        currentPoolSize = poolSize;
        currentQueryDuration = queryDurationSeconds;
        currentTimeout = connectionTimeoutSeconds;
        connectionPool = new Semaphore(poolSize);
        
        // Reset statistics
        timedOutRequests.set(0);
        successfulQueries.set(0);
        pendingQueries.set(concurrentQueries);
        completedQueries.set(0);
        totalQueriesForCompletion = concurrentQueries;

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
        activeSimulationId = simId;
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

        // Spawn all queries immediately via internal HTTP requests
        // This blocks SERVLET threads, not background threads
        String queryUrl = String.format("http://localhost:%d/api/simulations/connection-pool/query", serverPort);
        
        for (int i = 0; i < concurrentQueries; i++) {
            if (stopFlag.get()) break;
            
            final int queryNum = i + 1;
            
            // Small stagger to avoid thundering herd (10ms between each)
            scheduler.schedule(() -> {
                if (stopFlag.get()) {
                    // Query skipped due to stop - still counts as completed
                    markQueryCompleted(simId);
                    return;
                }
                
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(queryUrl))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(queryDurationSeconds + connectionTimeoutSeconds + 10))
                            .build();
                    
                    // Fire and forget - the servlet thread handles the blocking
                    // Completion tracking happens in executeQuery(), not here
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .whenComplete((response, error) -> {
                                if (error != null) {
                                    // HTTP failed (timeout, connection refused, etc.)
                                    // executeQuery never ran, so we need to mark completion here
                                    logger.debug("[ConnectionPool] Query {} HTTP failed: {}", queryNum, error.getMessage());
                                    pendingQueries.decrementAndGet();
                                    markQueryCompleted(simId);
                                }
                                // Success case: executeQuery() handles the completion
                            });
                            
                } catch (Exception e) {
                    logger.debug("[ConnectionPool] Query {} spawn failed: {}", queryNum, e.getMessage());
                    pendingQueries.decrementAndGet();
                    markQueryCompleted(simId);
                }
            }, i * 10L, TimeUnit.MILLISECONDS);
        }

        return simulation;
    }

    /**
     * Executes a single query (called by internal HTTP endpoint).
     * This method runs on a SERVLET THREAD and blocks waiting for a connection.
     */
    public Map<String, Object> executeQuery() {
        AtomicBoolean anyStopFlag = stopFlags.values().stream().findFirst().orElse(null);
        String simId = activeSimulationId;
        
        if (anyStopFlag != null && anyStopFlag.get()) {
            pendingQueries.decrementAndGet();
            markQueryCompleted(simId);
            return Map.of("status", "stopped", "acquired", false);
        }
        
        boolean acquired = false;
        try {
            logger.debug("[ConnectionPool] Servlet thread waiting for connection...");
            
            // This blocks the SERVLET THREAD - the key to causing latency!
            acquired = connectionPool.tryAcquire(currentTimeout, TimeUnit.SECONDS);
            
            if (!acquired) {
                // Connection timeout
                timedOutRequests.incrementAndGet();
                logger.debug("[ConnectionPool] Query TIMED OUT after {}s", currentTimeout);
                return Map.of("status", "timeout", "acquired", false);
            }
            
            // Check stop flag after acquiring
            if (anyStopFlag != null && anyStopFlag.get()) {
                connectionPool.release();
                return Map.of("status", "stopped", "acquired", false);
            }
            
            // Got a connection - simulate slow query (still blocking servlet thread!)
            activeConnections.incrementAndGet();
            logger.debug("[ConnectionPool] Executing query (active connections: {})", activeConnections.get());
            
            // Simulate the slow database query
            long endTime = System.currentTimeMillis() + (currentQueryDuration * 1000L);
            while (System.currentTimeMillis() < endTime) {
                if (anyStopFlag != null && anyStopFlag.get()) {
                    break;
                }
                Thread.sleep(100); // Check stop flag periodically
            }
            
            successfulQueries.incrementAndGet();
            return Map.of("status", "success", "acquired", true, "durationMs", currentQueryDuration * 1000);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("status", "interrupted", "acquired", acquired);
        } finally {
            if (acquired) {
                activeConnections.decrementAndGet();
                connectionPool.release();
            }
            pendingQueries.decrementAndGet();
            markQueryCompleted(simId);
        }
    }

    /**
     * Marks a query as completed and checks if simulation is done.
     * Thread-safe: uses atomic increment and compares against total.
     */
    private void markQueryCompleted(String simulationId) {
        int completed = completedQueries.incrementAndGet();
        if (completed >= totalQueriesForCompletion && simulationId != null) {
            completeSimulation(simulationId);
        }
    }

    /**
     * Completes the simulation and logs results.
     */
    private void completeSimulation(String simulationId) {
        if (stopFlags.remove(simulationId) == null) {
            return; // Already completed
        }
        
        activeSimulationId = null;
        simulationTracker.completeSimulation(simulationId);;
        
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
        }
        
        // Wait briefly for threads to see stop flag
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Clear flags and reset pool
        stopFlags.clear();
        connectionPool = new Semaphore(currentPoolSize);
        activeConnections.set(0);
        pendingQueries.set(0);
        completedQueries.set(0);
        totalQueriesForCompletion = 0;
        activeSimulationId = null;
        
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
                "pendingQueries", pendingQueries.get(),
                "completedQueries", completedQueries.get(),
                "totalQueries", totalQueriesForCompletion,
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
