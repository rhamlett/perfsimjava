package com.microsoft.azure.samples.perfsimjava.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * =============================================================================
 * SIMULATION RECORD
 * =============================================================================
 *
 * Represents an active or completed simulation instance.
 * Tracked by SimulationTrackerService and exposed via REST API.
 *
 * LIFECYCLE:
 *   1. Created when simulation is triggered (status = ACTIVE)
 *   2. Updated when simulation completes (status = COMPLETED/STOPPED/FAILED)
 *   3. Retained for event log history (not deleted immediately)
 */
public class Simulation {

    private String id;
    private SimulationType type;
    private SimulationStatus status;
    private Map<String, Object> parameters;
    private Instant startedAt;
    private Instant scheduledEndAt;
    private Instant stoppedAt;

    public Simulation() {
        this.id = UUID.randomUUID().toString();
        this.status = SimulationStatus.ACTIVE;
        this.startedAt = Instant.now();
    }

    public Simulation(SimulationType type, Map<String, Object> parameters, int durationSeconds) {
        this();
        this.type = type;
        this.parameters = parameters;
        this.scheduledEndAt = this.startedAt.plusSeconds(durationSeconds);
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SimulationType getType() {
        return type;
    }

    public void setType(SimulationType type) {
        this.type = type;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public void setStatus(SimulationStatus status) {
        this.status = status;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getScheduledEndAt() {
        return scheduledEndAt;
    }

    public void setScheduledEndAt(Instant scheduledEndAt) {
        this.scheduledEndAt = scheduledEndAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    /**
     * Marks the simulation as completed normally.
     */
    public void complete() {
        this.status = SimulationStatus.COMPLETED;
        this.stoppedAt = Instant.now();
    }

    /**
     * Marks the simulation as stopped by user.
     */
    public void stop() {
        this.status = SimulationStatus.STOPPED;
        this.stoppedAt = Instant.now();
    }

    /**
     * Marks the simulation as failed.
     */
    public void fail() {
        this.status = SimulationStatus.FAILED;
        this.stoppedAt = Instant.now();
    }
}
