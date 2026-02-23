package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.dto.SlowRequestRequest;
import com.microsoft.azure.samples.perfsimjava.service.SlowRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * =============================================================================
 * SLOW REQUEST CONTROLLER — Slow Response Simulation REST API
 * =============================================================================
 *
 * ENDPOINTS:
 *   GET  /api/simulations/slow → Execute a slow request (GET for browser testing)
 *   POST /api/simulations/slow → Execute a slow request with parameters
 */
@RestController
@RequestMapping("/api/simulations/slow")
public class SlowRequestController {

    private final SlowRequestService slowRequestService;

    public SlowRequestController(SlowRequestService slowRequestService) {
        this.slowRequestService = slowRequestService;
    }

    /**
     * GET endpoint for easy browser/curl testing with query params.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> slowGet(
            @RequestParam(defaultValue = "10") int delaySeconds,
            @RequestParam(defaultValue = "SLEEP") SlowRequestRequest.BlockingPattern blockingPattern) {
        
        SlowRequestRequest request = new SlowRequestRequest();
        request.setDelaySeconds(delaySeconds);
        request.setBlockingPattern(blockingPattern);

        return executeSlow(request);
    }

    /**
     * POST endpoint for programmatic use with JSON body.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> slowPost(@Valid @RequestBody SlowRequestRequest request) {
        return executeSlow(request);
    }

    private ResponseEntity<Map<String, Object>> executeSlow(SlowRequestRequest request) {
        long startTime = System.currentTimeMillis();
        Simulation simulation = slowRequestService.delay(request);
        long duration = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(Map.of(
                "id", simulation.getId(),
                "type", simulation.getType(),
                "status", simulation.getStatus(),
                "message", String.format("Slow request completed after %dms", duration),
                "parameters", simulation.getParameters(),
                "actualDurationMs", duration
        ));
    }
}
