package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SystemMetrics;
import com.microsoft.azure.samples.perfsimjava.service.EventLogService;
import com.microsoft.azure.samples.perfsimjava.service.MetricsService;
import com.microsoft.azure.samples.perfsimjava.service.SimulationTrackerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * ADMIN CONTROLLER — Administrative Endpoints
 * =============================================================================
 *
 * ENDPOINTS:
 *   GET /api/simulations    → List all active simulations
 *   GET /api/admin/status   → Admin status overview
 *   GET /api/admin/events   → Event log entries
 */
@RestController
@RequestMapping("/api")
public class AdminController {

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    private final MetricsService metricsService;

    public AdminController(SimulationTrackerService simulationTracker,
                          EventLogService eventLogService,
                          MetricsService metricsService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
        this.metricsService = metricsService;
    }

    /**
     * Lists all active simulations.
     */
    @GetMapping("/simulations")
    public ResponseEntity<Map<String, Object>> listSimulations() {
        List<Simulation> simulations = simulationTracker.getActiveSimulations();

        return ResponseEntity.ok(Map.of(
                "simulations", simulations.stream().map(sim -> Map.of(
                        "id", sim.getId(),
                        "type", sim.getType(),
                        "status", sim.getStatus(),
                        "parameters", sim.getParameters(),
                        "startedAt", sim.getStartedAt().toString(),
                        "scheduledEndAt", sim.getScheduledEndAt() != null 
                                ? sim.getScheduledEndAt().toString() : ""
                )).toList(),
                "count", simulations.size()
        ));
    }

    /**
     * Gets admin status overview.
     */
    @GetMapping("/admin/status")
    public ResponseEntity<Map<String, Object>> status() {
        SystemMetrics metrics = metricsService.collectMetrics();
        List<Simulation> active = simulationTracker.getActiveSimulations();

        return ResponseEntity.ok(Map.of(
                "application", "perfsimjava",
                "version", "1.0.0",
                "status", "running",
                "activeSimulations", active.size(),
                "metrics", Map.of(
                        "cpu", metrics.getCpu().getUsagePercent(),
                        "memory", Map.of(
                                "heapUsedMb", metrics.getMemory().getHeapUsedMb(),
                                "heapMaxMb", metrics.getMemory().getHeapMaxMb()
                        ),
                        "threads", metrics.getThread().getActiveCount()
                ),
                "process", Map.of(
                        "pid", metrics.getProcess().getPid(),
                        "uptimeSeconds", metrics.getProcess().getUptimeSeconds()
                )
        ));
    }

    /**
     * Gets event log entries.
     */
    @GetMapping("/admin/events")
    public ResponseEntity<Map<String, Object>> events(
            @RequestParam(defaultValue = "50") int limit) {
        List<EventLogEntry> entries = eventLogService.getRecentEntries(limit);

        return ResponseEntity.ok(Map.of(
                "events", entries,
                "count", entries.size()
        ));
    }

    /**
     * Gets build/version info for the dashboard.
     */
    @GetMapping("/admin/build")
    public ResponseEntity<Map<String, Object>> build() {
        return ResponseEntity.ok(Map.of(
                "application", "perfsimjava",
                "version", "1.0.0",
                "runtime", "Java " + System.getProperty("java.version"),
                "framework", "Spring Boot 3.3",
                "buildTime", "2024"
        ));
    }

    /**
     * Gets SKU information (useful when deployed to Azure).
     */
    @GetMapping("/admin/sku")
    public ResponseEntity<Map<String, Object>> sku() {
        String websiteSku = System.getenv("WEBSITE_SKU");
        String websiteHostname = System.getenv("WEBSITE_HOSTNAME");

        return ResponseEntity.ok(Map.of(
                "sku", websiteSku != null ? websiteSku : "Local",
                "hostname", websiteHostname != null ? websiteHostname : "localhost",
                "isAzure", websiteHostname != null
        ));
    }
}
