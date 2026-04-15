package com.microsoft.azure.samples.perfsimjava.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * =============================================================================
 * EVENT LOG ENTRY
 * =============================================================================
 *
 * Represents an entry in the event log ring buffer.
 * Events are displayed in the dashboard and help trace simulation activity.
 */
public class EventLogEntry {

    /**
     * Log severity levels.
     */
    public enum Level {
        INFO,
        WARN,
        ERROR
    }

    /**
     * Types of events that can be logged.
     */
    public enum EventType {
        SIMULATION_STARTED,
        SIMULATION_STOPPED,
        SIMULATION_COMPLETED,
        SIMULATION_FAILED,
        SIMULATION_PROGRESS,
        CRASH_WARNING,
        MEMORY_ALLOCATING,
        MEMORY_ALLOCATED,
        MEMORY_RELEASED,
        SERVER_STARTED,
        DISCLAIMER,
        CLIENT_CONNECTED,
        CLIENT_DISCONNECTED,
        LOAD_TEST_STATS,
        GOING_IDLE,
        WAKING_UP
    }

    private String id;
    private Instant timestamp;
    private Level level;
    private EventType event;
    private String message;
    private String simulationId;
    private SimulationType simulationType;
    private Map<String, Object> details;
    private String messageKey;
    private Map<String, Object> messageParams;

    public EventLogEntry() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public EventLogEntry(Level level, EventType event, String message) {
        this();
        this.level = level;
        this.event = event;
        this.message = message;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public EventType getEvent() {
        return event;
    }

    public void setEvent(EventType event) {
        this.event = event;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public SimulationType getSimulationType() {
        return simulationType;
    }

    public void setSimulationType(SimulationType simulationType) {
        this.simulationType = simulationType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public Map<String, Object> getMessageParams() {
        return messageParams;
    }

    public void setMessageParams(Map<String, Object> messageParams) {
        this.messageParams = messageParams;
    }
}
