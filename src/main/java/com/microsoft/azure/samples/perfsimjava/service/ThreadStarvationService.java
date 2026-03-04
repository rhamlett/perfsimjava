package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.ThreadStarvationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
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
 * THREAD STARVATION SERVICE — Servlet Thread Pool Exhaustion Simulation
 * =============================================================================
 *
 * PURPOSE:
 *   Simulates the effect of blocking operations exhausting the servlet thread pool.
 *   This is the Java equivalent of "Event Loop Blocking" in Node.js, but with
 *   different mechanics due to Java's thread-per-request model.
 *
 * HOW IT WORKS:
 *   The server spawns N internal HTTP requests to a /block endpoint on itself.
 *   Each request blocks its Tomcat servlet thread with CPU-intensive work.
 *   When blocked threads approach Tomcat's max-threads (200), new requests queue.
 *   The probe endpoint starts timing out, showing latency spikes.
 *
 * WHY INTERNAL REQUESTS:
 *   Browsers limit concurrent connections to ~6 per domain (HTTP/1.1 limit).
 *   By spawning requests server-side, we bypass this browser limitation.
 */
@Service
public class ThreadStarvationService {

    private static final Logger logger = LoggerFactory.getLogger(ThreadStarvationService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    // HTTP client for internal requests (unlimited connections)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    
    // Executor for spawning internal requests (not for blocking - just to initiate)
    private final ExecutorService requestSpawner = Executors.newCachedThreadPool();
    
    // Track active simulations and their stop flags
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeBlockers = new ConcurrentHashMap<>();

    public ThreadStarvationService(SimulationTrackerService simulationTracker, 
                                   EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
    }

    /**
     * Creates and triggers a thread starvation simulation.
     * Spawns N internal HTTP requests to the /block endpoint.
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

        // Log the start
        eventLogService.warn(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Thread starvation started - spawning %d blocking requests for %ds",
                        threadCount, durationSeconds),
                simulation.getId(),
                SimulationType.THREAD_STARVATION,
                params
        );

        // Spawn internal HTTP requests to block servlet threads
        String simulationId = simulation.getId();
        String blockUrl = String.format("http://localhost:%d/api/simulations/thread/starvation/block?simulationId=%s&durationSeconds=%d",
                serverPort, simulationId, durationSeconds);
        
        logger.info("[ThreadStarvation] Spawning {} internal requests to {}", threadCount, blockUrl);
        
        for (int i = 0; i < threadCount; i++) {
            final int requestNum = i;
            requestSpawner.submit(() -> {
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(blockUrl))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(durationSeconds + 30))
                            .build();
                    
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    logger.debug("[ThreadStarvation] Request {} failed (may be expected): {}", 
                            requestNum, e.getMessage());
                }
            });
        }

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
        int currentCount = 0;
        if (blockerCount != null) {
            currentCount = blockerCount.incrementAndGet();
            // Log when a new wave of blocking requests begins
            if (currentCount == 1 || currentCount % 50 == 0) {
                eventLogService.info(
                        EventLogEntry.EventType.INFO,
                        String.format("Thread starvation: %d threads now blocking (wave in progress)", currentCount),
                        simulationId,
                        SimulationType.THREAD_STARVATION,
                        null
                );
            }
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
                
                // Log progress when threads complete
                if (remaining > 0 && remaining % 50 == 0) {
                    eventLogService.info(
                            EventLogEntry.EventType.INFO,
                            String.format("Thread starvation: %d threads still blocking (queued requests may start next wave)", remaining),
                            simulationId,
                            SimulationType.THREAD_STARVATION,
                            null
                    );
                }
                
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
        // Copy keys to avoid ConcurrentModificationException
        List<String> ids = List.copyOf(stopFlags.keySet());
        for (String id : ids) {
            stop(id);
        }
    }

    /**
     * Stops a specific thread starvation simulation.
     */
    public boolean stop(String simulationId) {
        AtomicBoolean stopFlag = stopFlags.get(simulationId);
        if (stopFlag != null) {
            // Set flag first - blocking threads check this in their loop
            stopFlag.set(true);
            
            simulationTracker.completeSimulation(simulationId);
            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_STOPPED,
                    "Thread starvation simulation stopped by user",
                    simulationId,
                    SimulationType.THREAD_STARVATION,
                    null
            );
            
            // Delay cleanup to give blocking threads time to see the stop flag
            // They check stopFlag.get() in their while loop
            // Don't remove immediately - let threads exit gracefully first
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
                stopFlags.remove(simulationId);
                activeBlockers.remove(simulationId);
            });
            
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
