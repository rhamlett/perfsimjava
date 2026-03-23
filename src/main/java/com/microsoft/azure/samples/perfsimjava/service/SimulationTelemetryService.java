package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.telemetry.EventTelemetry;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * =============================================================================
 * SIMULATION TELEMETRY SERVICE — Application Insights Correlation
 * =============================================================================
 *
 * PURPOSE:
 *   Provides methods to correlate simulation IDs with Azure Application Insights
 *   telemetry. When a simulation starts, its unique ID is:
 *   
 *   1. Sent as a custom event to Application Insights (SimulationStarted/SimulationEnded)
 *   2. Set on the current OpenTelemetry span for request correlation
 *   3. Added to SLF4J MDC for structured logging correlation
 *   4. Logged to server output for kubectl/App Service log analysis
 *
 * CONFIGURATION:
 *   Application Insights is optional. If the APPLICATIONINSIGHTS_CONNECTION_STRING
 *   environment variable is not set, this service gracefully degrades:
 *   - Simulation IDs still appear in event log
 *   - MDC context still available for log correlation
 *   - No telemetry is sent to Azure (no-op)
 *
 *   Azure App Service automatically sets APPLICATIONINSIGHTS_CONNECTION_STRING
 *   when Application Insights is enabled in the portal.
 *
 * KQL CORRELATION:
 *   Once telemetry flows to Application Insights, use these queries:
 *
 *   // Find simulation events
 *   AppEvents
 *   | where Name in ("SimulationStarted", "SimulationEnded")
 *   | where Properties["SimulationId"] == "YOUR-GUID-HERE"
 *   | project TimeGenerated, Name, Properties
 *   | order by TimeGenerated asc
 *
 * PORTING NOTES:
 *   - .NET: Uses TelemetryClient.TrackEvent()
 *   - Node.js: Uses applicationinsights.defaultClient.trackEvent()
 *   - Python: Uses opencensus or azure-monitor-opentelemetry
 */
