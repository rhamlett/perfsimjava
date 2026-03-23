package com.microsoft.azure.samples.perfsimjava.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * =============================================================================
 * SIMULATION TELEMETRY SERVICE — Application Insights Correlation
 * =============================================================================
 *
 * PURPOSE:
 *   Provides methods to correlate simulation IDs with Azure Application Insights
 *   telemetry. When a simulation starts, its unique ID is added to:
 *   
 *   1. OpenTelemetry span attributes (for Application Insights correlation)
 *   2. SLF4J MDC (for structured logging correlation)
 *   3. Server logs (for kubectl and App Service log analysis)
 *
 * TELEMETRY FLOW:
 *   Dashboard → API → This Service → OpenTelemetry Span → Azure Monitor
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
 *   // Find all events for a simulation
 *   traces
 *   | where customDimensions["SimulationId"] == "YOUR-GUID-HERE"
 *   | order by timestamp asc
 *
 *   // Find failed requests during simulation
 *   requests
 *   | where customDimensions["SimulationId"] == "YOUR-GUID-HERE"
 *   | where success == false
 *
 * PORTING NOTES:
 *   - .NET: Uses Activity.Current.SetTag("SimulationId", id)
 *   - Node.js: Uses OpenTelemetry span.setAttribute()
 *   - Python: Uses opentelemetry.trace.get_current_span().set_attribute()
 */
@Service
public class SimulationTelemetryService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationTelemetryService.class);
    
    // OpenTelemetry attribute keys (used in KQL queries)
    public static final String SIMULATION_ID_KEY = "SimulationId";
    public static final String SIMULATION_TYPE_KEY = "SimulationType";
    
    // MDC keys for structured logging
    public static final String MDC_SIMULATION_ID = "simulationId";
    public static final String MDC_SIMULATION_TYPE = "simulationType";

    private final Tracer tracer;
    private final boolean telemetryEnabled;

    public SimulationTelemetryService() {
        // Check if Application Insights is configured
        String connectionString = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
        this.telemetryEnabled = connectionString != null && !connectionString.isBlank();
        
        if (telemetryEnabled) {
            // Get the global OpenTelemetry tracer
            // When Azure Monitor agent is attached, this connects to App Insights
            this.tracer = GlobalOpenTelemetry.getTracer("perfsimjava-simulations");
            logger.info("[SimulationTelemetry] Application Insights enabled - simulation IDs will flow to telemetry");
        } else {
            this.tracer = null;
            logger.info("[SimulationTelemetry] Application Insights not configured - telemetry correlation disabled");
        }
    }

    /**
     * Sets the simulation context on the current span for telemetry correlation.
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

        // 1. Set MDC for structured logging (works regardless of App Insights)
        MDC.put(MDC_SIMULATION_ID, simulationId);
        if (simulationType != null) {
            MDC.put(MDC_SIMULATION_TYPE, simulationType);
        }

        // 2. Set OpenTelemetry span attributes if telemetry is enabled
        if (telemetryEnabled) {
            try {
                Span currentSpan = Span.current();
                if (currentSpan != null && currentSpan.isRecording()) {
                    currentSpan.setAttribute(SIMULATION_ID_KEY, simulationId);
                    if (simulationType != null) {
                        currentSpan.setAttribute(SIMULATION_TYPE_KEY, simulationType);
                    }
                }
            } catch (Exception e) {
                // Gracefully handle if OpenTelemetry is not properly initialized
                logger.debug("[SimulationTelemetry] Could not set span attributes: {}", e.getMessage());
            }
        }

        // 3. Log for traditional log analysis (App Service logs, kubectl logs)
        logger.info("[Simulation] Context set - SimulationId={}, Type={}", simulationId, simulationType);
    }

    /**
     * Tracks a simulation started event in telemetry.
     * Creates a custom span/event that can be queried in Application Insights.
     *
     * @param simulationId The unique simulation identifier
     * @param simulationType The type of simulation being started
     */
    public void trackSimulationStarted(String simulationId, String simulationType) {
        setSimulationContext(simulationId, simulationType);
        
        if (telemetryEnabled && tracer != null) {
            try {
                // Create a span for the simulation start event
                Span span = tracer.spanBuilder("SimulationStarted")
                        .setParent(Context.current())
                        .setAttribute(SIMULATION_ID_KEY, simulationId)
                        .setAttribute(SIMULATION_TYPE_KEY, simulationType != null ? simulationType : "Unknown")
                        .startSpan();
                
                // End immediately - this is just a marker event
                span.end();
            } catch (Exception e) {
                logger.debug("[SimulationTelemetry] Could not track simulation start: {}", e.getMessage());
            }
        }
        
        logger.info("[Simulation] Started - SimulationId={}, Type={}", simulationId, simulationType);
    }

    /**
     * Tracks a simulation completed event in telemetry.
     *
     * @param simulationId The unique simulation identifier
     * @param simulationType The type of simulation that completed
     */
    public void trackSimulationCompleted(String simulationId, String simulationType) {
        if (telemetryEnabled && tracer != null) {
            try {
                Span span = tracer.spanBuilder("SimulationCompleted")
                        .setParent(Context.current())
                        .setAttribute(SIMULATION_ID_KEY, simulationId)
                        .setAttribute(SIMULATION_TYPE_KEY, simulationType != null ? simulationType : "Unknown")
                        .startSpan();
                span.end();
            } catch (Exception e) {
                logger.debug("[SimulationTelemetry] Could not track simulation completion: {}", e.getMessage());
            }
        }
        
        logger.info("[Simulation] Completed - SimulationId={}, Type={}", simulationId, simulationType);
        clearSimulationContext();
    }

    /**
     * Tracks a simulation stopped event (user-initiated stop) in telemetry.
     *
     * @param simulationId The unique simulation identifier
     * @param simulationType The type of simulation that was stopped
     */
    public void trackSimulationStopped(String simulationId, String simulationType) {
        if (telemetryEnabled && tracer != null) {
            try {
                Span span = tracer.spanBuilder("SimulationStopped")
                        .setParent(Context.current())
                        .setAttribute(SIMULATION_ID_KEY, simulationId)
                        .setAttribute(SIMULATION_TYPE_KEY, simulationType != null ? simulationType : "Unknown")
                        .startSpan();
                span.end();
            } catch (Exception e) {
                logger.debug("[SimulationTelemetry] Could not track simulation stop: {}", e.getMessage());
            }
        }
        
        logger.info("[Simulation] Stopped - SimulationId={}, Type={}", simulationId, simulationType);
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
