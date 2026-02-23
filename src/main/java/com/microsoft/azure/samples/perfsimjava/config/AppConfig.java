package com.microsoft.azure.samples.perfsimjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * =============================================================================
 * APPLICATION CONFIGURATION
 * =============================================================================
 *
 * PURPOSE:
 *   Centralizes all configurable values in one place. Every tunable parameter
 *   (intervals, limits) is defined here with sensible defaults that can be
 *   overridden via application.properties or environment variables.
 *
 * CONFIGURATION BINDING:
 *   Spring Boot automatically binds properties prefixed with "perfsim" to
 *   this class. For example:
 *   - perfsim.metrics-interval-ms=250 → metricsIntervalMs field
 *   - PERFSIM_METRICS_INTERVAL_MS=250 → same (env var format)
 *
 * PORTING NOTES:
 *   - Node.js: process.env with parseInt fallback
 *   - Python Django: settings.py with os.environ.get()
 *   - PHP Laravel: .env file with config() helper
 *   - C# ASP.NET: appsettings.json with IConfiguration
 */
@Configuration
@ConfigurationProperties(prefix = "perfsim")
public class AppConfig {

    /**
     * Metrics broadcast interval in milliseconds.
     * WebSocket clients receive metrics updates at this frequency.
     */
    private int metricsIntervalMs = 250;

    /**
     * Probe interval in milliseconds for the latency monitor.
     * Lower values provide more granular latency data.
     */
    private int probeIntervalMs = 100;

    /**
     * Maximum allowed simulation duration in seconds.
     * Prevents runaway simulations from consuming resources indefinitely.
     */
    private int maxSimulationDurationSeconds = 86400;

    /**
     * Maximum single memory allocation in megabytes.
     * Protects against accidentally triggering OOM.
     */
    private int maxMemoryAllocationMb = 65536;

    /**
     * Maximum number of event log entries to retain (ring buffer).
     */
    private int eventLogMaxEntries = 100;

    /**
     * Thread pool size for CPU stress simulations.
     * Defaults to available processors.
     */
    private int cpuStressThreadPoolSize = Runtime.getRuntime().availableProcessors();

    // Getters and Setters

    public int getMetricsIntervalMs() {
        return metricsIntervalMs;
    }

    public void setMetricsIntervalMs(int metricsIntervalMs) {
        this.metricsIntervalMs = metricsIntervalMs;
    }

    public int getProbeIntervalMs() {
        return probeIntervalMs;
    }

    public void setProbeIntervalMs(int probeIntervalMs) {
        this.probeIntervalMs = probeIntervalMs;
    }

    public int getMaxSimulationDurationSeconds() {
        return maxSimulationDurationSeconds;
    }

    public void setMaxSimulationDurationSeconds(int maxSimulationDurationSeconds) {
        this.maxSimulationDurationSeconds = maxSimulationDurationSeconds;
    }

    public int getMaxMemoryAllocationMb() {
        return maxMemoryAllocationMb;
    }

    public void setMaxMemoryAllocationMb(int maxMemoryAllocationMb) {
        this.maxMemoryAllocationMb = maxMemoryAllocationMb;
    }

    public int getEventLogMaxEntries() {
        return eventLogMaxEntries;
    }

    public void setEventLogMaxEntries(int eventLogMaxEntries) {
        this.eventLogMaxEntries = eventLogMaxEntries;
    }

    public int getCpuStressThreadPoolSize() {
        return cpuStressThreadPoolSize;
    }

    public void setCpuStressThreadPoolSize(int cpuStressThreadPoolSize) {
        this.cpuStressThreadPoolSize = cpuStressThreadPoolSize;
    }
}
