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
 * In Java's thread-per-request model, blocking multiple servlet threads
 * exhausts the thread pool, causing new requests to queue.
 *
 * ENDPOINTS:
 *   POST /api/simulations/threadstarvation → Trigger thread starvation
 *   GET  /api/simulations/threadstarvation → List active simulations
 */
@RestController
@RequestMapping("/api/simulations/threadstarvation")
public class ThreadStarvationController {

    private final ThreadStarvationService threadStarvationService;

    public ThreadStarvationController(ThreadStarvationService threadStarvationService) {
        this.threadStarvationService = threadStarvationService;
    }

    /**
     * Triggers thread starvation simulation.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> trigger(@Valid @RequestBody ThreadStarvationRequest request) {
        Simulation simulation = threadStarvationService.trigger(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "message", String.format("Thread starvation triggered for %ds with %d threads",
                        request.getDurationSeconds(), request.getThreadCount()),
                "parameters", simulation.getParameters(),
                "warning", "Server may become unresponsive during this simulation!"
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
                        "startedAt", sim.getStartedAt().toString()
                )).toList(),
                "count", simulations.size()
        ));
    }
}
