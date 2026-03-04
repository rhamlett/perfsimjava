package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.LoadTestResult;
import com.microsoft.azure.samples.perfsimjava.model.LoadTestStats;
import com.microsoft.azure.samples.perfsimjava.model.dto.LoadTestRequest;
import com.microsoft.azure.samples.perfsimjava.service.LoadTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * =============================================================================
 * LOAD TEST CONTROLLER — Load Testing REST API
 * =============================================================================
 *
 * PURPOSE:
 *   REST endpoints for load testing. The main endpoint performs lightweight work
 *   that uses CPU and memory, suitable for generating load that can eventually
 *   lead to Azure App Service frontend timeouts (230 seconds).
 *
 * ENDPOINTS:
 *   GET  /api/loadtest       → Execute a load test request
 *   GET  /api/loadtest/stats → Get load test statistics
 *
 * USAGE:
 *   This endpoint is designed to be hit repeatedly by load testing tools
 *   (Azure Load Testing, Apache JMeter, k6, etc.). Under sustained load:
 *
 *   1. Response times increase as concurrent requests exceed the soft limit
 *   2. After 120 seconds of processing, 20% chance of random exception
 *   3. Memory pressure builds as more requests hold buffers simultaneously
 *
 * PARAMETERS (query string):
 *   workIterations    - CPU work intensity per cycle (default: 200)
 *   bufferSizeKb      - Memory held per request in KB (default: 20000)
 *   baselineDelayMs   - Minimum request duration in ms (default: 500)
 *   softLimit         - Concurrent requests before degradation (default: 25)
 *   degradationFactor - Additional delay ms per request over limit (default: 500)
 *
 * EXAMPLE:
 *   GET /api/loadtest?workIterations=300&bufferSizeKb=10000&baselineDelayMs=1000
 *
 * NOTE:
 *   This endpoint does not appear in the UI dashboard. It is documented in
 *   the Azure Diagnostics and API Documentation pages.
 */
@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestController.class);

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    /**
     * Executes a load test request.
     *
     * Performs CPU work and holds memory for the calculated duration based on
     * current concurrency and degradation settings. After 120 seconds of
     * processing, has a 20% chance of throwing a random exception.
     *
     * @param workIterations    CPU work intensity per cycle (1-10000, default: 200)
     * @param bufferSizeKb      Memory buffer size in KB (100-500000, default: 20000)
     * @param baselineDelayMs   Minimum duration in ms (100-60000, default: 500)
     * @param softLimit         Max concurrent before degradation (1-1000, default: 25)
     * @param degradationFactor Delay per request over limit (0-10000, default: 500)
     * @return Load test result with timing metrics
     */
    @GetMapping
    public ResponseEntity<?> execute(
            @RequestParam(required = false, defaultValue = "200") int workIterations,
            @RequestParam(required = false, defaultValue = "20000") int bufferSizeKb,
            @RequestParam(required = false, defaultValue = "500") int baselineDelayMs,
            @RequestParam(required = false, defaultValue = "25") int softLimit,
            @RequestParam(required = false, defaultValue = "500") int degradationFactor) {

        // Build request from query parameters
        LoadTestRequest request = new LoadTestRequest();
        request.setWorkIterations(clamp(workIterations, 1, 10000));
        request.setBufferSizeKb(clamp(bufferSizeKb, 100, 500000));
        request.setBaselineDelayMs(clamp(baselineDelayMs, 100, 60000));
        request.setSoftLimit(clamp(softLimit, 1, 1000));
        request.setDegradationFactor(clamp(degradationFactor, 0, 10000));

        try {
            LoadTestResult result = loadTestService.execute(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // Return error details for injected exceptions
            logger.warn("[LoadTestController] Request failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "errorType", e.getClass().getSimpleName(),
                    "errorMessage", e.getMessage(),
                    "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

    /**
     * Returns current load test statistics.
     *
     * Includes:
     * - Current and peak concurrent requests
     * - Total, successful, and failed request counts
     * - Error rate percentage
     * - Average, min, and max response times
     * - Requests per second
     */
    @GetMapping("/stats")
    public ResponseEntity<LoadTestStats> getStats() {
        return ResponseEntity.ok(loadTestService.getStats());
    }

    /**
     * Clamps a value between min and max bounds.
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
