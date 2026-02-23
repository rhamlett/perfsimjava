package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import com.microsoft.azure.samples.perfsimjava.model.dto.CrashRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * =============================================================================
 * CRASH SERVICE — Intentional Process Termination Simulation
 * =============================================================================
 *
 * PURPOSE:
 *   Intentionally crashes the JVM process using different failure modes.
 *   Each crash type produces a different diagnostic signature in monitoring
 *   tools (Azure AppLens, Application Insights), helping users learn to
 *   identify crash types from their diagnostics.
 *
 * CRASH TYPES:
 *   1. FailFast (Runtime.halt / System.exit)
 *      → Immediate termination, no shutdown hooks
 *      → Visible as abrupt process termination in Azure
 *
 *   2. Stack Overflow
 *      → Infinite recursion until stack space exhausted
 *      → Produces StackOverflowError in crash dump
 *      → May not auto-recover on Azure
 *
 *   3. Unhandled Exception
 *      → RuntimeException thrown from a thread
 *      → Standard crash, auto-recovers on Azure App Service
 *
 *   4. Out of Memory (OOM)
 *      → Allocates objects until heap exhausted
 *      → Produces OutOfMemoryError
 *      → May not auto-recover, requires restart
 *
 * SAFETY:
 *   All crash methods use scheduled execution to ensure the HTTP response
 *   is sent BEFORE the process crashes. The crash is deferred by ~100ms.
 *
 * PORTING NOTES:
 *   - Node.js: process.abort(), throw Error, infinite recursion
 *   - Python: sys.exit(1), raise Exception, recursion
 *   - C#: Environment.FailFast(), throw, StackOverflowException
 *   - PHP: exit(1), trigger_error(), recursive function
 */
@Service
public class CrashService {

    private static final Logger logger = LoggerFactory.getLogger(CrashService.class);

    private final EventLogService eventLogService;

    public CrashService(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    /**
     * Triggers a crash of the specified type.
     * The crash is deferred to allow the HTTP response to be sent first.
     */
    public void triggerCrash(CrashRequest request) {
        CrashRequest.CrashType crashType = request.getCrashType();

        // Log the impending crash
        eventLogService.error(
                EventLogEntry.EventType.SIMULATION_STARTED,
                String.format("Crash simulation initiated: %s", crashType),
                null,
                SimulationType.valueOf("CRASH_" + crashType.name()),
                Map.of("method", getMethodDescription(crashType))
        );

        // Warn about recovery for certain crash types
        if (crashType == CrashRequest.CrashType.STACKOVERFLOW || 
            crashType == CrashRequest.CrashType.OOM) {
            eventLogService.warn(
                    EventLogEntry.EventType.CRASH_WARNING,
                    String.format("%s crashes may not auto-recover on Azure App Service. " +
                            "Manual restart from Azure Portal may be required.", crashType),
                    null,
                    null,
                    Map.of("recoveryHint", "Azure Portal > App Service > Restart")
            );
        }

        // Schedule the crash to happen after response is sent
        Executors.newSingleThreadScheduledExecutor().schedule(
                () -> executeCrash(crashType),
                100,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Executes the actual crash.
     */
    private void executeCrash(CrashRequest.CrashType crashType) {
        logger.error("EXECUTING CRASH: {}", crashType);

        switch (crashType) {
            case FAILFAST -> crashWithFailFast();
            case STACKOVERFLOW -> crashWithStackOverflow();
            case EXCEPTION -> crashWithException();
            case OOM -> crashWithOOM();
        }
    }

    /**
     * Immediate process termination via Runtime.halt().
     * Does not run shutdown hooks - immediate death.
     */
    private void crashWithFailFast() {
        logger.error("Crash: FailFast (Runtime.halt)");
        Runtime.getRuntime().halt(1);
    }

    /**
     * Stack overflow via infinite recursion.
     */
    private void crashWithStackOverflow() {
        logger.error("Crash: StackOverflow (infinite recursion)");
        recurseForever();
    }

    private void recurseForever() {
        recurseForever();
    }

    /**
     * Unhandled runtime exception.
     */
    private void crashWithException() {
        logger.error("Crash: Unhandled Exception");
        throw new RuntimeException("Intentional crash: Unhandled exception simulation");
    }

    /**
     * Out of memory via rapid allocation.
     */
    private void crashWithOOM() {
        logger.error("Crash: OutOfMemoryError (rapid allocation)");
        List<byte[]> allocations = new ArrayList<>();
        try {
            while (true) {
                // Allocate 100MB chunks until OOM
                allocations.add(new byte[100 * 1024 * 1024]);
            }
        } catch (OutOfMemoryError e) {
            // This will crash the JVM
            throw e;
        }
    }

    /**
     * Gets method description for logging.
     */
    private String getMethodDescription(CrashRequest.CrashType crashType) {
        return switch (crashType) {
            case FAILFAST -> "Runtime.halt(1)";
            case STACKOVERFLOW -> "infinite recursion";
            case EXCEPTION -> "RuntimeException";
            case OOM -> "OutOfMemoryError (rapid allocation)";
        };
    }
}
