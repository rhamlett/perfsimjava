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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
 * HOW IT WORKS IN NODE.JS vs JAVA:
 *   Node.js (single-threaded event loop):
 *   - One thread handles ALL requests
 *   - Blocking = server completely unresponsive
 *   - Even 1 blocking operation stops everything
 *
 *   Java (thread-per-request model):
 *   - Each request gets its own thread from a pool (e.g., Tomcat default: 200)
 *   - Blocking one thread only affects that one request
 *   - To exhaust the pool, need to block MANY threads simultaneously
 *   - Once pool is exhausted, new requests queue and eventually timeout
 *
 * SIMULATION APPROACH:
 *   1. Spawn N threads that run blocking operations
 *   2. Each thread performs synchronous crypto work (PBKDF2)
 *   3. Threads hold their servlet thread for the full duration
 *   4. When threadCount >= Tomcat's max-threads, new requests queue
 *   5. Dashboard shows latency spikes as requests wait in queue
 *
 * PORTING NOTES:
 *   - Node.js: pbkdf2Sync in main thread blocks everything
 *   - Python asyncio: time.sleep() blocks, asyncio.sleep() doesn't
 *   - C#: Thread.Sleep() in request handler, ASP.NET has limited threads
 *   - PHP: usleep() blocks the current request handler
 *
 *   The key insight: in thread-per-request models, you need to exhaust
 *   the thread pool to see the same effect as blocking Node's event loop.
 */
@Service
public class ThreadStarvationService {

    private static final Logger logger = LoggerFactory.getLogger(ThreadStarvationService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;

    public ThreadStarvationService(SimulationTrackerService simulationTracker, 
                                   EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
    }

    /**
     * Triggers thread starvation by blocking multiple servlet threads.
     *
     * @param request The starvation parameters
     * @return The simulation result (completes when done)
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

        // Log the start with warning
        eventLogService.warn(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Thread starvation started for %ds with %d threads - server may become unresponsive!",
                        durationSeconds, threadCount),
                simulation.getId(),
                SimulationType.THREAD_STARVATION,
                params
        );

        // Run the blocking simulation asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                runBlockingSimulation(simulation.getId(), durationSeconds, threadCount);
                simulationTracker.completeSimulation(simulation.getId());
                eventLogService.info(
                        EventLogEntry.EventType.SIMULATION_COMPLETED,
                        "Thread starvation simulation completed",
                        simulation.getId(),
                        SimulationType.THREAD_STARVATION,
                        null
                );
            } catch (Exception e) {
                simulationTracker.failSimulation(simulation.getId());
                eventLogService.error(
                        EventLogEntry.EventType.SIMULATION_FAILED,
                        "Thread starvation failed: " + e.getMessage(),
                        simulation.getId(),
                        SimulationType.THREAD_STARVATION,
                        null
                );
            }
        });

        return simulation;
    }

    /**
     * Runs the blocking simulation by spawning threads that hold resources.
     */
    private void runBlockingSimulation(String simulationId, int durationSeconds, int threadCount) 
            throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        // Submit blocking tasks
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    blockThread(endTime, threadNum);
                } finally {
                    latch.countDown();
                }
            }, executor));
        }

        // Wait for all threads to complete
        latch.await(durationSeconds + 5, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    /**
     * Blocks a thread with CPU-intensive work until the end time.
     * This simulates a sync-over-async anti-pattern where servlet threads
     * are held by blocking operations.
     */
    private void blockThread(long endTime, int threadNum) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");

            while (System.currentTimeMillis() < endTime) {
                // Perform blocking crypto work
                PBEKeySpec spec = new PBEKeySpec(
                        "password".toCharArray(),
                        "salt".getBytes(),
                        5000,   // iterations - shorter for more frequent checks
                        256     // key length
                );
                factory.generateSecret(spec);
                spec.clearPassword();
            }
        } catch (Exception e) {
            logger.warn("Thread {} blocking work failed: {}", threadNum, e.getMessage());
        }
    }

    /**
     * Gets all active thread starvation simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.THREAD_STARVATION);
    }
}
