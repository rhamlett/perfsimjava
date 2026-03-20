package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =============================================================================
 * EVENT LOG SERVICE — Event Recording and Broadcasting
 * =============================================================================
 *
 * PURPOSE:
 *   Maintains an in-memory list of event log entries and broadcasts
 *   them to connected WebSocket clients. Events provide visibility into
 *   simulation lifecycle and system state changes.
 *
 * MEMORY:
 *   The log is cleared on each page reset/app restart, so no limit is
 *   enforced. For long-running sessions, consider periodic clearing.
 *
 * BROADCASTING:
 *   New events are immediately pushed to all WebSocket clients via
 *   SimpMessagingTemplate. Clients don't need to poll for updates.
 *
 * PORTING NOTES:
 *   - Node.js: Array with Socket.IO emit for broadcast
 *   - Python: list with Flask-SocketIO emit
 *   - C#: List with SignalR Clients.All.SendAsync
 *   - PHP: Session storage or Redis list with LPUSH
 */
@Service
public class EventLogService {

    private static final Logger logger = LoggerFactory.getLogger(EventLogService.class);

    private final LinkedList<EventLogEntry> entries = new LinkedList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final SimpMessagingTemplate messagingTemplate;

    public EventLogService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Logs an INFO level event.
     */
    public EventLogEntry info(EventLogEntry.EventType event, String message) {
        return log(EventLogEntry.Level.INFO, event, message, null, null, null);
    }

    /**
     * Logs an INFO level event with simulation context.
     */
    public EventLogEntry info(EventLogEntry.EventType event, String message, 
                               String simulationId, SimulationType simulationType, 
                               Map<String, Object> details) {
        return log(EventLogEntry.Level.INFO, event, message, simulationId, simulationType, details);
    }

    /**
     * Logs a WARN level event.
     */
    public EventLogEntry warn(EventLogEntry.EventType event, String message) {
        return log(EventLogEntry.Level.WARN, event, message, null, null, null);
    }

    /**
     * Logs a WARN level event with simulation context.
     */
    public EventLogEntry warn(EventLogEntry.EventType event, String message,
                               String simulationId, SimulationType simulationType,
                               Map<String, Object> details) {
        return log(EventLogEntry.Level.WARN, event, message, simulationId, simulationType, details);
    }

    /**
     * Logs an ERROR level event.
     */
    public EventLogEntry error(EventLogEntry.EventType event, String message) {
        return log(EventLogEntry.Level.ERROR, event, message, null, null, null);
    }

    /**
     * Logs an ERROR level event with simulation context.
     */
    public EventLogEntry error(EventLogEntry.EventType event, String message,
                                String simulationId, SimulationType simulationType,
                                Map<String, Object> details) {
        return log(EventLogEntry.Level.ERROR, event, message, simulationId, simulationType, details);
    }

    /**
     * Core logging method.
     */
    private EventLogEntry log(EventLogEntry.Level level, EventLogEntry.EventType event,
                               String message, String simulationId, 
                               SimulationType simulationType, Map<String, Object> details) {
        EventLogEntry entry = new EventLogEntry(level, event, message);
        entry.setSimulationId(simulationId);
        entry.setSimulationType(simulationType);
        entry.setDetails(details);

        // Log to console as well
        switch (level) {
            case INFO -> logger.info("[{}] {}", event, message);
            case WARN -> logger.warn("[{}] {}", event, message);
            case ERROR -> logger.error("[{}] {}", event, message);
        }

        // Add to event log
        lock.lock();
        try {
            entries.addFirst(entry);
        } finally {
            lock.unlock();
        }

        // Broadcast to WebSocket clients
        broadcast(entry);

        return entry;
    }

    /**
     * Gets recent event log entries.
     *
     * @param count Maximum number of entries to return
     * @return List of recent entries (newest first)
     */
    public List<EventLogEntry> getRecentEntries(int count) {
        lock.lock();
        try {
            int limit = Math.min(count, entries.size());
            return new ArrayList<>(entries.subList(0, limit));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets all event log entries.
     *
     * @return List of all entries (newest first)
     */
    public List<EventLogEntry> getAllEntries() {
        lock.lock();
        try {
            return new ArrayList<>(entries);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Broadcasts an event to all WebSocket clients.
     */
    private void broadcast(EventLogEntry entry) {
        try {
            messagingTemplate.convertAndSend("/topic/events", entry);
        } catch (Exception e) {
            logger.warn("Failed to broadcast event: {}", e.getMessage());
        }
    }
}
