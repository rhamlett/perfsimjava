package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * =============================================================================
 * IDLE SERVICE — Application Idle State Management
 * =============================================================================
 *
 * PURPOSE:
 *   Tracks application activity and manages idle state. When the application
 *   has been idle for the configured timeout period, health probes are stopped
 *   to reduce unnecessary traffic to AppLens and Application Insights.
 *
 * IDLE BEHAVIOR:
 *   - After IDLE_TIMEOUT_MINUTES of inactivity, the app enters idle state
 *   - In idle state, ProbeService stops sending health probes
 *   - Activity (page loads, load tests, API usage) resets the idle timer
 *   - When activity resumes after idle, the app "wakes up" and probes resume
 *
 * ACTIVITY TRACKING:
 *   The following actions count as activity and reset the idle timer:
 *   - Page loads (via /api/health/activity endpoint)
 *   - Load test requests
 *   - Any simulation start/stop
 *   - WebSocket client connections
 *
 * CONFIGURATION:
 *   - IDLE_TIMEOUT_MINUTES env var: Override default 20 minute timeout
 *   - Set to 0 to disable idle timeout entirely
 *
 * PORTING NOTES:
 *   - Node.js: setInterval with Date.now() tracking
 *   - Python: threading.Timer with datetime tracking
 *   - C#: BackgroundService with DateTimeOffset tracking
 *   - PHP: Session-based tracking or Redis with TTL
 */
@Service
public class IdleService {

    private static final Logger logger = LoggerFactory.getLogger(IdleService.class);

    // Default idle timeout: 20 minutes
    private static final int DEFAULT_IDLE_TIMEOUT_MINUTES = 20;

    private final EventLogService eventLogService;

    // Idle timeout in minutes (0 = disabled)
    private int idleTimeoutMinutes = DEFAULT_IDLE_TIMEOUT_MINUTES;

    // Tracks the last activity timestamp
    private final AtomicReference<Instant> lastActivityTime = new AtomicReference<>(Instant.now());

    // Tracks whether app is currently idle
    private volatile boolean idle = false;

    // Tracks whether we've logged the going-idle message
    private volatile boolean idleLogSent = false;

    public IdleService(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @PostConstruct
    public void init() {
        // Check for IDLE_TIMEOUT_MINUTES environment variable override
        String envTimeout = System.getenv("IDLE_TIMEOUT_MINUTES");
        if (envTimeout != null && !envTimeout.isEmpty()) {
            try {
                int envValue = Integer.parseInt(envTimeout);
                if (envValue >= 0) {
                    this.idleTimeoutMinutes = envValue;
                    if (envValue == 0) {
                        logger.info("[IdleService] Idle timeout DISABLED via IDLE_TIMEOUT_MINUTES=0");
                    } else {
                        logger.info("[IdleService] Idle timeout set to {} minutes from environment", envValue);
                    }
                } else {
                    logger.warn("[IdleService] Invalid IDLE_TIMEOUT_MINUTES value '{}', using default {}m",
                            envTimeout, DEFAULT_IDLE_TIMEOUT_MINUTES);
                }
            } catch (NumberFormatException e) {
                logger.warn("[IdleService] Invalid IDLE_TIMEOUT_MINUTES value '{}', using default {}m",
                        envTimeout, DEFAULT_IDLE_TIMEOUT_MINUTES);
            }
        } else {
            logger.info("[IdleService] Using default idle timeout of {} minutes", DEFAULT_IDLE_TIMEOUT_MINUTES);
        }

        // Initialize last activity time
        lastActivityTime.set(Instant.now());
    }

    /**
     * Records activity, resetting the idle timer.
     * Call this whenever user activity occurs.
     *
     * @param source Description of the activity source (for logging)
     * @return true if this activity woke the app from idle state
     */
    public boolean recordActivity(String source) {
        Instant now = Instant.now();
        lastActivityTime.set(now);

        boolean wasIdle = idle;
        if (wasIdle) {
            idle = false;
            idleLogSent = false;
            logger.info("[IdleService] App waking up from idle state. Source: {}", source);
            eventLogService.warn(EventLogEntry.EventType.WAKING_UP,
                    "App waking up from idle state. There may be gaps in diagnostics and logs.");
        }

        return wasIdle;
    }

    /**
     * Checks if the application is currently idle.
     * Also handles the transition to idle state and logging.
     *
     * @return true if the app is idle (probes should be paused)
     */
    public boolean isIdle() {
        // If idle timeout is disabled, never idle
        if (idleTimeoutMinutes <= 0) {
            return false;
        }

        Instant lastActivity = lastActivityTime.get();
        Duration timeSinceActivity = Duration.between(lastActivity, Instant.now());
        long minutesSinceActivity = timeSinceActivity.toMinutes();

        // Check if we should transition to idle
        if (minutesSinceActivity >= idleTimeoutMinutes) {
            if (!idle) {
                idle = true;
                if (!idleLogSent) {
                    idleLogSent = true;
                    logger.info("[IdleService] Application going idle after {} minutes of inactivity",
                            minutesSinceActivity);
                    eventLogService.warn(EventLogEntry.EventType.GOING_IDLE,
                            "Application going idle, no health probes being sent. There will be gaps in diagnostics and logs.");
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Gets the current idle state without triggering state transitions.
     * Use this for status reporting only.
     *
     * @return true if currently idle
     */
    public boolean isCurrentlyIdle() {
        return idle;
    }

    /**
     * Gets the configured idle timeout in minutes.
     *
     * @return idle timeout minutes (0 = disabled)
     */
    public int getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    /**
     * Gets the time since last activity in seconds.
     *
     * @return seconds since last activity
     */
    public long getSecondsSinceLastActivity() {
        return Duration.between(lastActivityTime.get(), Instant.now()).getSeconds();
    }

    /**
     * Gets the last activity timestamp.
     *
     * @return last activity instant
     */
    public Instant getLastActivityTime() {
        return lastActivityTime.get();
    }

    /**
     * Gets idle service status for API responses.
     *
     * @return map with idle service state
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "idle", idle,
                "idleTimeoutMinutes", idleTimeoutMinutes,
                "idleTimeoutEnabled", idleTimeoutMinutes > 0,
                "secondsSinceLastActivity", getSecondsSinceLastActivity(),
                "lastActivityTime", lastActivityTime.get().toString(),
                "minutesUntilIdle", Math.max(0, idleTimeoutMinutes - getSecondsSinceLastActivity() / 60)
        );
    }
}
