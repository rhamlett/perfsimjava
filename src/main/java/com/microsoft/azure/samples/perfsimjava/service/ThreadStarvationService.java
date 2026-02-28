package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.ThreadStarvationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * =============================================================================
 * THREAD STARVATION SERVICE — Servlet Thread Pool Exhaustion Simulation
 * =============================================================================
 *
 * PURPOSE:
 *   Simulates the effect of blocking operations exhausting the servlet thread pool.
 *   This is the Java equivalent of "Event Loop Blocking" in Node.js, but with
 *   different mechanics due to Java's thread-per-request model.
 *
 * HOW IT WORKS:
 *   The dashboard makes N concurrent HTTP requests to the /block endpoint.
 *   Each request blocks its Tomcat servlet thread with CPU-intensive work.
 *   When blocked threads approach Tomcat's max-threads (200), new requests queue.
 *   The probe endpoint starts timing out, showing latency spikes.
 *
 * WHY THIS APPROACH:
 *   Creating a separate ExecutorService does NOT block servlet threads!
 *   We must block INSIDE the HTTP request handler to tie up Tomcat's pool.
 */
@Service
public class ThreadStarvationService {

    private static final Logger logger = LoggerFactory.getLogger(ThreadStarvationService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    
    // Track active simulations and their stop flags
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeBlockers = new ConcurrentHashMap<>();

    public ThreadStarvationService(SimulationTrackerService simulationTracker, 
                                   EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
    }

    /**
     * Creates a thread starvation simulation record.
     * The actual blocking is done via concurrent HTTP requests to /block endpoint.
     */
    public Simulation trigger(ThreadStarvationRequest request) {
        int durationSeconds = request.getDurationSeconds();
        int threadCount = request.getThreadCount();

        // Create simulation record
        Map<String, Object> params = Map.of(
                "type", SimulationType.THREAD_STARVATION,
                "durationSeconds", durationSeconds,
                "threadCount", threadCount
        );
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.THREAD_STARVATION,
                params,
                durationSeconds
        );
        
        // Initialize tracking
        stopFlags.put(simulation.getId(), new AtomicBoolean(false));
        activeBlockers.put(simulation.getId(), new AtomicInteger(0));

        // Log the start with warning
        eventLogService.warn(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Thread starvation started - %d requests will block servlet threads for %ds",
                        threadCount, durationSeconds),
                simulation.getId(),
                SimulationType.THREAD_STARVATION,
                params
        );

        return simulation;
    }

    /**
     * Blocks the calling servlet thread with CPU-intensive work.
     * Called from the /block HTTP endpoint - each call ties up one Tomcat thread.
     * 
     * @param simulationId The simulation to associate with
     * @param durationSeconds How long to block
     * @return true if blocked successfully, false if stopped early
     */
    public boolean blockServletThread(String simulationId, int durationSeconds) {
        AtomicBoolean stopFlag = stopFlags.get(simulationId);
        AtomicInteger blockerCount = activeBlockers.get(simulationId);
        
        if (stopFlag == null || stopFlag.get()) {
            return false; // Simulation stopped or doesn't exist
        }
        
        // Track active blocker count
        if (blockerCount != null) {
            blockerCount.incrementAndGet();
        }
        
        try {
            long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            
            // Block this servlet thread with CPU work until duration expires or stopped
            while (System.currentTimeMillis() < endTime && !stopFlag.get()) {
                PBEKeySpec spec = new PBEKeySpec(
                        "password".toCharArray(),
                        "salt".getBytes(),
                        10000,  // iterations - CPU intensive
                        256
                );
                factory.generateSecret(spec);
                spec.clearPassword();
            }
            
            return !stopFlag.get(); // Return true if completed normally
            
        } catch (Exception e) {
            logger.warn("Block operation failed: {}", e.getMessage());
            return false;
        } finally {
            if (blockerCount != null) {
                int remaining = blockerCount.decrementAndGet();
                
                // Check if this was the last blocker
                if (remaining <= 0 && stopFlags.containsKey(simulationId)) {
                    completeSimulation(simulationId);
                }
            }
        }
    }

    /**
     * Checks if a simulation is still active.
     */
    public boolean isSimulationActive(String simulationId) {
        AtomicBoolean stopFlag = stopFlags.get(simulationId);
        return stopFlag != null && !stopFlag.get();
    }

    /**
     * Gets the count of currently blocking threads for a simulation.
     */
    public int getActiveBlockerCount(String simulationId) {
        AtomicInteger count = activeBlockers.get(simulationId);
        return count != null ? count.get() : 0;
    }

    /**
     * Completes a simulation and cleans up.
     */
    private void completeSimulation(String simulationId) {
        stopFlags.remove(simulationId);
        activeBlockers.remove(simulationId);
        simulationTracker.completeSimulation(simulationId);
        eventLogService.info(
                EventLogEntry.EventType.SIMULATION_COMPLETED,
                "Thread starvation simulation completed",
                simulationId,
                SimulationType.THREAD_STARVATION,
                null
        );
    }

    /**
     * Stops all active thread starvation simulations.
     */
    public void stopAll() {
        for (String id : stopFlags.keySet()) {
            stop(id);
        }
    }

    /**
     * Stops a specific thread starvation simulation.
     */
    public boolean stop(String simulationId) {
        AtomicBoolean stopFlag = stopFlags.get(simulationId);
        if (stopFlag != null) {
            stopFlag.set(true);
            simulationTracker.completeSimulation(simulationId);
            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_STOPPED,
                    "Thread starvation simulation stopped by user",
                    simulationId,
                    SimulationType.THREAD_STARVATION,
                    null
            );
            // Cleanup will happen when last blocker exits
            stopFlags.remove(simulationId);
            activeBlockers.remove(simulationId);
            return true;
        }
        return false;
    }

    /**
     * Gets all active thread starvation simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.THREAD_STARVATION);
    }
}
