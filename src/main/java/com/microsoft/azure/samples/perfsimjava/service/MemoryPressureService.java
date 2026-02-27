package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.MemoryPressureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * =============================================================================
 * MEMORY PRESSURE SERVICE — Heap Memory Allocation Simulation
 * =============================================================================
 *
 * PURPOSE:
 *   Simulates memory pressure by allocating and retaining objects on the
 *   managed heap. Memory is held until explicitly released via DELETE endpoint.
 *   This makes memory usage visible in JVM metrics and Azure monitoring.
 *
 * HOW IT WORKS:
 *   1. Allocate byte arrays in chunks (1MB per array)
 *   2. Store arrays in a List to prevent garbage collection
 *   3. Memory shows up in JVM heap metrics (heapUsed)
 *   4. On release: clear list, arrays become eligible for GC
 *   5. Optionally trigger System.gc() to encourage immediate reclamation
 *
 * WHY BYTE ARRAYS:
 *   byte[] arrays are allocated directly on the Java heap and are visible
 *   in memory metrics. They're also efficient to allocate in bulk.
 *
 * PORTING NOTES:
 *   - Node.js: Allocate objects (not Buffers) to show in V8 heap
 *   - Python: List of dict objects shows in memory_get_usage()
 *   - C#: List<byte[]> shows in GC.GetTotalMemory()
 *   - PHP: Array of stdClass objects shows in memory_get_usage()
 *
 *   KEY BEHAVIORS:
 *   - Memory does NOT auto-expire - must be explicitly released
 *   - Allocation is done incrementally to avoid blocking
 *   - Multiple independent allocations can coexist (tracked by ID)
 */
