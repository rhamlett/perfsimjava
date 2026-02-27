package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for CPU stress simulation.
 */
public class CpuStressRequest {

    public enum Intensity {
        MODERATE,
        HIGH
    }

    @NotNull(message = "Intensity is required")
    private Intensity intensity;

    @Min(value = 1, message = "Duration must be at least 1 second")
    private int durationSeconds = 30;

    public Intensity getIntensity() {
        return intensity;
    }

    public void setIntensity(Intensity intensity) {
        this.intensity = intensity;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
