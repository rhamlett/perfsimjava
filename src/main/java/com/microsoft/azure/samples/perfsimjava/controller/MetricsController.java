package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.SystemMetrics;
import com.microsoft.azure.samples.perfsimjava.service.ConnectionPoolService;
import com.microsoft.azure.samples.perfsimjava.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * =============================================================================
 * METRICS CONTROLLER — System Metrics REST API
 * =============================================================================
 *
 * PURPOSE:
 *   REST endpoints for retrieving system metrics. The main metrics are
 *   pushed via WebSocket, but REST endpoints are available for polling
 *   and integration with external monitoring systems.
 *
 * ENDPOINTS:
 *   GET /api/metrics       → Full metrics snapshot
 *   GET /api/metrics/probe → Lightweight probe endpoint for latency monitoring
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final ConnectionPoolService connectionPoolService;

    public MetricsController(MetricsService metricsService,
                             ConnectionPoolService connectionPoolService) {
        this.metricsService = metricsService;
        this.connectionPoolService = connectionPoolService;
    }

    /**
     * Gets current system metrics snapshot.
     */
    @GetMapping
    public ResponseEntity<SystemMetrics> getMetrics() {
        return ResponseEntity.ok(metricsService.collectMetrics());
    }

    /**
     * Lightweight probe endpoint for latency monitoring.
     * This is what the probe service hits to measure response time.
     * 
     * When connection pool exhaustion is active, this endpoint must acquire
     * a connection first - simulating realistic health checks that verify
     * database connectivity.
     */
    @GetMapping("/probe")
    public ResponseEntity<MetricsService.ProbeResponse> probe() {
        // When connection pool simulation is active, probe must acquire a connection
        // This makes probe latency realistic - real health checks often query the DB
        connectionPoolService.acquireProbeConnection();
        
        return ResponseEntity.ok(metricsService.getProbeResponse());
    }
}
