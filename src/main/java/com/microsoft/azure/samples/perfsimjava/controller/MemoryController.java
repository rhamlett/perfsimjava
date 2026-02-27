package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.MemoryPressureRequest;
import com.microsoft.azure.samples.perfsimjava.service.MemoryPressureService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * MEMORY CONTROLLER — Memory Pressure Simulation REST API
 * =============================================================================
 *
 * ENDPOINTS:
 *   POST   /api/simulations/memory     → Allocate memory
 *   DELETE /api/simulations/memory/:id → Release specific allocation
 *   DELETE /api/simulations/memory     → Release all allocations
 *   GET    /api/simulations/memory     → List active allocations
 */
@RestController
@RequestMapping("/api/simulations/memory")
public class MemoryController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryController.class);
    private final MemoryPressureService memoryPressureService;

    public MemoryController(MemoryPressureService memoryPressureService) {
        this.memoryPressureService = memoryPressureService;
    }

    /**
     * Allocates memory.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> allocate(@Valid @RequestBody MemoryPressureRequest request) {
        logger.info("=== MEMORY CONTROLLER: POST /api/simulations/memory called, sizeMb={} ===", request.getSizeMb());
        Simulation simulation = memoryPressureService.allocate(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "message", String.format("Memory allocation started: %dMB", request.getSizeMb()),
                "parameters", simulation.getParameters()
        ));
    }

    /**
     * Releases a specific memory allocation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> release(@PathVariable String id) {
        Simulation simulation = memoryPressureService.release(id);

        if (simulation == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "message", "Memory released",
                "status", simulation.getStatus()
        ));
    }

    /**
     * Releases all memory allocations.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> releaseAll() {
        List<Simulation> active = memoryPressureService.getActiveSimulations();
        int count = active.size();
        int totalMb = memoryPressureService.getTotalAllocatedMb();
        memoryPressureService.releaseAll();

        return ResponseEntity.ok(Map.of(
                "message", String.format("Released %dMB across %d allocation(s)", totalMb, count),
                "count", count,
                "totalMb", totalMb
        ));
    }

    /**
     * Lists active memory allocations.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Simulation> simulations = memoryPressureService.getActiveSimulations();
        int totalMb = memoryPressureService.getTotalAllocatedMb();

        return ResponseEntity.ok(Map.of(
                "simulations", simulations.stream().map(sim -> Map.of(
                        "id", sim.getId(),
                        "type", sim.getType(),
                        "status", sim.getStatus(),
                        "parameters", sim.getParameters(),
                        "startedAt", sim.getStartedAt().toString()
                )).toList(),
                "count", simulations.size(),
                "totalAllocatedMb", totalMb
        ));
    }
}
