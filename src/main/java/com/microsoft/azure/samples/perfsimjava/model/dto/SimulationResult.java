package com.microsoft.azure.samples.perfsimjava.model.dto;

import com.microsoft.azure.samples.perfsimjava.model.SimulationType;

import java.time.Instant;
import java.util.Map;

/**
 * =============================================================================
 * SIMULATION RESULT DTO
 * =============================================================================
 *
 * Represents the result of triggering a performance problem simulation.
 * Returned by all simulation trigger endpoints.
 *
 * The SimulationId is a unique GUID that enables end-to-end correlation between
 * the dashboard UI and Azure Application Insights telemetry, allowing users to
 * filter logs and traces for a specific simulation run.
 */
public class SimulationResult {

    /**
     * Unique identifier for tracking this simulation instance.
     * Use this ID to correlate metrics and logs with the specific simulation.
     * This ID flows to Application Insights via Activity tags for KQL queries.
     */
    private String simulationId;

    /**
     * The type of performance problem that was triggered.
     */
    private SimulationType type;

    /**
     * Current status of the simulation.
     * Possible values: Started, Completed, Failed, Cancelled
     */
    private String status;

    /**
     * Human-readable description of what happened or is happening.
     */
    private String message;

    /**
     * The actual parameters used for the simulation.
     * May differ from requested parameters if limits were applied.
     */
    private Map<String, Object> actualParameters;

    /**
     * When this simulation started.
     */
    private Instant startedAt;

    /**
     * When this simulation is expected to complete.
     * Null for simulations with no defined end (e.g., memory allocation holds until released).
     */
    private Instant estimatedEndAt;

    // Constructors

    public SimulationResult() {
        this.startedAt = Instant.now();
    }

    public SimulationResult(String simulationId, SimulationType type, String status, String message) {
        this();
        this.simulationId = simulationId;
        this.type = type;
        this.status = status;
        this.message = message;
    }

    // Builder-style setters for fluent API

    public SimulationResult simulationId(String simulationId) {
        this.simulationId = simulationId;
        return this;
    }

    public SimulationResult type(SimulationType type) {
        this.type = type;
        return this;
    }

    public SimulationResult status(String status) {
        this.status = status;
        return this;
    }

    public SimulationResult message(String message) {
        this.message = message;
        return this;
    }

    public SimulationResult actualParameters(Map<String, Object> actualParameters) {
        this.actualParameters = actualParameters;
        return this;
    }

    public SimulationResult estimatedEndAt(Instant estimatedEndAt) {
        this.estimatedEndAt = estimatedEndAt;
        return this;
    }

    // Getters and setters

    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public SimulationType getType() {
        return type;
    }

    public void setType(SimulationType type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getActualParameters() {
        return actualParameters;
    }

    public void setActualParameters(Map<String, Object> actualParameters) {
        this.actualParameters = actualParameters;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEstimatedEndAt() {
        return estimatedEndAt;
    }

    public void setEstimatedEndAt(Instant estimatedEndAt) {
        this.estimatedEndAt = estimatedEndAt;
    }
}
