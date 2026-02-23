package com.microsoft.azure.samples.perfsimjava.model.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for crash simulation.
 */
public class CrashRequest {

    public enum CrashType {
        /**
         * System.exit(1) or Runtime.halt(1) - immediate termination.
         */
        FAILFAST,

        /**
         * StackOverflowError via infinite recursion.
         */
        STACKOVERFLOW,

        /**
         * Unhandled RuntimeException.
         */
        EXCEPTION,

        /**
         * OutOfMemoryError via rapid allocation.
         */
        OOM
    }

    @NotNull(message = "Crash type is required")
    private CrashType crashType = CrashType.EXCEPTION;

    public CrashType getCrashType() {
        return crashType;
    }

    public void setCrashType(CrashType crashType) {
        this.crashType = crashType;
    }
}
