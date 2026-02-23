package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * =============================================================================
 * HEALTH CONTROLLER — Health Check Endpoints
 * =============================================================================
 *
 * PURPOSE:
 *   Provides health check endpoints for Azure App Service health probes
 *   and Kubernetes liveness/readiness probes.
 *
 * ENDPOINTS:
 *   GET /api/health          → Basic health check
 *   GET /api/health/live     → Liveness probe (is the app running?)
 *   GET /api/health/ready    → Readiness probe (can the app handle traffic?)
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final MetricsService metricsService;

    public HealthController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Basic health check endpoint.
     * Returns 200 if the application is running.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis(),
                "application", "perfsimjava",
                "version", "1.0.0"
        ));
    }

    /**
     * Liveness probe - is the application alive?
     * Used by Kubernetes/Azure to determine if the container should be restarted.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "alive"));
    }

    /**
     * Readiness probe - can the application handle traffic?
     * Used by Kubernetes/Azure to determine if traffic should be routed here.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        // Could add checks for database, external services, etc.
        return ResponseEntity.ok(Map.of("status", "ready"));
    }
}
