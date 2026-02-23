package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.SystemMetrics;
import com.microsoft.azure.samples.perfsimjava.service.MetricsService;
import com.microsoft.azure.samples.perfsimjava.service.ProbeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    private final ProbeService probeService;

    public MetricsController(MetricsService metricsService, ProbeService probeService) {
        this.metricsService = metricsService;
        this.probeService = probeService;
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
     */
    @GetMapping("/probe")
    public ResponseEntity<MetricsService.ProbeResponse> probe() {
        return ResponseEntity.ok(metricsService.getProbeResponse());
    }

    /**
     * Gets probe service statistics.
     */
    @GetMapping("/probe/stats")
    public ResponseEntity<Map<String, Object>> probeStats() {
        return ResponseEntity.ok(probeService.getStats());
    }
}
