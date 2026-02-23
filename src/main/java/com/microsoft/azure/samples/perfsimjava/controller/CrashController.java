package com.microsoft.azure.samples.perfsimjava.controller;

import com.microsoft.azure.samples.perfsimjava.model.dto.CrashRequest;
import com.microsoft.azure.samples.perfsimjava.service.CrashService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * =============================================================================
 * CRASH CONTROLLER — Application Crash Simulation REST API
 * =============================================================================
 *
 * WARNING: These endpoints will terminate the JVM process!
 *
 * ENDPOINTS:
 *   POST /api/simulations/crash              → Trigger crash (body: crashType)
 *   POST /api/simulations/crash/failfast     → Trigger failfast crash
 *   POST /api/simulations/crash/stackoverflow → Trigger stack overflow
 *   POST /api/simulations/crash/exception    → Trigger unhandled exception
 *   POST /api/simulations/crash/oom          → Trigger out of memory
 */
@RestController
@RequestMapping("/api/simulations/crash")
public class CrashController {

    private final CrashService crashService;

    public CrashController(CrashService crashService) {
        this.crashService = crashService;
    }

    /**
     * Triggers a crash with the specified type.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crash(@Valid @RequestBody CrashRequest request) {
        crashService.triggerCrash(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", "Crash initiated - process will terminate shortly",
                "crashType", request.getCrashType(),
                "warning", "This will TERMINATE the application!"
        ));
    }

    /**
     * Triggers FailFast crash (Runtime.halt).
     */
    @PostMapping("/failfast")
    public ResponseEntity<Map<String, Object>> failfast() {
        CrashRequest request = new CrashRequest();
        request.setCrashType(CrashRequest.CrashType.FAILFAST);
        return crash(request);
    }

    /**
     * Triggers StackOverflow crash.
     */
    @PostMapping("/stackoverflow")
    public ResponseEntity<Map<String, Object>> stackoverflow() {
        CrashRequest request = new CrashRequest();
        request.setCrashType(CrashRequest.CrashType.STACKOVERFLOW);
        return crash(request);
    }

    /**
     * Triggers unhandled exception crash.
     */
    @PostMapping("/exception")
    public ResponseEntity<Map<String, Object>> exception() {
        CrashRequest request = new CrashRequest();
        request.setCrashType(CrashRequest.CrashType.EXCEPTION);
        return crash(request);
    }

    /**
     * Triggers OutOfMemory crash.
     */
    @PostMapping("/oom")
    public ResponseEntity<Map<String, Object>> oom() {
        CrashRequest request = new CrashRequest();
        request.setCrashType(CrashRequest.CrashType.OOM);
        return crash(request);
    }
}
