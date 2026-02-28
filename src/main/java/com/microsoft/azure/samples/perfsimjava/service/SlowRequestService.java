package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.SlowRequestRequest;
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

/**
 * =============================================================================
 * SLOW REQUEST SERVICE — Multiple Blocking Pattern Simulation
 * =============================================================================
 *
 * PURPOSE:
 *   Simulates slow HTTP responses using multiple blocking strategies, each
 *   demonstrating a different way requests can become slow in real applications.
 *
 * THREE BLOCKING PATTERNS:
 *
 *   1. SLEEP (default) — NON-BLOCKING DELAY
 *      Uses Thread.sleep() which releases the CPU but holds the thread.
 *      Simulates: Slow database queries, external API calls.
 *      Other requests can still be processed by other threads.
 *
 *   2. EXECUTOR_SATURATION — Common ForkJoinPool Exhaustion
 *      Saturates Java's common ForkJoinPool with blocking tasks.
 *      When all FJP threads are busy, parallel streams and CompletableFuture
 *      operations queue up, causing cascading slowdowns.
 *      Similar to Node.js libuv thread pool saturation.
 *
 *   3. SYNC_BLOCKING — Synchronous CPU-Bound Work
 *      Runs CPU-intensive work synchronously on the request thread.
 *      Similar to JDBC calls or synchronous I/O blocking a thread.
 *      The thread cannot serve other requests while blocked.
 */
@Service
public class SlowRequestService {

    private static final Logger logger = LoggerFactory.getLogger(SlowRequestService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    // Scheduler for spawning requests at intervals
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // HTTP client for internal requests
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    
    // Track active simulations for stopping
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    public SlowRequestService(SimulationTrackerService simulationTracker,
                              EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
    }

    /**
     * Starts a slow request simulation that spawns multiple requests at intervals.
     * Returns immediately - requests execute in background.
     */
    public Simulation trigger(SlowRequestRequest request) {
        int delaySeconds = request.getDelaySeconds();
        int intervalSeconds = request.getIntervalSeconds();
        int maxRequests = request.getMaxRequests();
        SlowRequestRequest.BlockingPattern pattern = request.getBlockingPattern();

        // Create simulation record
        Map<String, Object> params = Map.of(
                "type", SimulationType.SLOW_REQUEST,
                "delaySeconds", delaySeconds,
                "intervalSeconds", intervalSeconds,
                "maxRequests", maxRequests,
                "blockingPattern", pattern.name()
        );
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.SLOW_REQUEST,
                params,
                delaySeconds * maxRequests + intervalSeconds * (maxRequests - 1)
        );

        // Initialize stop flag
        stopFlags.put(simulation.getId(), new AtomicBoolean(false));

        // Log the start
        String patternDesc = getPatternDescription(pattern);
        eventLogService.warn(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Slow requests: %d requests at %ds intervals, %ds each (%s)", 
                        maxRequests, intervalSeconds, delaySeconds, patternDesc),
                simulation.getId(),
                SimulationType.SLOW_REQUEST,
                params
        );

        // Spawn requests at intervals using internal HTTP calls
        String blockUrl = String.format("http://localhost:%d/api/simulations/slow/execute?delaySeconds=%d&pattern=%s",
                serverPort, delaySeconds, pattern.name());
        AtomicBoolean stopFlag = stopFlags.get(simulation.getId());
        
        for (int i = 0; i < maxRequests; i++) {
            final int requestNum = i + 1;
            long delayMs = (long) i * intervalSeconds * 1000;
            
            scheduler.schedule(() -> {
                if (stopFlag.get()) {
                    return; // Simulation was stopped
                }
                
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(blockUrl))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(delaySeconds + 30))
                            .build();
                    
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenAccept(response -> {
                                logger.debug("[SlowRequest] Request {} completed", requestNum);
                            });
                            
                } catch (Exception e) {
                    logger.debug("[SlowRequest] Request {} failed: {}", requestNum, e.getMessage());
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }

        // Schedule completion
        long totalDuration = (long) (maxRequests - 1) * intervalSeconds * 1000 + delaySeconds * 1000L;
        scheduler.schedule(() -> {
            stopFlags.remove(simulation.getId());
            simulationTracker.completeSimulation(simulation.getId());
            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_COMPLETED,
                    "Slow request simulation completed",
                    simulation.getId(),
                    SimulationType.SLOW_REQUEST,
                    null
            );
        }, totalDuration + 1000, TimeUnit.MILLISECONDS);

        return simulation;
    }

    /**
     * Executes a single slow request (called by internal HTTP requests).
     */
    public void executeSingleRequest(int delaySeconds, SlowRequestRequest.BlockingPattern pattern) {
        try {
            switch (pattern) {
                case SLEEP -> blockWithSleep(delaySeconds);
                case EXECUTOR_SATURATION -> blockWithExecutorSaturation(delaySeconds);
                case SYNC_BLOCKING -> blockWithSyncWork(delaySeconds);
            }
        } catch (Exception e) {
            logger.warn("Slow request execution failed: {}", e.getMessage());
        }
    }

    /**
     * Stops all active slow request simulations.
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
        eventLogService.info(
                EventLogEntry.EventType.SIMULATION_STOPPED,
                "Slow request simulations stopped",
                null,
                SimulationType.SLOW_REQUEST,
                null
        );
    }

    /**
     * Gets all active slow request simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.SLOW_REQUEST);
    }

    /**
     * Simple sleep-based delay. Releases CPU but holds thread.
     */
    private void blockWithSleep(int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
    }

    /**
     * Saturates the common ForkJoinPool with blocking tasks.
     */
    private void blockWithExecutorSaturation(int seconds) {
        int poolSize = ForkJoinPool.commonPool().getParallelism();
        long endTime = System.currentTimeMillis() + (seconds * 1000L);

        CompletableFuture<?>[] futures = new CompletableFuture[poolSize];
        for (int i = 0; i < poolSize; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    while (System.currentTimeMillis() < endTime) {
                        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
                        PBEKeySpec spec = new PBEKeySpec("pwd".toCharArray(), "s".getBytes(), 1000, 128);
                        factory.generateSecret(spec);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
        }
        CompletableFuture.allOf(futures).join();
    }

    /**
     * Synchronous CPU-bound blocking work.
     */
    private void blockWithSyncWork(int seconds) {
        long endTime = System.currentTimeMillis() + (seconds * 1000L);

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");

            while (System.currentTimeMillis() < endTime) {
                PBEKeySpec spec = new PBEKeySpec(
                        "password".toCharArray(),
                        "salt".getBytes(),
                        10000,
                        512
                );
                factory.generateSecret(spec);
                spec.clearPassword();
            }
        } catch (Exception e) {
            logger.warn("Sync blocking work failed: {}", e.getMessage());
        }
    }

    /**
     * Gets human-readable pattern description.
     */
    private String getPatternDescription(SlowRequestRequest.BlockingPattern pattern) {
        return switch (pattern) {
            case SLEEP -> "Thread.sleep (non-blocking to pool)";
            case EXECUTOR_SATURATION -> "ForkJoinPool saturation";
            case SYNC_BLOCKING -> "Synchronous CPU-bound blocking";
        };
    }
}
