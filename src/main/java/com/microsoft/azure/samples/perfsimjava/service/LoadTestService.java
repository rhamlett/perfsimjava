package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.LoadTestResult;
import com.microsoft.azure.samples.perfsimjava.model.LoadTestStats;
import com.microsoft.azure.samples.perfsimjava.model.dto.LoadTestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * =============================================================================
 * LOAD TEST SERVICE — Lightweight Load Testing Endpoint
 * =============================================================================
 *
 * PURPOSE:
 *   Provides a lightweight endpoint that performs CPU work and memory allocation
 *   suitable for load testing. When compounded by high volume, these requests
 *   slow down and can lead to Azure App Service frontend timeouts (230 seconds).
 *
 * HOW IT WORKS:
 *   1. Allocates a memory buffer of configurable size (default 20MB)
 *   2. Calculates delay based on concurrent requests and degradation formula
 *   3. Runs CPU work cycles interleaved with memory touches and sleeps
 *   4. After 120 seconds elapsed, introduces 20% chance of random exception
 *   5. Tracks statistics and broadcasts them periodically via event log
 *
 * DEGRADATION FORMULA:
 *   totalDelay = baselineDelayMs + max(0, concurrent - softLimit) * degradationFactor
 *
 *   This causes response times to increase as concurrent requests exceed the
 *   soft limit, simulating resource contention under load.
 *
 * EXCEPTION INJECTION:
 *   After processing for 120+ seconds, each work cycle has a 20% chance of
 *   throwing a random exception. This simulates timeout-related failures
 *   that occur under sustained high load.
 *
 * EXCEPTION TYPES:
 *   - IllegalStateException (simulates InvalidOperationException)
 *   - NullPointerException
 *   - TimeoutException
 *   - IOException
 *   - OutOfMemoryError (simulated via exception)
 *   - ArithmeticException (divide by zero)
 *   - IllegalArgumentException
 *   - IndexOutOfBoundsException
 *   - UnsupportedOperationException
 *   - InterruptedException (re-thrown as RuntimeException)
 *   - SecurityException
 *   - ClassCastException
 *
 * PORTING NOTES:
 *   Similar to .NET Core implementation with:
 *   - Thread.SpinWait equivalent using busy-wait loop
 *   - AtomicInteger for concurrent request tracking
 *   - byte[] array for memory allocation with periodic touches
 */
