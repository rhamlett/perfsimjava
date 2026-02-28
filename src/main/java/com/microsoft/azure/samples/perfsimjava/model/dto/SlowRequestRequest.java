package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for slow request simulation.
 */
public class SlowRequestRequest {

    public enum BlockingPattern {
        /**
         * Thread.sleep() - non-blocking to thread pool, just delays response.
         */
        SLEEP,

        /**
         * ExecutorService saturation - blocks the common ForkJoinPool.
         */
        EXECUTOR_SATURATION,

        /**
         * Synchronous blocking with JDBC-like patterns.
         */
        SYNC_BLOCKING
    }

    @Min(value = 1, message = "Delay must be at least 1 second")
    private int delaySeconds = 60;

    private BlockingPattern blockingPattern = BlockingPattern.SYNC_BLOCKING;

    private double intervalSeconds = 1;

    @Min(value = 1, message = "Max requests must be at least 1")
    private int maxRequests = 1;

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public BlockingPattern getBlockingPattern() {
        return blockingPattern;
    }

    public void setBlockingPattern(BlockingPattern blockingPattern) {
        this.blockingPattern = blockingPattern;
    }

    public double getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(double intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }
}
