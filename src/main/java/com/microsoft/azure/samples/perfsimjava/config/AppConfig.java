package com.microsoft.azure.samples.perfsimjava.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    
    /**
     * Minimum allowed probe interval in milliseconds.
     * Safety floor to prevent excessive probing.
     */
    private static final int MIN_PROBE_INTERVAL_MS = 100;

    /**
     * Metrics broadcast interval in milliseconds.
     * WebSocket clients receive metrics updates at this frequency.
     */
    private int metricsIntervalMs = 250;

    /**
     * Probe interval in milliseconds for the latency monitor.
     * Configurable via HEALTH_PROBE_RATE environment variable.
     * Default: 200ms (5 probes/sec). Minimum: 100ms.
     */
    private int probeIntervalMs = 200;

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
     * Thread pool size for CPU stress simulations.
     * Defaults to available processors.
     */
    private int cpuStressThreadPoolSize = Runtime.getRuntime().availableProcessors();

    /**
     * UI language code (ISO 639-1). Determines the language for dashboard UI translations.
     * Can be overridden by UI_LANGUAGE environment variable.
     * Default: "en" (English, no translation needed).
     */
    private String uiLanguage = "en";

    /**
     * Azure Cognitive Services Translator API key.
     * Required for non-English UI languages.
     * Can be overridden by TRANSLATOR_API_KEY environment variable.
     */
    private String translatorApiKey = "";

    /**
     * Azure Translator API endpoint URL.
     * Can be overridden by TRANSLATOR_ENDPOINT environment variable.
     */
    private String translatorEndpoint = "https://api.cognitive.microsofttranslator.com";

    /**
     * Azure region of the Translator resource.
     * Can be overridden by TRANSLATOR_REGION environment variable.
     */
    private String translatorRegion = "eastus";

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
        // Enforce minimum probe interval
        this.probeIntervalMs = Math.max(probeIntervalMs, MIN_PROBE_INTERVAL_MS);
    }

    /**
     * Post-construction initialization.
     * Checks for HEALTH_PROBE_RATE environment variable override.
     */
    @PostConstruct
    public void init() {
        String healthProbeRate = System.getenv("HEALTH_PROBE_RATE");
        if (healthProbeRate != null && !healthProbeRate.isEmpty()) {
            try {
                int envValue = Integer.parseInt(healthProbeRate);
                int clampedValue = Math.max(envValue, MIN_PROBE_INTERVAL_MS);
                this.probeIntervalMs = clampedValue;
                if (envValue < MIN_PROBE_INTERVAL_MS) {
                    logger.warn("[AppConfig] HEALTH_PROBE_RATE={} is below minimum, clamped to {}ms", 
                            envValue, MIN_PROBE_INTERVAL_MS);
                } else {
                    logger.info("[AppConfig] HEALTH_PROBE_RATE set to {}ms from environment", clampedValue);
                }
            } catch (NumberFormatException e) {
                logger.warn("[AppConfig] Invalid HEALTH_PROBE_RATE value '{}', using default {}ms", 
                        healthProbeRate, probeIntervalMs);
            }
        }

        // Log i18n configuration (bound via Spring Boot property placeholders)
        if (uiLanguage != null && !uiLanguage.isBlank() && !uiLanguage.equalsIgnoreCase("en")) {
            logger.info("[AppConfig] UI_LANGUAGE set to '{}'", uiLanguage);
            if (translatorApiKey != null && !translatorApiKey.isBlank()) {
                logger.info("[AppConfig] TRANSLATOR_API_KEY is configured");
            }
            logger.info("[AppConfig] TRANSLATOR_ENDPOINT: {}", translatorEndpoint);
            logger.info("[AppConfig] TRANSLATOR_REGION: {}", translatorRegion);
        }
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

    public int getCpuStressThreadPoolSize() {
        return cpuStressThreadPoolSize;
    }

    public void setCpuStressThreadPoolSize(int cpuStressThreadPoolSize) {
        this.cpuStressThreadPoolSize = cpuStressThreadPoolSize;
    }

    public String getUiLanguage() {
        return uiLanguage;
    }

    public void setUiLanguage(String uiLanguage) {
        this.uiLanguage = uiLanguage;
    }

    public String getTranslatorApiKey() {
        return translatorApiKey;
    }

    public void setTranslatorApiKey(String translatorApiKey) {
        this.translatorApiKey = translatorApiKey;
    }

    public String getTranslatorEndpoint() {
        return translatorEndpoint;
    }

    public void setTranslatorEndpoint(String translatorEndpoint) {
        this.translatorEndpoint = translatorEndpoint;
    }

    public String getTranslatorRegion() {
        return translatorRegion;
    }

    public void setTranslatorRegion(String translatorRegion) {
        this.translatorRegion = translatorRegion;
    }
}