@Service
public class LoadTestService {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestService.class);

    // Exception injection settings
    private static final long EXCEPTION_THRESHOLD_MS = 120_000; // 120 seconds
    private static final double EXCEPTION_PROBABILITY = 0.20; // 20%

    // Work cycle settings
    private static final int CYCLE_SLEEP_MS = 50; // Yield time between work cycles
    private static final int PAGE_SIZE = 4096; // Memory page size for touching

    private final EventLogService eventLogService;
    private final Random random = new Random();

    // Concurrent request tracking
    private final AtomicInteger currentConcurrent = new AtomicInteger(0);
    private final AtomicInteger peakConcurrent = new AtomicInteger(0);

    // Period statistics (reset every 60 seconds)
    private final AtomicLong periodTotalRequests = new AtomicLong(0);
    private final AtomicLong periodSuccessfulRequests = new AtomicLong(0);
    private final AtomicLong periodFailedRequests = new AtomicLong(0);
    private final AtomicLong periodTotalResponseTimeMs = new AtomicLong(0);
    private final AtomicLong periodMaxResponseTimeMs = new AtomicLong(0);
    private final AtomicLong periodMinResponseTimeMs = new AtomicLong(Long.MAX_VALUE);
    private volatile Instant periodStartTime = Instant.now();
    private volatile Instant lastRequestTime = null;

    // All-time statistics
    private final AtomicLong allTimeTotalRequests = new AtomicLong(0);
    private final AtomicLong allTimeSuccessfulRequests = new AtomicLong(0);
    private final AtomicLong allTimeFailedRequests = new AtomicLong(0);

    // Exception types for random injection
    private static final Class<?>[] EXCEPTION_TYPES = {
            IllegalStateException.class,
            NullPointerException.class,
            TimeoutException.class,
            IOException.class,
            ArithmeticException.class,
            IllegalArgumentException.class,
            IndexOutOfBoundsException.class,
            UnsupportedOperationException.class,
            SecurityException.class,
            ClassCastException.class
    };

    private static final String[] EXCEPTION_MESSAGES = {
            "Operation is not valid due to the current state of the object",
            "Object reference not set to an instance of an object",
            "The operation has timed out",
            "I/O error occurred during processing",
            "Attempted to divide by zero",
            "Invalid argument provided",
            "Index was out of range",
            "Operation is not supported",
            "Security violation detected",
            "Specified cast is not valid"
    };

    public LoadTestService(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
        logger.info("[LoadTestService] Initialized with exception threshold {}ms, probability {}%",
                EXCEPTION_THRESHOLD_MS, EXCEPTION_PROBABILITY * 100);
    }

    /**
     * Executes a load test request with configurable CPU work and memory usage.
     *
     * @param request The load test parameters
     * @return Result containing timing metrics
     * @throws RuntimeException if random exception is triggered after 120s
     */
    public LoadTestResult execute(LoadTestRequest request) throws Exception {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        Instant startTime = Instant.now();
        long startMs = System.currentTimeMillis();

        // Increment concurrent request counter
        int concurrent = currentConcurrent.incrementAndGet();
        updatePeakConcurrent(concurrent);

        logger.debug("[LoadTest:{}] Started - concurrent={}, params={}", requestId, concurrent, request);

        LoadTestResult result = new LoadTestResult();
        result.setRequestId(requestId);
        result.setStartTime(startTime);
        result.setConcurrentAtStart(concurrent);
        result.setBufferSizeKb(request.getBufferSizeKb());
        result.setWorkIterations(request.getWorkIterations());
        result.setParameters(Map.of(
                "workIterations", request.getWorkIterations(),
                "bufferSizeKb", request.getBufferSizeKb(),
                "baselineDelayMs", request.getBaselineDelayMs(),
                "softLimit", request.getSoftLimit(),
                "degradationFactor", request.getDegradationFactor()
        ));

        byte[] buffer = null;
        Exception thrownException = null;

        try {
            // Step 1: Allocate memory buffer
            buffer = allocateBuffer(request.getBufferSizeKb());

            // Step 2: Calculate delay based on concurrent requests
            int overLimit = Math.max(0, concurrent - request.getSoftLimit());
            long degradationDelay = (long) overLimit * request.getDegradationFactor();
            long totalDelayMs = request.getBaselineDelayMs() + degradationDelay;
            result.setCalculatedDelayMs(totalDelayMs);

            logger.debug("[LoadTest:{}] Calculated delay={}ms (baseline={}, overLimit={}, degradation={})",
                    requestId, totalDelayMs, request.getBaselineDelayMs(), overLimit, degradationDelay);

            // Step 3: Run sustained work loop
            executeSustainedWorkLoop(requestId, buffer, request.getWorkIterations(), totalDelayMs, startMs);

            result.setSuccess(true);
            periodSuccessfulRequests.incrementAndGet();
            allTimeSuccessfulRequests.incrementAndGet();

        } catch (Exception e) {
            thrownException = e;
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setErrorType(e.getClass().getSimpleName());
            periodFailedRequests.incrementAndGet();
            allTimeFailedRequests.incrementAndGet();
            logger.warn("[LoadTest:{}] Failed with {}: {}", requestId, e.getClass().getSimpleName(), e.getMessage());
        } finally {
            // Ensure memory is released by nulling the reference
            buffer = null;

            // Update concurrent counter
            int finalConcurrent = currentConcurrent.decrementAndGet();
            result.setConcurrentAtEnd(finalConcurrent);

            // Calculate duration
            long endMs = System.currentTimeMillis();
            long durationMs = endMs - startMs;
            result.setEndTime(Instant.now());
            result.setDurationMs(durationMs);

            // Update statistics
            updateStatistics(durationMs);

            logger.debug("[LoadTest:{}] Completed - duration={}ms, concurrent={}, success={}",
                    requestId, durationMs, finalConcurrent, result.isSuccess());
        }

        // Re-throw exception after cleanup if one occurred
        if (thrownException != null) {
            throw thrownException;
        }

        return result;
    }

    /**
     * Returns current load test statistics.
     */
    public LoadTestStats getStats() {
        LoadTestStats stats = new LoadTestStats();
        stats.setCurrentConcurrent(currentConcurrent.get());
        stats.setPeakConcurrent(peakConcurrent.get());
        stats.setTotalRequests(allTimeTotalRequests.get());
        stats.setSuccessfulRequests(allTimeSuccessfulRequests.get());
        stats.setFailedRequests(allTimeFailedRequests.get());

        long total = allTimeTotalRequests.get();
        long failed = allTimeFailedRequests.get();
        stats.setErrorRate(total > 0 ? (double) failed / total * 100 : 0);

        // Period statistics
        stats.setPeriodStartTime(periodStartTime);
        stats.setLastRequestTime(lastRequestTime);

        long periodRequests = periodTotalRequests.get();
        long periodSeconds = java.time.Duration.between(periodStartTime, Instant.now()).getSeconds();
        stats.setPeriodDurationSeconds(periodSeconds);

        if (periodRequests > 0) {
            stats.setAvgResponseTimeMs((double) periodTotalResponseTimeMs.get() / periodRequests);
            stats.setMaxResponseTimeMs(periodMaxResponseTimeMs.get());
            long minVal = periodMinResponseTimeMs.get();
            stats.setMinResponseTimeMs(minVal == Long.MAX_VALUE ? 0 : minVal);
            stats.setRequestsPerSecond(periodSeconds > 0 ? (double) periodRequests / periodSeconds : 0);
        } else {
            stats.setAvgResponseTimeMs(0);
            stats.setMaxResponseTimeMs(0);
            stats.setMinResponseTimeMs(0);
            stats.setRequestsPerSecond(0);
        }

        return stats;
    }

    /**
     * Resets period statistics and logs summary.
     * Called every 60 seconds by scheduler.
     */
    @Scheduled(fixedRate = 60000)
    public void resetPeriodStats() {
        LoadTestStats stats = getStats();

        // Only log if there were requests in this period
        if (periodTotalRequests.get() > 0) {
            eventLogService.info(
                    EventLogEntry.EventType.LOAD_TEST_STATS,
                    String.format("Load test period stats: %d requests, %.1f avg ms, %d max ms, %.2f RPS, %.1f%% errors",
                            periodTotalRequests.get(),
                            stats.getAvgResponseTimeMs(),
                            stats.getMaxResponseTimeMs(),
                            stats.getRequestsPerSecond(),
                            stats.getErrorRate()),
                    null,
                    null,
                    Map.of(
                            "periodRequests", periodTotalRequests.get(),
                            "avgResponseTimeMs", stats.getAvgResponseTimeMs(),
                            "maxResponseTimeMs", stats.getMaxResponseTimeMs(),
                            "requestsPerSecond", stats.getRequestsPerSecond(),
                            "errorRate", stats.getErrorRate(),
                            "currentConcurrent", stats.getCurrentConcurrent(),
                            "peakConcurrent", stats.getPeakConcurrent()
                    )
            );

            logger.info("[LoadTestService] Period stats: requests={}, avgMs={:.1f}, maxMs={}, rps={:.2f}, errors={:.1f}%",
                    periodTotalRequests.get(),
                    stats.getAvgResponseTimeMs(),
                    stats.getMaxResponseTimeMs(),
                    stats.getRequestsPerSecond(),
                    stats.getErrorRate());
        }

        // Reset period counters
        periodTotalRequests.set(0);
        periodSuccessfulRequests.set(0);
        periodFailedRequests.set(0);
        periodTotalResponseTimeMs.set(0);
        periodMaxResponseTimeMs.set(0);
        periodMinResponseTimeMs.set(Long.MAX_VALUE);
        periodStartTime = Instant.now();
    }

    /**
     * Allocates a memory buffer and touches each page to ensure real allocation.
     */
    private byte[] allocateBuffer(int bufferSizeKb) {
        int bufferBytes = bufferSizeKb * 1024;
        byte[] buffer = new byte[bufferBytes];

        // Touch every page to ensure memory is actually allocated (not just reserved)
        // This forces the OS to commit physical memory pages
        for (int i = 0; i < bufferBytes; i += PAGE_SIZE) {
            buffer[i] = (byte) (i & 0xFF);
        }

        logger.trace("[LoadTest] Allocated and touched {}KB buffer", bufferSizeKb);
        return buffer;
    }

    /**
     * Executes the sustained work loop with CPU work, memory touches, and sleeps.
     */
    private void executeSustainedWorkLoop(String requestId, byte[] buffer, int workIterations,
                                           long totalDelayMs, long startMs) throws Exception {
        long targetEndMs = startMs + totalDelayMs;
        int workDurationPerCycleMs = Math.max(1, workIterations / 100);
        int cycleCount = 0;

        while (System.currentTimeMillis() < targetEndMs) {
            cycleCount++;
            long elapsedMs = System.currentTimeMillis() - startMs;

            // Check for exception injection after threshold
            if (elapsedMs >= EXCEPTION_THRESHOLD_MS) {
                checkAndThrowRandomException(requestId, elapsedMs);
            }

            // CPU work via busy-wait spin loop
            doCpuWork(workDurationPerCycleMs);

            // Touch memory buffer to keep it active and prevent optimization
            touchBuffer(buffer);

            // Yield to prevent 100% CPU saturation
            Thread.sleep(CYCLE_SLEEP_MS);
        }

        logger.trace("[LoadTest:{}] Completed {} work cycles", requestId, cycleCount);
    }

    /**
     * Performs CPU-intensive work via spin loop for specified duration.
     */
    private void doCpuWork(int durationMs) {
        long endTime = System.currentTimeMillis() + durationMs;
        double result = 0;

        // Busy-wait spin loop with some computation to prevent compiler optimization
        while (System.currentTimeMillis() < endTime) {
            for (int i = 0; i < 1000; i++) {
                result += Math.sin(i) * Math.cos(i);
            }
        }

        // Use result to prevent dead code elimination
        if (result == Double.NEGATIVE_INFINITY) {
            logger.trace("Spin result: {}", result);
        }
    }

    /**
     * Touches the buffer periodically to keep it in active memory.
     */
    private void touchBuffer(byte[] buffer) {
        if (buffer == null || buffer.length == 0) return;

        // Touch a few random pages to keep buffer active
        int numTouches = Math.min(10, buffer.length / PAGE_SIZE);
        for (int i = 0; i < numTouches; i++) {
            int index = random.nextInt(buffer.length);
            buffer[index] = (byte) (buffer[index] + 1);
        }
    }

    /**
     * Checks if a random exception should be thrown and throws it.
     * 20% probability after 120 seconds of processing.
     */
    private void checkAndThrowRandomException(String requestId, long elapsedMs) throws Exception {
        if (random.nextDouble() < EXCEPTION_PROBABILITY) {
            int index = random.nextInt(EXCEPTION_TYPES.length);
            Class<?> exceptionType = EXCEPTION_TYPES[index];
            String message = EXCEPTION_MESSAGES[index] +
                    String.format(" [Injected after %ds, requestId=%s]", elapsedMs / 1000, requestId);

            logger.warn("[LoadTest:{}] Injecting {} after {}ms", requestId, exceptionType.getSimpleName(), elapsedMs);

            // Create and throw the exception
            Exception ex = createException(exceptionType, message);
            throw ex;
        }
    }

    /**
     * Creates an exception instance of the specified type.
     */
    private Exception createException(Class<?> type, String message) {
        try {
            return (Exception) type.getConstructor(String.class).newInstance(message);
        } catch (Exception e) {
            // Fallback to RuntimeException
            return new RuntimeException(message);
        }
    }

    /**
     * Updates peak concurrent counter atomically.
     */
    private void updatePeakConcurrent(int newValue) {
        int current;
        do {
            current = peakConcurrent.get();
            if (newValue <= current) {
                return;
            }
        } while (!peakConcurrent.compareAndSet(current, newValue));
    }

    /**
     * Updates statistics counters atomically.
     */
    private void updateStatistics(long durationMs) {
        periodTotalRequests.incrementAndGet();
        allTimeTotalRequests.incrementAndGet();
        periodTotalResponseTimeMs.addAndGet(durationMs);
        lastRequestTime = Instant.now();

        // Update max
        long currentMax;
        do {
            currentMax = periodMaxResponseTimeMs.get();
            if (durationMs <= currentMax) break;
        } while (!periodMaxResponseTimeMs.compareAndSet(currentMax, durationMs));

        // Update min
        long currentMin;
        do {
            currentMin = periodMinResponseTimeMs.get();
            if (durationMs >= currentMin) break;
        } while (!periodMinResponseTimeMs.compareAndSet(currentMin, durationMs));
    }
}
