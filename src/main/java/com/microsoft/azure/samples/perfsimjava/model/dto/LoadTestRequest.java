package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

    @Min(value = 1, message = "workIterations must be at least 1")
    @Max(value = 10000, message = "workIterations cannot exceed 10000")
    private int workIterations = 200;

    @Min(value = 100, message = "bufferSizeKb must be at least 100")
    @Max(value = 500000, message = "bufferSizeKb cannot exceed 500000")
    private int bufferSizeKb = 20000;

    @Min(value = 100, message = "baselineDelayMs must be at least 100")
    @Max(value = 60000, message = "baselineDelayMs cannot exceed 60000")
    private int baselineDelayMs = 500;

    @Min(value = 1, message = "softLimit must be at least 1")
    @Max(value = 1000, message = "softLimit cannot exceed 1000")
    private int softLimit = 25;

    @Min(value = 0, message = "degradationFactor cannot be negative")
    @Max(value = 10000, message = "degradationFactor cannot exceed 10000")
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
