package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.FailedRequestsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * =============================================================================
 * FAILED REQUESTS SERVICE
 * =============================================================================
 *
 * PURPOSE:
 *   Generates HTTP 5xx responses by triggering load test requests with 100% error
 *   probability. These failed requests perform visible "work" (CPU and memory) so
 *   they appear in request latency monitoring before failing with various exception types.
 *
 * HOW IT WORKS:
 *   1. Spawns internal HTTP requests to the /api/loadtest endpoint
 *   2. Configures 100% error probability with immediate error injection (errorAfter=0)
 *   3. Each request performs meaningful work (CPU/memory) before failing
 *   4. The errors produce HTTP 500 responses visible in AppLens and Application Insights
 *
 * DIAGNOSTIC VALUE:
 *   - AppLens: HTTP 5xx errors will be visible in error analysis
 *   - Application Insights: Failed requests with exception details
 *   - Request Latency Monitor: Visible latency spikes before error
 *   - Common scenarios: Simulating application errors under load
 */
@Service
public class FailedRequestsService {

    private static final Logger logger = LoggerFactory.getLogger(FailedRequestsService.class);

    private final SimulationTrackerService simulationTracker;
    private final EventLogService eventLogService;
    private final SimulationTelemetryService telemetryService;

    @Value("${server.port:8080}")
    private int serverPort;

    // Track active simulations
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    
    // Scheduler for spawning requests
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // HTTP client for internal requests
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    // Track statistics
    private final AtomicInteger completedRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicInteger pendingRequests = new AtomicInteger(0);
    private volatile String activeSimulationId = null;
    private volatile int totalRequestsForCompletion = 0;

    public FailedRequestsService(SimulationTrackerService simulationTracker,
                                  EventLogService eventLogService,
                                  SimulationTelemetryService telemetryService) {
        this.simulationTracker = simulationTracker;
        this.eventLogService = eventLogService;
        this.telemetryService = telemetryService;
    }

    /**
     * Triggers a failed requests simulation.
     * Spawns internal HTTP requests that will produce 5xx errors.
     */
    public Simulation trigger(FailedRequestsRequest request) {
        int numberOfRequests = request.getNumberOfRequests();

        // Reset statistics
        completedRequests.set(0);
        failedRequests.set(0);
        pendingRequests.set(numberOfRequests);
        totalRequestsForCompletion = numberOfRequests;

        // Create simulation record
        Map<String, Object> params = Map.of(
                "type", SimulationType.FAILED_REQUESTS,
                "numberOfRequests", numberOfRequests
        );
        
        // Estimate 2-3 seconds per request (1 second work + error handling)
        int estimatedDuration = numberOfRequests * 2;
        Simulation simulation = simulationTracker.createSimulation(
                SimulationType.FAILED_REQUESTS,
                params,
                estimatedDuration
        );

        // Initialize stop flag
        String simId = simulation.getId();
        activeSimulationId = simId;
        stopFlags.put(simId, new AtomicBoolean(false));
        AtomicBoolean stopFlag = stopFlags.get(simId);

        // Log the start
        eventLogService.warn(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Failed Requests: Generating %d HTTP 5xx errors", numberOfRequests),
                simId,
                SimulationType.FAILED_REQUESTS,
                params,
                "srv.failed.started", Map.of("count", numberOfRequests)
        );

        // Track simulation start in Application Insights
        telemetryService.trackSimulationStarted(simId, SimulationType.FAILED_REQUESTS.name());

        // Spawn all requests via internal HTTP calls
        // Each request calls the load test endpoint with 100% error probability
        for (int i = 0; i < numberOfRequests && !stopFlag.get(); i++) {
            final int requestNum = i + 1;
            scheduler.submit(() -> executeFailedRequest(simId, stopFlag, requestNum));
        }

        return simulation;
    }

