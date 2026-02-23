package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for thread pool starvation simulation.
 * This is the Java equivalent of "Event Loop Blocking" in Node.js.
 */
public class ThreadStarvationRequest {

    @Min(value = 1, message = "Duration must be at least 1 second")
    @Max(value = 60, message = "Duration cannot exceed 60 seconds")
    private int durationSeconds = 5;

    @Min(value = 1, message = "Thread count must be at least 1")
    @Max(value = 200, message = "Thread count cannot exceed 200")
    private int threadCount = 10;

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
}
