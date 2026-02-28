package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.SlowRequestRequest;
import com.microsoft.azure.samples.perfsimjava.service.SlowRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * SLOW REQUEST CONTROLLER — Slow Response Simulation REST API
 * =============================================================================
 *
 * ENDPOINTS:
 *   POST   /api/simulations/slow         → Start slow request simulation (intervals)
 *   POST   /api/simulations/slow/execute → Execute a single slow request (internal)
 *   GET    /api/simulations/slow         → List active simulations
 *   DELETE /api/simulations/slow         → Stop all slow request simulations
 */
@RestController
@RequestMapping("/api/simulations/slow")
public class SlowRequestController {

    private final SlowRequestService slowRequestService;

    public SlowRequestController(SlowRequestService slowRequestService) {
        this.slowRequestService = slowRequestService;
    }

    /**
     * Starts a slow request simulation with interval-based request spawning.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> trigger(@Valid @RequestBody SlowRequestRequest request) {
        Simulation simulation = slowRequestService.trigger(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "status", simulation.getStatus(),
                "message", String.format("Slow request simulation started: %d requests at %ds intervals",
                        request.getMaxRequests(), request.getIntervalSeconds()),
                "parameters", simulation.getParameters()
        ));
    }

    /**
     * Executes a single slow request (called internally by the service).
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @RequestParam(defaultValue = "10") int delaySeconds,
            @RequestParam(defaultValue = "SLEEP") SlowRequestRequest.BlockingPattern pattern) {
        
        slowRequestService.executeSingleRequest(delaySeconds, pattern);
        
        return ResponseEntity.ok(Map.of(
                "message", "Slow request completed",
                "delaySeconds", delaySeconds,
                "pattern", pattern.name()
        ));
    }

    /**
     * Lists active slow request simulations.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Simulation> simulations = slowRequestService.getActiveSimulations();

        return ResponseEntity.ok(Map.of(
                "simulations", simulations.stream().map(sim -> Map.of(
                        "id", sim.getId(),
                        "type", sim.getType(),
                        "status", sim.getStatus(),
                        "parameters", sim.getParameters(),
                        "startedAt", sim.getStartedAt().toString()
                )).toList(),
                "count", simulations.size()
        ));
    }

    /**
     * Stops all active slow request simulations.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        slowRequestService.stopAll();
        return ResponseEntity.ok(Map.of(
                "message", "Slow request simulations stopped"
        ));
    }
}
