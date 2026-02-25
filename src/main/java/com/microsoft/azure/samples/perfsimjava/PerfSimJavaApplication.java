package com.microsoft.azure.samples.perfsimjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * =============================================================================
 * PERFORMANCE PROBLEM SIMULATOR - JAVA 21
 * =============================================================================
 *
 * PURPOSE:
 *   Educational tool for Azure App Service diagnostics training.
 *   Intentionally creates controllable performance problems to help support
 *   engineers learn to diagnose common Java application issues.
 *
 * SIMULATIONS AVAILABLE:
 *   - CPU Stress: Spawns threads running CPU-intensive work
 *   - Memory Pressure: Allocates and retains heap objects
 *   - Thread Pool Starvation: Blocks servlet threads with sync-over-async patterns
 *   - Slow Requests: Simulates slow HTTP responses with various blocking patterns
 *   - Crash Simulation: Triggers different failure modes (OOM, StackOverflow, etc.)
 *
 * PORTING NOTES:
 *   This is the Java 21 port of PerfSimNode. Key differences from Node.js:
 *   - Uses thread pools instead of single event loop
 *   - "Event Loop Blocking" becomes "Thread Pool Starvation"
 *   - WebSocket via Spring WebSocket (STOMP/SockJS) instead of Socket.IO
 *   - JVM-based metrics instead of V8 heap metrics
 *
 * WARNING:
 *   This application intentionally causes performance problems.
 *   DO NOT deploy to production without proper safeguards.
 */
@SpringBootApplication
@EnableScheduling
public class PerfSimJavaApplication {

    public static void main(String[] args) {
        // Print startup banner
        System.out.println("=".repeat(70));
        System.out.println(" Performance Problem Simulator - Java Blessed Image (Java|21)");
        System.out.println("=".repeat(70));
        System.out.println(" WARNING: This application intentionally causes performance problems!");
        System.out.println(" Use only in controlled environments for training purposes.");
        System.out.println("=".repeat(70));

        SpringApplication.run(PerfSimJavaApplication.class, args);
    }
}
