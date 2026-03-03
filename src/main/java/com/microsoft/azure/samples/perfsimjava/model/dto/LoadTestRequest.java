package com.microsoft.azure.samples.perfsimjava.model.dto;

/**
 * =============================================================================
 * LOAD TEST REQUEST DTO
 * =============================================================================
 *
 * Request parameters for load testing endpoint. All parameters have sensible
 * defaults optimized for demonstrating load-related behavior on Azure App Service.
 *
 * PARAMETERS:
 *   workIterations    - Controls CPU work intensity per cycle
 *   bufferSizeKb      - Memory held for request duration
 *   baselineDelayMs   - Minimum request duration
 *   softLimit         - Concurrent requests before degradation begins
 *   degradationFactor - Additional delay per request over soft limit
 *
 * DEGRADATION FORMULA:
 *   totalDelay = baselineDelayMs + max(0, concurrent - softLimit) * degradationFactor
 */
public class LoadTestRequest {

    private int workIterations = 200;

    private int bufferSizeKb = 20000;

    private int baselineDelayMs = 500;

    private int softLimit = 25;

    private int degradationFactor = 500;

    // Getters and Setters

    public int getWorkIterations() {
        return workIterations;
    }

    public void setWorkIterations(int workIterations) {
        this.workIterations = workIterations;
    }

    public int getBufferSizeKb() {
        return bufferSizeKb;
    }

    public void setBufferSizeKb(int bufferSizeKb) {
        this.bufferSizeKb = bufferSizeKb;
    }

    public int getBaselineDelayMs() {
        return baselineDelayMs;
    }

    public void setBaselineDelayMs(int baselineDelayMs) {
        this.baselineDelayMs = baselineDelayMs;
    }

    public int getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(int softLimit) {
        this.softLimit = softLimit;
    }

    public int getDegradationFactor() {
        return degradationFactor;
    }

    public void setDegradationFactor(int degradationFactor) {
        this.degradationFactor = degradationFactor;
    }

    @Override
    public String toString() {
        return String.format("LoadTestRequest{workIterations=%d, bufferSizeKb=%d, baselineDelayMs=%d, softLimit=%d, degradationFactor=%d}",
                workIterations, bufferSizeKb, baselineDelayMs, softLimit, degradationFactor);
    }
}