@Service
public class MemoryPressureService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryPressureService.class);
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    private final Map<String, MemoryAllocation> allocations;
    private final ExecutorService allocator;

    public MemoryPressureService(SimulationTrackerService simulationTracker, EventLogService eventLogService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
        this.allocations = new ConcurrentHashMap<>();
        this.allocator = Executors.newSingleThreadExecutor();
    }

    /**
     * Allocates memory and starts a memory pressure simulation.
     *
     * @param request The memory allocation parameters
     * @return The created simulation
     */
    public Simulation allocate(MemoryPressureRequest request) {
        int sizeMb = request.getSizeMb();
        logger.info("=== ALLOCATE CALLED: sizeMb={} ===", sizeMb);
        logger.info("=== EXISTING ALLOCATIONS BEFORE THIS REQUEST: count={}, totalMB={} ===", 
                    allocations.size(), getTotalAllocatedMb());

        // Create simulation record (no auto-expiry for memory allocations)
        Map<String, Object> params = Map.of(
                "type", SimulationType.MEMORY_PRESSURE,
                "sizeMb", sizeMb
        );
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.MEMORY_PRESSURE,
                params,
                Integer.MAX_VALUE / 1000  // Effectively infinite duration
        );

        // Log allocation start
        eventLogService.info(
                EventLogEntry.EventType.MEMORY_ALLOCATING,
                String.format("Starting allocation of %dMB...", sizeMb),
                simulation.getId(),
                SimulationType.MEMORY_PRESSURE,
                params
        );

        // Initialize allocation tracking
        MemoryAllocation allocation = new MemoryAllocation(sizeMb);
        allocations.put(simulation.getId(), allocation);
        
        logger.info("=== ALLOCATIONS AFTER ADDING NEW: count={} ===", allocations.size());

        // Perform allocation asynchronously in chunks
        allocator.submit(() -> allocateMemoryAsync(simulation.getId(), sizeMb));

        return simulation;
    }

    /**
     * Releases a memory allocation.
     *
     * @param simulationId The simulation ID to release
     * @return The released simulation, or null if not found
     */
    public Simulation release(String simulationId) {
        MemoryAllocation allocation = allocations.remove(simulationId);
        if (allocation == null) {
            return null;
        }

        // Clear the memory
        int releasedMb = allocation.sizeMb;
        allocation.data.clear();

        // Update simulation status
        Simulation simulation = simulationTracker.stopSimulation(simulationId);

        // Log the release
        eventLogService.info(
                EventLogEntry.EventType.MEMORY_RELEASED,
                String.format("Released %dMB of heap memory", releasedMb),
                simulationId,
                SimulationType.MEMORY_PRESSURE,
                Map.of("sizeMb", releasedMb)
        );

        // Suggest garbage collection
        System.gc();

        return simulation;
    }

    /**
     * Gets all active memory pressure simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.MEMORY_PRESSURE);
    }

    /**
     * Releases all active memory allocations.
     */
    public void releaseAll() {
        List<Simulation> active = getActiveSimulations();
        for (Simulation sim : active) {
            release(sim.getId());
        }
    }

    /**
     * Gets total memory currently allocated (based on requested size).
     */
    public int getTotalAllocatedMb() {
        return allocations.values().stream()
                .mapToInt(a -> a.sizeMb)
                .sum();
    }

    /**
     * Gets total ACTUAL bytes allocated in data lists.
     */
    public long getActualAllocatedBytes() {
        return allocations.values().stream()
                .mapToLong(a -> a.data.stream().mapToLong(chunk -> chunk.length).sum())
                .sum();
    }

    /**
     * Gets diagnostic info about all allocations.
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diag = new java.util.LinkedHashMap<>();
        
        // JVM memory info (Runtime method)
        Runtime rt = Runtime.getRuntime();
        diag.put("runtime_totalMemoryMb", rt.totalMemory() / (1024 * 1024));
        diag.put("runtime_freeMemoryMb", rt.freeMemory() / (1024 * 1024));
        diag.put("runtime_usedMemoryMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        diag.put("runtime_maxMemoryMb", rt.maxMemory() / (1024 * 1024));
        
        // JVM memory info (MemoryMXBean - same as dashboard)
        java.lang.management.MemoryMXBean memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
        java.lang.management.MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        diag.put("mxbean_heapUsedMb", heapUsage.getUsed() / (1024 * 1024));
        diag.put("mxbean_heapCommittedMb", heapUsage.getCommitted() / (1024 * 1024));
        diag.put("mxbean_heapMaxMb", heapUsage.getMax() / (1024 * 1024));
        
        // Allocation tracking info
        diag.put("allocationsCount", allocations.size());
        diag.put("requestedTotalMb", getTotalAllocatedMb());
        diag.put("actualTotalBytes", getActualAllocatedBytes());
        diag.put("actualTotalMb", getActualAllocatedBytes() / (1024 * 1024));
        diag.put("chunkSizeBytes", CHUNK_SIZE);
        diag.put("chunkSizeMb", CHUNK_SIZE / (1024 * 1024));
        
        // Calculated expected vs actual
        long baseline = 30; // Approximate baseline MB
        long expectedUsedMb = baseline + (getActualAllocatedBytes() / (1024 * 1024));
        long actualUsedMb = heapUsage.getUsed() / (1024 * 1024);
        diag.put("expectedUsedMb", expectedUsedMb);
        diag.put("actualUsedMb", actualUsedMb);
        diag.put("unexplainedMb", actualUsedMb - expectedUsedMb);
        
        // Per-allocation details
        allocations.forEach((id, alloc) -> {
            String prefix = "alloc_" + id.substring(0, 8);
            diag.put(prefix + "_requestedMb", alloc.sizeMb);
            diag.put(prefix + "_chunksCount", alloc.data.size());
            long actualBytes = alloc.data.stream().mapToLong(c -> c.length).sum();
            diag.put(prefix + "_actualBytes", actualBytes);
            diag.put(prefix + "_actualMb", actualBytes / (1024 * 1024));
            
            // Check if all chunks are the expected size
            long unexpectedSizeChunks = alloc.data.stream()
                    .filter(c -> c.length != CHUNK_SIZE)
                    .count();
            diag.put(prefix + "_unexpectedSizeChunks", unexpectedSizeChunks);
        });
        
        return diag;
    }

    /**
     * Allocates memory asynchronously in chunks.
     */
    private void allocateMemoryAsync(String simulationId, int sizeMb) {
        java.lang.management.MemoryMXBean memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
        long heapBefore = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        
        logger.info("=== ALLOCATION START ===");
        logger.info("=== Requested: {} MB, ChunkSize: {} bytes ===", sizeMb, CHUNK_SIZE);
        logger.info("=== Heap BEFORE allocation: {} MB ===", heapBefore);
        
        MemoryAllocation allocation = allocations.get(simulationId);
        if (allocation == null) {
            return;
        }

        long totalBytesAllocated = 0;
        try {
            for (int i = 0; i < sizeMb; i++) {
                // Check if still active
                if (!allocations.containsKey(simulationId)) {
                    logger.info("Allocation {} cancelled", simulationId);
                    return;
                }

                // Allocate 1MB chunk
                byte[] chunk = new byte[CHUNK_SIZE];
                totalBytesAllocated += chunk.length;
                
                // Fill with data to ensure it's actually allocated
                for (int j = 0; j < chunk.length; j += 4096) {
                    chunk[j] = (byte) (i % 256);
                }
                allocation.data.add(chunk);

                // Log progress every 100 chunks with heap status
                if (i % 100 == 0) {
                    long heapNow = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
                    logger.info("Progress: {}/{} chunks, allocated: {} MB, heap now: {} MB, heap delta: {} MB", 
                                i, sizeMb, totalBytesAllocated / (1024 * 1024), heapNow, heapNow - heapBefore);
                    Thread.sleep(1);
                }
            }

            long heapAfter = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            logger.info("=== ALLOCATION COMPLETE ===");
            logger.info("=== Requested: {} MB ===", sizeMb);
            logger.info("=== Chunks created: {} ===", allocation.data.size());
            logger.info("=== Total bytes allocated: {} ({} MB) ===", totalBytesAllocated, totalBytesAllocated / (1024*1024));
            logger.info("=== Heap BEFORE: {} MB ===", heapBefore);
            logger.info("=== Heap AFTER: {} MB ===", heapAfter);
            logger.info("=== Heap DELTA: {} MB (expected: {} MB) ===", heapAfter - heapBefore, sizeMb);
            
            // Log completion
            eventLogService.info(
                    EventLogEntry.EventType.MEMORY_ALLOCATED,
                    String.format("Allocated %dMB of heap memory (%d chunks)",
                            sizeMb, allocation.data.size()),
                    simulationId,
                    SimulationType.MEMORY_PRESSURE,
                    Map.of("sizeMb", sizeMb, "chunks", allocation.data.size())
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Memory allocation interrupted for {}", simulationId);
        } catch (OutOfMemoryError e) {
            logger.error("OutOfMemoryError during allocation: {}", e.getMessage());
            eventLogService.error(
                    EventLogEntry.EventType.SIMULATION_FAILED,
                    "Memory allocation failed: OutOfMemoryError",
                    simulationId,
                    SimulationType.MEMORY_PRESSURE,
                    null
            );
            allocations.remove(simulationId);
            simulationTracker.failSimulation(simulationId);
        }
    }

    /**
     * Memory allocation tracking.
     */
    private static class MemoryAllocation {
        final int sizeMb;
        final List<byte[]> data;

        MemoryAllocation(int sizeMb) {
            this.sizeMb = sizeMb;
            this.data = new ArrayList<>(sizeMb);
        }
    }
}
