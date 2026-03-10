package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.FailedRequestsRequest;
import com.microsoft.azure.samples.perfsimjava.service.FailedRequestsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * FAILED REQUESTS CONTROLLER — HTTP 5xx Error Simulation REST API
 * =============================================================================
 *
 * PURPOSE:
 *   REST endpoints for generating HTTP 5xx errors. Triggers load test requests
 *   with 100% error probability to produce visible failures in AppLens and
 *   Application Insights.
 *
 * ENDPOINTS:
 *   POST   /api/simulations/failed-requests       → Trigger failed requests simulation
 *   GET    /api/simulations/failed-requests       → List active simulations
 *   GET    /api/simulations/failed-requests/stats → Get current statistics
 *   DELETE /api/simulations/failed-requests       → Stop all active simulations
 *
 * DIAGNOSTIC VALUE:
 *   - Generates visible HTTP 500 errors in AppLens error analysis
 *   - Creates failed request entries in Application Insights
 *   - Produces visible latency spikes in request latency monitoring
 *   - Simulates application errors for diagnostic tool testing
 */
@RestController
@RequestMapping("/api/simulations/failed-requests")
public class FailedRequestsController {

    private final FailedRequestsService failedRequestsService;

    public FailedRequestsController(FailedRequestsService failedRequestsService) {
        this.failedRequestsService = failedRequestsService;
    }

    /**
     * Triggers a failed requests simulation.
     *
     * POST /api/simulations/failed-requests
     * Body: { numberOfRequests: 10 }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> trigger(@Valid @RequestBody FailedRequestsRequest request) {
        Simulation simulation = failedRequestsService.trigger(request);
        return ResponseEntity.ok(Map.of(
                "message", "Failed requests simulation started",
                "simulation", simulation,
                "numberOfRequests", request.getNumberOfRequests()
        ));
    }

    /**
     * Gets current simulation statistics.
     *
     * GET /api/simulations/failed-requests/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(failedRequestsService.getStats());
    }

    /**
     * Lists active failed requests simulations.
     *
     * GET /api/simulations/failed-requests
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Simulation> simulations = failedRequestsService.getActiveSimulations();
        return ResponseEntity.ok(Map.of(
                "count", simulations.size(),
                "simulations", simulations
        ));
    }

    /**
     * Stops all active failed requests simulations.
     *
     * DELETE /api/simulations/failed-requests
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        failedRequestsService.stopAll();
        return ResponseEntity.ok(Map.of(
                "message", "Failed requests simulation stopped"
        ));
    }
}
