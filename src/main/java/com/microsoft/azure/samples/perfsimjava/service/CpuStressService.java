package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.CpuStressRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * =============================================================================
 * CPU STRESS SERVICE — CPU-Intensive Workload Simulation
 * =============================================================================
 *
 * PURPOSE:
 *   Simulates high CPU usage by spawning threads that perform CPU-intensive
 *   cryptographic operations. Each thread runs at 100% CPU on its core.
 *   The simulation is visible in system-wide CPU metrics.
 *
 * HOW IT WORKS:
 *   1. Creates a thread pool with size based on intensity level
 *   2. Each thread runs a tight loop performing PBKDF2 key derivation
 *   3. PBKDF2 is computationally expensive (iterative hashing)
 *   4. Threads run until duration expires or simulation is stopped
 *
 * INTENSITY LEVELS:
 *   - MODERATE: Uses ~65% of available CPU cores
 *   - HIGH: Uses ~100% of available CPU cores
 *
 * PORTING NOTES:
 *   - Node.js: Uses child_process.fork() for separate processes
 *   - Python: Uses multiprocessing.Process (GIL prevents threading)
 *   - C#: Uses Task.Run with parallel loops
 *   - PHP: Uses pcntl_fork() for child processes
 *
 *   The key is to use OS-level parallelism (threads or processes) so the
 *   CPU load is visible in system metrics, not just within the runtime.
 */
@Service
public class CpuStressService {

    private static final Logger logger = LoggerFactory.getLogger(CpuStressService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    private final ScheduledExecutorService scheduler;
    private final Map<String, SimulationContext> activeSimulations;

    private final int availableProcessors;

    public CpuStressService(SimulationTrackerService simulationTracker, EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.activeSimulations = new ConcurrentHashMap<>();
        this.availableProcessors = Runtime.getRuntime().availableProcessors();

        logger.info("[CpuStressService] Initialized with {} available processors", availableProcessors);
    }

    /**
     * Starts a new CPU stress simulation.
     *
     * @param request The CPU stress parameters
     * @return The created simulation
     */
    public Simulation start(CpuStressRequest request) {
        // Calculate thread count based on intensity
        int threadCount = calculateThreadCount(request.getIntensity());

        // Create simulation record
        Map<String, Object> params = Map.of(
                "type", SimulationType.CPU_STRESS,
                "intensity", request.getIntensity().name(),
                "durationSeconds", request.getDurationSeconds(),
                "threadCount", threadCount
        );
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.CPU_STRESS,
                params,
                request.getDurationSeconds()
        );

        // Log the start
        eventLogService.info(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("CPU stress started (%s, %d threads) for %ds",
                        request.getIntensity(), threadCount, request.getDurationSeconds()),
                simulation.getId(),
                SimulationType.CPU_STRESS,
                params
        );

        // Create worker threads
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean running = new AtomicBoolean(true);

        // Submit CPU-intensive tasks
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> runCpuIntensiveWork(running));
        }

        // Store simulation context
        SimulationContext context = new SimulationContext(executor, running);
        activeSimulations.put(simulation.getId(), context);

        // Schedule auto-stop after duration
        scheduler.schedule(
                () -> stopInternal(simulation.getId(), true),
                request.getDurationSeconds(),
                TimeUnit.SECONDS
        );

        return simulation;
    }

    /**
     * Stops a running CPU stress simulation.
     *
     * @param simulationId The simulation ID to stop
     * @return The stopped simulation, or null if not found
     */
    public Simulation stop(String simulationId) {
        return stopInternal(simulationId, false);
    }

    /**
     * Gets all active CPU stress simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.CPU_STRESS);
    }

    /**
     * Stops all active CPU stress simulations.
     */
    public void stopAll() {
        List<Simulation> active = getActiveSimulations();
        for (Simulation sim : active) {
            stop(sim.getId());
        }
    }

    private Simulation stopInternal(String simulationId, boolean completed) {
        SimulationContext context = activeSimulations.remove(simulationId);
        if (context == null) {
            return null;
        }

        // Signal threads to stop
        context.running.set(false);

        // Shutdown executor
        context.executor.shutdownNow();
        try {
            context.executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Update simulation status
        Simulation simulation;
        if (completed) {
            simulation = simulationTracker.completeSimulation(simulationId);
            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_COMPLETED,
                    "CPU stress completed",
                    simulationId,
                    SimulationType.CPU_STRESS,
                    null
            );
        } else {
            simulation = simulationTracker.stopSimulation(simulationId);
            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_STOPPED,
                    "CPU stress stopped by user",
                    simulationId,
                    SimulationType.CPU_STRESS,
                    null
            );
        }

        return simulation;
    }

    /**
     * Calculates the number of threads based on intensity.
     */
    private int calculateThreadCount(CpuStressRequest.Intensity intensity) {
        return switch (intensity) {
            case MODERATE -> Math.max(1, (int) (availableProcessors * 0.65));
            case HIGH -> availableProcessors;
        };
    }

    /**
     * Runs CPU-intensive work until stopped.
     * Uses PBKDF2 key derivation which is computationally expensive.
     */
    private void runCpuIntensiveWork(AtomicBoolean running) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                // PBKDF2 with high iteration count - CPU intensive
                PBEKeySpec spec = new PBEKeySpec(
                        "password".toCharArray(),
                        "salt".getBytes(),
                        10000,  // iterations
                        512     // key length
                );
                factory.generateSecret(spec);
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("CPU stress work failed: {}", e.getMessage());
        }
    }

    /**
     * Context for an active simulation.
     */
    private record SimulationContext(ExecutorService executor, AtomicBoolean running) {}
}
