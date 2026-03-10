package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
 *   GET /api/health             → Basic health check
 *   GET /api/health/live        → Liveness probe (is the app running?)
 *   GET /api/health/ready       → Readiness probe (can the app handle traffic?)
 *   GET /api/health/config      → Frontend configuration (probe intervals, etc.)
 *   GET /api/health/footer      → Footer info (PAGE_FOOTER env var and build time)
 *   GET /api/health/environment → Environment info (SKU, Azure detection)
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String buildTime;
    private final AppConfig appConfig;

    public HealthController(AppConfig appConfig) {
        this.appConfig = appConfig;
        // Capture build time at startup
        this.buildTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()) + " UTC";
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

    /**
     * Frontend configuration endpoint - returns configurable values for the dashboard.
     * Used by the frontend to adapt to server-side configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of(
                "latencyProbeIntervalMs", appConfig.getProbeIntervalMs(),
                "metricsIntervalMs", appConfig.getMetricsIntervalMs()
        ));
    }

    /**
     * Footer info endpoint - returns PAGE_FOOTER env var and build time.
     * Used by the dashboard to display footer credits and build info.
     */
    @GetMapping("/footer")
    public ResponseEntity<Map<String, Object>> footer() {
        String pageFooter = System.getenv("PAGE_FOOTER");
        
        Map<String, Object> response = new HashMap<>();
        response.put("buildTime", buildTime);
        response.put("footer", pageFooter); // Will be null if not set
        
        return ResponseEntity.ok(response);
    }

    /**
     * Environment info endpoint - returns SKU, Azure detection, and worker instance details.
     */
    @GetMapping("/environment")
    public ResponseEntity<Map<String, Object>> environment() {
        String websiteSku = System.getenv("WEBSITE_SKU");
        String websiteHostname = System.getenv("WEBSITE_HOSTNAME");
        String computerName = System.getenv("COMPUTERNAME");
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("sku", websiteSku != null ? websiteSku : "Local");
        response.put("hostname", websiteHostname != null ? websiteHostname : "localhost");
        response.put("isAzure", websiteHostname != null);
        response.put("computerName", computerName != null ? computerName : "local-worker");
        
        return ResponseEntity.ok(response);
    }
}
