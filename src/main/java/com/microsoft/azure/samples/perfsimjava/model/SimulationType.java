package com.microsoft.azure.samples.perfsimjava.model;

/**
 * =============================================================================
 * SIMULATION TYPE ENUMERATION
 * =============================================================================
 *
 * Types of performance simulations available in the system.
 * Each type corresponds to a distinct simulation service and API endpoint.
 *
 * PORTING NOTES:
 *   - Node.js: Union type string literal
 *   - Python: Enum class
 *   - C#: enum with [JsonConverter(typeof(JsonStringEnumConverter))]
 *   - PHP: Backed enum (PHP 8.1+)
 */
public enum SimulationType {
    /**
     * Spawns threads running CPU-intensive work to burn CPU cores.
     * Uses ExecutorService with tasks performing cryptographic operations.
     */
    CPU_STRESS,

    /**
     * Allocates heap objects to consume memory and trigger GC pressure.
     * Memory is held until explicitly released via DELETE endpoint.
     */
    MEMORY_PRESSURE,

    /**
     * Blocks servlet threads with synchronous operations.
     * This is the Java equivalent of "Event Loop Blocking" in Node.js.
     * In Java's thread-per-request model, blocking a thread prevents it
     * from serving other requests, eventually exhausting the thread pool.
     */
    THREAD_STARVATION,

    /**
     * Simulates database connection pool exhaustion.
     * Uses a Semaphore to mimic HikariCP/JDBC pool behavior.
     * Demonstrates what happens when slow queries hold connections too long.
     */
    CONNECTION_POOL_EXHAUSTION,

    /**
     * Crashes via unhandled exception.
     */
    CRASH_EXCEPTION,

    /**
     * Crashes via OutOfMemoryError (allocates until OOM).
     */
    CRASH_MEMORY,

    /**
     * Terminates via System.exit(1) or Runtime.halt(1).
     */
    CRASH_FAILFAST,

    /**
     * Crashes via StackOverflowError (infinite recursion).
     */
    CRASH_STACKOVERFLOW,

    /**
     * Generates HTTP 5xx responses by triggering load test requests with 100% error probability.
     * These failed requests perform visible "work" to appear in request latency monitoring
     * and produce HTTP 500 errors visible in AppLens and Application Insights.
     */
    FAILED_REQUESTS
}
