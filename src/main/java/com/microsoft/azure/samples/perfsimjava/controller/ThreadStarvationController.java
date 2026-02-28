package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.ThreadStarvationRequest;
import com.microsoft.azure.samples.perfsimjava.service.ThreadStarvationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * THREAD STARVATION CONTROLLER — Thread Pool Exhaustion Simulation REST API
 * =============================================================================
 *
 * This is the Java equivalent of "Event Loop Blocking" in Node.js.
 * The dashboard spawns N concurrent requests to /block, each tying up
 * one Tomcat servlet thread until the server's thread pool is exhausted.
 *
 * ENDPOINTS:
 *   POST   /api/simulations/thread/starvation       → Create simulation record
 *   POST   /api/simulations/thread/starvation/block → Block calling servlet thread
 *   GET    /api/simulations/thread/starvation       → List active simulations
 *   DELETE /api/simulations/thread/starvation       → Stop all simulations
 */
@RestController
@RequestMapping("/api/simulations/thread/starvation")
public class ThreadStarvationController {

    private final ThreadStarvationService threadStarvationService;

    public ThreadStarvationController(ThreadStarvationService threadStarvationService) {
        this.threadStarvationService = threadStarvationService;
    }

    /**
     * Creates a thread starvation simulation record.
     * Returns the simulation ID - client then spawns N requests to /block.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> trigger(@Valid @RequestBody ThreadStarvationRequest request) {
        Simulation simulation = threadStarvationService.trigger(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "durationSeconds", request.getDurationSeconds(),
                "threadCount", request.getThreadCount(),
                "message", String.format("Simulation created - spawn %d requests to /block endpoint",
                        request.getThreadCount()),
                "parameters", simulation.getParameters()
        ));
    }

    /**
     * Blocks the calling servlet thread.
     * Each request to this endpoint ties up one Tomcat thread.
     * The dashboard spawns N concurrent requests to exhaust the thread pool.
     */
    @PostMapping("/block")
    public ResponseEntity<Map<String, Object>> block(
            @RequestParam String simulationId,
            @RequestParam int durationSeconds) {
        
        boolean completed = threadStarvationService.blockServletThread(simulationId, durationSeconds);
        
        return ResponseEntity.ok(Map.of(
                "simulationId", simulationId,
                "completed", completed,
                "message", completed ? "Block completed" : "Block interrupted or simulation stopped"
        ));
    }

    /**
     * Lists active thread starvation simulations.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Simulation> simulations = threadStarvationService.getActiveSimulations();

        return ResponseEntity.ok(Map.of(
                "simulations", simulations.stream().map(sim -> Map.of(
                        "id", sim.getId(),
                        "type", sim.getType(),
                        "status", sim.getStatus(),
                        "parameters", sim.getParameters(),
                        "startedAt", sim.getStartedAt().toString(),
                        "activeBlockers", threadStarvationService.getActiveBlockerCount(sim.getId())
                )).toList(),
                "count", simulations.size()
        ));
    }
    
    /**
     * Stops all active thread starvation simulations.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        threadStarvationService.stopAll();
        return ResponseEntity.ok(Map.of(
                "message", "All thread starvation simulations stopped"
        ));
    }
}
