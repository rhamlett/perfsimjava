package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.SlowRequestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

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
 *
 * PORTING NOTES:
 *   - Node.js: setTimeout (non-blocking), libuv saturation, worker threads
 *   - Python: asyncio.sleep (non-blocking), ThreadPoolExecutor saturation
 *   - C#: Task.Delay (non-blocking), ThreadPool saturation
 *   - PHP: sleep() blocks, no equivalent for pool saturation
 */
@Service
public class SlowRequestService {

    private static final Logger logger = LoggerFactory.getLogger(SlowRequestService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;

    public SlowRequestService(SimulationTrackerService simulationTracker,
                              EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
    }

    /**
     * Executes a slow request with the specified blocking pattern.
     *
     * @param request The slow request parameters
     * @return The completed simulation
     */
    public Simulation delay(SlowRequestRequest request) {
        int delaySeconds = request.getDelaySeconds();
        SlowRequestRequest.BlockingPattern pattern = request.getBlockingPattern();

        // Create simulation record
        Map<String, Object> params = Map.of(
                "type", SimulationType.SLOW_REQUEST,
                "delaySeconds", delaySeconds,
                "blockingPattern", pattern.name()
        );
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.SLOW_REQUEST,
                params,
                delaySeconds
        );

        // Log the start
        String patternDesc = getPatternDescription(pattern);
        eventLogService.info(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Slow request started: %ds delay (%s)", delaySeconds, patternDesc),
                simulation.getId(),
                SimulationType.SLOW_REQUEST,
                params
        );

        try {
            // Execute blocking pattern
            switch (pattern) {
                case SLEEP -> blockWithSleep(delaySeconds);
                case EXECUTOR_SATURATION -> blockWithExecutorSaturation(delaySeconds);
                case SYNC_BLOCKING -> blockWithSyncWork(delaySeconds);
            }

            // Mark completed
            simulationTracker.completeSimulation(simulation.getId());
            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_COMPLETED,
                    String.format("Slow request completed (%s)", patternDesc),
                    simulation.getId(),
                    SimulationType.SLOW_REQUEST,
                    null
            );

        } catch (Exception e) {
            simulationTracker.failSimulation(simulation.getId());
            eventLogService.error(
                    EventLogEntry.EventType.SIMULATION_FAILED,
                    "Slow request failed: " + e.getMessage(),
                    simulation.getId(),
                    SimulationType.SLOW_REQUEST,
                    null
            );
        }

        return simulationTracker.getSimulation(simulation.getId());
    }

    /**
     * Simple sleep-based delay. Releases CPU but holds thread.
     */
    private void blockWithSleep(int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
    }

    /**
     * Saturates the common ForkJoinPool with blocking tasks.
     * This affects all parallel streams and default CompletableFuture operations.
     */
    private void blockWithExecutorSaturation(int seconds) {
        int poolSize = ForkJoinPool.commonPool().getParallelism();
        long endTime = System.currentTimeMillis() + (seconds * 1000L);

        // Submit tasks to saturate the common pool
        CompletableFuture<?>[] futures = new CompletableFuture[poolSize];
        for (int i = 0; i < poolSize; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    while (System.currentTimeMillis() < endTime) {
                        // Small crypto work to keep the thread busy
                        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
                        PBEKeySpec spec = new PBEKeySpec("pwd".toCharArray(), "s".getBytes(), 1000, 128);
                        factory.generateSecret(spec);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
        }

        // Wait for all to complete
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
