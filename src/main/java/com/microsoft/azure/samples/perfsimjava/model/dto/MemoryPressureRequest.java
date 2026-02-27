package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for memory pressure simulation.
 */
public class MemoryPressureRequest {

    @Min(value = 1, message = "Size must be at least 1 MB")
    private int sizeMb = 512;

    public int getSizeMb() {
        return sizeMb;
    }

    public void setSizeMb(int sizeMb) {
        this.sizeMb = sizeMb;
    }
}
