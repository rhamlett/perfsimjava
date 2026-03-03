package com.microsoft.azure.samples.perfsimjava.model;

import java.time.Instant;

/**
 * =============================================================================
 * LOAD TEST STATS
 * =============================================================================
 *
 * Aggregated statistics for the load test endpoint, tracking request counts,
 * timing metrics, and error rates.
 */
public class LoadTestStats {

    private int currentConcurrent;
    private int peakConcurrent;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private double errorRate;
    private double avgResponseTimeMs;
    private long maxResponseTimeMs;
    private long minResponseTimeMs;
    private double requestsPerSecond;
    private Instant periodStartTime;
    private Instant lastRequestTime;
    private long periodDurationSeconds;

    public LoadTestStats() {
        this.minResponseTimeMs = Long.MAX_VALUE;
    }

    // Getters and Setters

    public int getCurrentConcurrent() {
        return currentConcurrent;
    }

    public void setCurrentConcurrent(int currentConcurrent) {
        this.currentConcurrent = currentConcurrent;
    }

    public int getPeakConcurrent() {
        return peakConcurrent;
    }

    public void setPeakConcurrent(int peakConcurrent) {
        this.peakConcurrent = peakConcurrent;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(long successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(long failedRequests) {
        this.failedRequests = failedRequests;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getAvgResponseTimeMs() {
        return avgResponseTimeMs;
    }

    public void setAvgResponseTimeMs(double avgResponseTimeMs) {
        this.avgResponseTimeMs = avgResponseTimeMs;
    }

    public long getMaxResponseTimeMs() {
        return maxResponseTimeMs;
    }

    public void setMaxResponseTimeMs(long maxResponseTimeMs) {
        this.maxResponseTimeMs = maxResponseTimeMs;
    }

    public long getMinResponseTimeMs() {
        return minResponseTimeMs;
    }

    public void setMinResponseTimeMs(long minResponseTimeMs) {
        this.minResponseTimeMs = minResponseTimeMs;
    }

    public double getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(double requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public Instant getPeriodStartTime() {
        return periodStartTime;
    }

    public void setPeriodStartTime(Instant periodStartTime) {
        this.periodStartTime = periodStartTime;
    }

    public Instant getLastRequestTime() {
        return lastRequestTime;
    }

    public void setLastRequestTime(Instant lastRequestTime) {
        this.lastRequestTime = lastRequestTime;
    }

    public long getPeriodDurationSeconds() {
        return periodDurationSeconds;
    }

    public void setPeriodDurationSeconds(long periodDurationSeconds) {
        this.periodDurationSeconds = periodDurationSeconds;
    }
}
