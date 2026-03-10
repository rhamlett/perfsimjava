package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * =============================================================================
 * FAILED REQUESTS SIMULATION REQUEST DTO
 * =============================================================================
 *
 * Request parameters for triggering failed HTTP requests that generate 5xx errors.
 * These requests call the load test endpoint with 100% error probability to produce
 * visible HTTP 500 responses in AppLens and Application Insights.
 *
 * The requests perform enough CPU/memory work to appear in request latency monitoring
 * before failing with various exception types.
 */
public class FailedRequestsRequest {

    /**
     * Number of failed requests to generate.
     * Each request will produce an HTTP 500 response after performing visible work.
     */
    @Min(value = 1, message = "Number of requests must be at least 1")
    @Max(value = 1000, message = "Number of requests must not exceed 1000")
    private int numberOfRequests = 10;

    public int getNumberOfRequests() {
        return numberOfRequests;
    }

    public void setNumberOfRequests(int numberOfRequests) {
        this.numberOfRequests = numberOfRequests;
    }

    @Override
    public String toString() {
        return String.format("FailedRequestsRequest{numberOfRequests=%d}", numberOfRequests);
    }
}