@Service
public class SimulationTelemetryService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationTelemetryService.class);
    
    // Custom event names (used in KQL queries)
    public static final String EVENT_SIMULATION_STARTED = "SimulationStarted";
    public static final String EVENT_SIMULATION_ENDED = "SimulationEnded";
    
    // Property keys
    public static final String SIMULATION_ID_KEY = "SimulationId";
    public static final String SIMULATION_TYPE_KEY = "SimulationType";
    public static final String END_REASON_KEY = "EndReason";
    
    // MDC keys for structured logging
    public static final String MDC_SIMULATION_ID = "simulationId";
    public static final String MDC_SIMULATION_TYPE = "simulationType";

    private final TelemetryClient telemetryClient;
    private final boolean telemetryEnabled;

    public SimulationTelemetryService() {
        // Check if Application Insights is configured
        String connectionString = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
        this.telemetryEnabled = connectionString != null && !connectionString.isBlank();
        
        if (telemetryEnabled) {
            // Initialize TelemetryClient with connection string
            TelemetryConfiguration config = TelemetryConfiguration.createDefault();
            config.setConnectionString(connectionString);
            this.telemetryClient = new TelemetryClient(config);
            logger.info("[SimulationTelemetry] Application Insights enabled - custom events will be sent");
        } else {
            this.telemetryClient = null;
            logger.info("[SimulationTelemetry] Application Insights not configured - telemetry disabled");
        }
    }

    /**
     * Sets the simulation context on the current span and MDC for telemetry correlation.
     * Call this at the start of a simulation to ensure all subsequent telemetry
     * is tagged with the simulation ID.
     *
     * @param simulationId The unique simulation identifier (GUID)
     * @param simulationType The type of simulation (e.g., CPU_STRESS, MEMORY_PRESSURE)
     */
    public void setSimulationContext(String simulationId, String simulationType) {
        if (simulationId == null || simulationId.isBlank()) {
            return;
        }

        // Set MDC for structured logging (works regardless of App Insights)
        MDC.put(MDC_SIMULATION_ID, simulationId);
        if (simulationType != null) {
            MDC.put(MDC_SIMULATION_TYPE, simulationType);
        }

        // Set on current OpenTelemetry span if available
        try {
            Span currentSpan = Span.current();
            if (currentSpan != null && currentSpan.isRecording()) {
                currentSpan.setAttribute(SIMULATION_ID_KEY, simulationId);
                if (simulationType != null) {
                    currentSpan.setAttribute(SIMULATION_TYPE_KEY, simulationType);
                }
            }
        } catch (Exception e) {
            logger.debug("[SimulationTelemetry] Could not set span attributes: {}", e.getMessage());
        }

        logger.info("[Simulation] Context set - SimulationId={}, Type={}", simulationId, simulationType);
    }

    /**
     * Tracks a simulation started event in Application Insights.
     * Creates a custom event that can be queried in Log Analytics.
     *
     * @param simulationId The unique simulation identifier
     * @param simulationType The type of simulation being started
     */
    public void trackSimulationStarted(String simulationId, String simulationType) {
        setSimulationContext(simulationId, simulationType);
        
        if (telemetryEnabled && telemetryClient != null) {
            try {
                EventTelemetry event = new EventTelemetry(EVENT_SIMULATION_STARTED);
                event.getProperties().put(SIMULATION_ID_KEY, simulationId);
                event.getProperties().put(SIMULATION_TYPE_KEY, simulationType != null ? simulationType : "Unknown");
                
                telemetryClient.trackEvent(event);
                telemetryClient.flush();
                
                logger.debug("[SimulationTelemetry] Tracked SimulationStarted event");
            } catch (Exception e) {
                logger.warn("[SimulationTelemetry] Could not track simulation start: {}", e.getMessage());
            }
        }
        
        logger.info("[Simulation] Started - SimulationId={}, Type={}", simulationId, simulationType);
    }

    /**
     * Tracks a simulation completed event in Application Insights.
     *
     * @param simulationId The unique simulation identifier
     * @param simulationType The type of simulation that completed
     */
    public void trackSimulationCompleted(String simulationId, String simulationType) {
        trackSimulationEnded(simulationId, simulationType, "Completed");
    }

    /**
     * Tracks a simulation stopped event (user-initiated stop) in Application Insights.
     *
     * @param simulationId The unique simulation identifier
     * @param simulationType The type of simulation that was stopped
     */
    public void trackSimulationStopped(String simulationId, String simulationType) {
        trackSimulationEnded(simulationId, simulationType, "Stopped");
    }
    
    /**
     * Internal method to track simulation end events.
     */
    private void trackSimulationEnded(String simulationId, String simulationType, String reason) {
        if (telemetryEnabled && telemetryClient != null) {
            try {
                EventTelemetry event = new EventTelemetry(EVENT_SIMULATION_ENDED);
                event.getProperties().put(SIMULATION_ID_KEY, simulationId);
                event.getProperties().put(SIMULATION_TYPE_KEY, simulationType != null ? simulationType : "Unknown");
                event.getProperties().put(END_REASON_KEY, reason);
                
                telemetryClient.trackEvent(event);
                telemetryClient.flush();
                
                logger.debug("[SimulationTelemetry] Tracked SimulationEnded event ({})", reason);
            } catch (Exception e) {
                logger.warn("[SimulationTelemetry] Could not track simulation end: {}", e.getMessage());
            }
        }
        
        logger.info("[Simulation] {} - SimulationId={}, Type={}", reason, simulationId, simulationType);
        clearSimulationContext();
    }

    /**
     * Clears the simulation context from MDC.
     * Call this when a simulation ends to prevent context leakage.
     */
    public void clearSimulationContext() {
        MDC.remove(MDC_SIMULATION_ID);
        MDC.remove(MDC_SIMULATION_TYPE);
    }

    /**
     * Returns whether Application Insights telemetry is enabled.
     * Useful for conditional behaviors or debugging.
     */
    public boolean isTelemetryEnabled() {
        return telemetryEnabled;
    }
}
