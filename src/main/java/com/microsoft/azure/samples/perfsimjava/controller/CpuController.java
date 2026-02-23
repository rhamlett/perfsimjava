package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.CpuStressRequest;
import com.microsoft.azure.samples.perfsimjava.service.CpuStressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * CPU CONTROLLER — CPU Stress Simulation REST API
 * =============================================================================
 *
 * PURPOSE:
 *   REST endpoints for starting, stopping, and listing CPU stress simulations.
 *   Delegates all business logic to CpuStressService.
 *
 * ENDPOINTS:
 *   POST   /api/simulations/cpu     → Start CPU stress simulation
 *   DELETE /api/simulations/cpu/:id → Stop a running simulation
 *   DELETE /api/simulations/cpu     → Stop all CPU simulations
 *   GET    /api/simulations/cpu     → List active CPU simulations
 */
@RestController
@RequestMapping("/api/simulations/cpu")
public class CpuController {

    private final CpuStressService cpuStressService;

    public CpuController(CpuStressService cpuStressService) {
        this.cpuStressService = cpuStressService;
    }

    /**
     * Starts a new CPU stress simulation.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@Valid @RequestBody CpuStressRequest request) {
        Simulation simulation = cpuStressService.start(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "message", String.format("CPU stress simulation started (%s) for %ds",
                        request.getIntensity(), request.getDurationSeconds()),
                "parameters", simulation.getParameters(),
                "scheduledEndAt", simulation.getScheduledEndAt().toString()
        ));
    }

    /**
     * Stops a running CPU stress simulation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String id) {
        Simulation simulation = cpuStressService.stop(id);

        if (simulation == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "message", "CPU stress simulation stopped",
                "status", simulation.getStatus(),
                "stoppedAt", simulation.getStoppedAt().toString()
        ));
    }

    /**
     * Stops all running CPU stress simulations.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        List<Simulation> active = cpuStressService.getActiveSimulations();
        int count = active.size();
        cpuStressService.stopAll();

        return ResponseEntity.ok(Map.of(
                "message", String.format("Stopped %d CPU stress simulation(s)", count),
                "count", count
        ));
    }

    /**
     * Lists active CPU stress simulations.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Simulation> simulations = cpuStressService.getActiveSimulations();

        return ResponseEntity.ok(Map.of(
                "simulations", simulations.stream().map(sim -> Map.of(
                        "id", sim.getId(),
                        "type", sim.getType(),
                        "status", sim.getStatus(),
                        "parameters", sim.getParameters(),
                        "startedAt", sim.getStartedAt().toString(),
                        "scheduledEndAt", sim.getScheduledEndAt().toString()
                )).toList(),
                "count", simulations.size()
        ));
    }
}
