package com.microsoft.azure.samples.perfsimjava.model;

import java.time.Instant;
import java.util.Map;

/**
 * =============================================================================
 * LOAD TEST RESULT
 * =============================================================================
 *
 * Response returned by the load test endpoint containing timing metrics
 * and request details for analysis.
 */
public class LoadTestResult {

    private String requestId;
    private Instant startTime;
    private Instant endTime;
    private long durationMs;
    private long calculatedDelayMs;
    private int concurrentAtStart;
    private int concurrentAtEnd;
    private int bufferSizeKb;
    private int workIterations;
    private boolean success;
    private String errorMessage;
    private String errorType;
    private Map<String, Object> parameters;

    public LoadTestResult() {
    }

    // Getters and Setters

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public long getCalculatedDelayMs() {
        return calculatedDelayMs;
    }

    public void setCalculatedDelayMs(long calculatedDelayMs) {
        this.calculatedDelayMs = calculatedDelayMs;
    }

    public int getConcurrentAtStart() {
        return concurrentAtStart;
    }

    public void setConcurrentAtStart(int concurrentAtStart) {
        this.concurrentAtStart = concurrentAtStart;
    }

    public int getConcurrentAtEnd() {
        return concurrentAtEnd;
    }

    public void setConcurrentAtEnd(int concurrentAtEnd) {
        this.concurrentAtEnd = concurrentAtEnd;
    }

    public int getBufferSizeKb() {
        return bufferSizeKb;
    }

    public void setBufferSizeKb(int bufferSizeKb) {
        this.bufferSizeKb = bufferSizeKb;
    }

    public int getWorkIterations() {
        return workIterations;
    }

    public void setWorkIterations(int workIterations) {
        this.workIterations = workIterations;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