    /**
     * Executes a single request that is configured to fail.
     * Uses the load test endpoint with 100% error probability.
     */
    private void executeFailedRequest(String simulationId, AtomicBoolean stopFlag, int requestNum) {
        if (stopFlag.get()) {
            pendingRequests.decrementAndGet();
            checkCompletion(simulationId);
            return;
        }

        try {
            // Build URL with parameters for guaranteed failure:
            // - errorAfter=1: Start error injection after 1 second (0 disables it)
            // - errorPercent=100: 100% chance of error on each work cycle
            // - baselineDelayMs=2000: 2 seconds of visible work to ensure error triggers
            // - workIterations=300: Moderate CPU work to appear in latency monitor
            // - bufferSizeKb=5000: 5MB memory to appear in metrics
            // - internal=true: Exclude from load test stats logging
            String url = String.format(
                    "http://localhost:%d/api/loadtest?errorAfter=1&errorPercent=100&baselineDelayMs=2000&workIterations=300&bufferSizeKb=5000&internal=true",
                    serverPort
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            logger.debug("[FailedRequests:{}] Sending request #{}", simulationId, requestNum);

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            
            if (statusCode >= 500) {
                // Expected failure - extract error type from response if available
                String errorType = extractErrorType(response.body());
                failedRequests.incrementAndGet();
                
                eventLogService.error(
                        EventLogEntry.EventType.SIMULATION_FAILED,
                        String.format("Failed Request #%d: HTTP %d - %s", requestNum, statusCode, errorType),
                        simulationId,
                        SimulationType.FAILED_REQUESTS,
                        Map.of("requestNum", requestNum, "statusCode", statusCode, "errorType", errorType),
                        "srv.failed.error", Map.of("requestNum", requestNum, "statusCode", statusCode, "errorType", errorType)
                );
                
                logger.info("[FailedRequests:{}] Request #{} failed as expected: HTTP {} - {}",
                        simulationId, requestNum, statusCode, errorType);
            } else {
                // Unexpected success - log as warning
                eventLogService.warn(
                        EventLogEntry.EventType.SIMULATION_PROGRESS,
                        String.format("Failed Request #%d: Unexpected HTTP %d (expected 5xx)", requestNum, statusCode),
                        simulationId,
                        SimulationType.FAILED_REQUESTS,
                        Map.of("requestNum", requestNum, "statusCode", statusCode),
                        "srv.failed.unexpected", Map.of("requestNum", requestNum, "statusCode", statusCode)
                );
                
                logger.warn("[FailedRequests:{}] Request #{} unexpectedly succeeded: HTTP {}",
                        simulationId, requestNum, statusCode);
            }

        } catch (Exception e) {
            // Network/timeout errors also count as failures
            failedRequests.incrementAndGet();
            String errorType = e.getClass().getSimpleName();
            
            eventLogService.error(
                    EventLogEntry.EventType.SIMULATION_FAILED,
                    String.format("Failed Request #%d: %s - %s", requestNum, errorType, e.getMessage()),
                    simulationId,
                    SimulationType.FAILED_REQUESTS,
                    Map.of("requestNum", requestNum, "errorType", errorType, "message", e.getMessage()),
                    "srv.failed.exception", Map.of("requestNum", requestNum, "errorType", errorType, "errorMessage", String.valueOf(e.getMessage()))
            );
            
            logger.error("[FailedRequests:{}] Request #{} threw exception: {} - {}",
                    simulationId, requestNum, errorType, e.getMessage());
        } finally {
            completedRequests.incrementAndGet();
            pendingRequests.decrementAndGet();
            checkCompletion(simulationId);
        }
    }

    /**
     * Extracts the error type from the load test response body.
     */
    private String extractErrorType(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "UnknownError";
        }
        
        // Try to extract error type from JSON response
        // Expected format: {"error": "...", "errorType": "IllegalStateException", ...}
        try {
            int typeIndex = responseBody.indexOf("\"errorType\"");
            if (typeIndex >= 0) {
                int colonIndex = responseBody.indexOf(":", typeIndex);
                int quoteStart = responseBody.indexOf("\"", colonIndex + 1);
                int quoteEnd = responseBody.indexOf("\"", quoteStart + 1);
                if (quoteStart >= 0 && quoteEnd > quoteStart) {
                    return responseBody.substring(quoteStart + 1, quoteEnd);
                }
            }
            
            // Try extracting from "exception" or "error" field
            int errorIndex = responseBody.indexOf("\"exception\"");
            if (errorIndex < 0) {
                errorIndex = responseBody.indexOf("\"error\"");
            }
            if (errorIndex >= 0) {
                int colonIndex = responseBody.indexOf(":", errorIndex);
                int quoteStart = responseBody.indexOf("\"", colonIndex + 1);
                int quoteEnd = responseBody.indexOf("\"", quoteStart + 1);
                if (quoteStart >= 0 && quoteEnd > quoteStart) {
                    String error = responseBody.substring(quoteStart + 1, quoteEnd);
                    // Extract just the exception class name if it's a full message
                    if (error.contains(":")) {
                        return error.split(":")[0].trim();
                    }
                    return error;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse error type from response: {}", e.getMessage());
        }
        
        return "ServerError";
    }

    /**
     * Checks if simulation is complete and logs summary.
     */
    private void checkCompletion(String simulationId) {
        if (!simulationId.equals(activeSimulationId)) {
            return;
        }

        int completed = completedRequests.get();
        if (completed >= totalRequestsForCompletion) {
            int failed = failedRequests.get();
            
            // Mark simulation as completed
            simulationTracker.completeSimulation(simulationId);
            stopFlags.remove(simulationId);

            eventLogService.info(
                    EventLogEntry.EventType.SIMULATION_COMPLETED,
                    String.format("Failed Requests completed: %d/%d requests produced 5xx errors",
                            failed, totalRequestsForCompletion),
                    simulationId,
                    SimulationType.FAILED_REQUESTS,
                    Map.of(
                            "totalRequests", totalRequestsForCompletion,
                            "failedRequests", failed,
                            "successRate", String.format("%.1f%%", (double) failed / totalRequestsForCompletion * 100)
                    ),
                    "srv.failed.completed", Map.of("failed", failed, "total", totalRequestsForCompletion)
            );

            telemetryService.trackSimulationCompleted(simulationId, SimulationType.FAILED_REQUESTS.name());

            logger.info("[FailedRequests:{}] Simulation completed: {}/{} failed",
                    simulationId, failed, totalRequestsForCompletion);

            activeSimulationId = null;
        }
    }

    /**
     * Gets current simulation statistics.
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "pending", pendingRequests.get(),
                "completed", completedRequests.get(),
                "failed", failedRequests.get(),
                "total", totalRequestsForCompletion,
                "inProgress", activeSimulationId != null
        );
    }

    /**
     * Returns list of active simulations.
     */
    public List<Simulation> getActiveSimulations() {
        return simulationTracker.getActiveSimulationsByType(SimulationType.FAILED_REQUESTS);
    }

    /**
     * Stops all active failed requests simulations.
     */
    public void stopAll() {
        stopFlags.values().forEach(flag -> flag.set(true));
        
        if (activeSimulationId != null) {
            simulationTracker.completeSimulation(activeSimulationId);
            
            eventLogService.warn(
                    EventLogEntry.EventType.SIMULATION_STOPPED,
                    String.format("Failed Requests stopped: %d/%d completed, %d failed",
                            completedRequests.get(), totalRequestsForCompletion, failedRequests.get()),
                    activeSimulationId,
                    SimulationType.FAILED_REQUESTS,
                    Map.of(
                            "completed", completedRequests.get(),
                            "failed", failedRequests.get(),
                            "total", totalRequestsForCompletion
                    ),
                    "srv.failed.stopped", Map.of("completed", completedRequests.get(), "total", totalRequestsForCompletion, "failed", failedRequests.get())
            );
            
            telemetryService.trackSimulationStopped(activeSimulationId, SimulationType.FAILED_REQUESTS.name());
        }

        stopFlags.clear();
        activeSimulationId = null;
        pendingRequests.set(0);

        logger.info("[FailedRequests] All simulations stopped");
    }
}
