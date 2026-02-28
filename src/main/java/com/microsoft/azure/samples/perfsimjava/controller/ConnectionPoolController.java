package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.ConnectionPoolRequest;
import com.microsoft.azure.samples.perfsimjava.service.ConnectionPoolService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for Connection Pool Exhaustion simulation.
 *
 * Simulates database connection pool exhaustion - a common Java performance issue.
 */
@RestController
@RequestMapping("/api/simulations/connection-pool")
public class ConnectionPoolController {

    private final ConnectionPoolService connectionPoolService;

    public ConnectionPoolController(ConnectionPoolService connectionPoolService) {
        this.connectionPoolService = connectionPoolService;
    }

    /**
     * Triggers a connection pool exhaustion simulation.
     *
     * POST /api/simulations/connection-pool
     * Body: { poolSize, queryDurationSeconds, concurrentQueries, connectionTimeoutSeconds }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> trigger(@Valid @RequestBody ConnectionPoolRequest request) {
        Simulation simulation = connectionPoolService.trigger(request);
        return ResponseEntity.ok(Map.of(
                "message", "Connection pool exhaustion simulation started",
                "simulation", simulation
        ));
    }

    /**
     * Gets current pool statistics.
     *
     * GET /api/simulations/connection-pool/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(connectionPoolService.getPoolStats());
    }

    /**
     * Lists active connection pool simulations.
     *
     * GET /api/simulations/connection-pool
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Simulation> simulations = connectionPoolService.getActiveSimulations();
        return ResponseEntity.ok(Map.of(
                "count", simulations.size(),
                "simulations", simulations
        ));
    }

    /**
     * Stops all active connection pool simulations.
     *
     * DELETE /api/simulations/connection-pool
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        connectionPoolService.stopAll();
        return ResponseEntity.ok(Map.of(
                "message", "Connection pool simulation stopped"
        ));
    }
}
