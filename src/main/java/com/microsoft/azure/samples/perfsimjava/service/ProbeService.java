package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * =============================================================================
 * PROBE SERVICE — Request Latency Monitor
 * =============================================================================
 *
 * PURPOSE:
 *   Measures the actual HTTP response latency of the application by sending
 *   periodic probe requests. Unlike simple health checks, this measures
 *   real response time including any thread pool queueing delays.
 *
 * TWO PROBE MODES:
 *   1. LOCAL PROBE (100ms interval):
 *      - Sends HTTP GET to localhost:/api/metrics/probe
 *      - Fast interval for real-time latency monitoring dashboard
 *      - Lightweight, stays within the JVM/container network
 *
 *   2. FRONTEND PROBE (1000ms interval, Azure only):
 *      - Sends HTTP GET through Azure frontend (WEBSITE_HOSTNAME)
 *      - Slower interval to reduce CPU overhead
 *      - Traffic visible in Azure AppLens diagnostics
 *      - Only active when WEBSITE_HOSTNAME environment variable is set
 *
 * WHY TWO PROBES:
 *   - Local probe: High-frequency data for the dashboard latency chart
 *   - Frontend probe: AppLens visibility for Azure diagnostics training
 *   - Separation reduces CPU overhead while maintaining both features
 *
 * PORTING NOTES:
 *   - Node.js: Uses a separate child process (sidecar) for true isolation
 *   - Python: asyncio.create_task() or separate thread
 *   - C#: BackgroundService with HttpClient
 *   - PHP: External script (self-probe.sh) using curl
 */
@Service
public class ProbeService {

    private static final Logger logger = LoggerFactory.getLogger(ProbeService.class);
    
    // Frontend probe interval - fixed at 1 second for AppLens visibility
    private static final int FRONTEND_PROBE_INTERVAL_MS = 1000;

    private final SimpMessagingTemplate messagingTemplate;
    private final AppConfig config;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    // Local probe state
    private String localProbeUrl;
    private final AtomicLong localProbeCount = new AtomicLong(0);
    private final AtomicLong localErrorCount = new AtomicLong(0);
    private volatile long lastLocalLatencyMs = 0;

    // Frontend probe state (for AppLens)
    private String frontendProbeUrl;
    private final AtomicLong frontendProbeCount = new AtomicLong(0);
    private final AtomicLong frontendErrorCount = new AtomicLong(0);
    private volatile long lastFrontendLatencyMs = 0;
    private volatile boolean frontendProbeEnabled = false;

    public ProbeService(SimpMessagingTemplate messagingTemplate, AppConfig config) {
        this.messagingTemplate = messagingTemplate;
        this.config = config;

        // Create dedicated HTTP client for probing
        // Uses a small thread pool to avoid blocking
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newFixedThreadPool(2))
                .build();

        // Single-threaded scheduler for probe tasks
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "probe-service");
            t.setDaemon(true);
            return t;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));

        // Local probe URL - always use localhost
        localProbeUrl = "http://localhost:" + port + "/api/metrics/probe";
        logger.info("[ProbeService] Local probe URL: {}", localProbeUrl);

        // Frontend probe URL - only when running in Azure
        String hostname = System.getenv("WEBSITE_HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            frontendProbeUrl = "https://" + hostname + "/api/metrics/probe";
            frontendProbeEnabled = true;
            logger.info("[ProbeService] Frontend probe enabled: {}", frontendProbeUrl);
        } else {
            logger.info("[ProbeService] Frontend probe disabled (not running in Azure)");
        }

        // Start local probe loop (fast, for dashboard latency chart)
        int localIntervalMs = config.getProbeIntervalMs();
        logger.info("[ProbeService] Starting local probe loop at {}ms interval", localIntervalMs);
        scheduler.scheduleAtFixedRate(
                this::sendLocalProbe,
                5000,  // Initial delay - wait for app to stabilize
                localIntervalMs,
                TimeUnit.MILLISECONDS
        );

        // Start frontend probe loop (slower, for AppLens visibility)
        if (frontendProbeEnabled) {
            logger.info("[ProbeService] Starting frontend probe loop at {}ms interval", FRONTEND_PROBE_INTERVAL_MS);
            scheduler.scheduleAtFixedRate(
                    this::sendFrontendProbe,
                    6000,  // Stagger start to avoid overlap
                    FRONTEND_PROBE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Sends a local probe request and broadcasts the result to the dashboard.
     * This is the high-frequency probe for the latency monitor chart.
     */
    private void sendLocalProbe() {
        long startTime = System.currentTimeMillis();
        long timestamp = startTime;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(localProbeUrl))
                .header("X-Probe-Request", "true")
                .header("X-Probe-Type", "local")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long latencyMs = System.currentTimeMillis() - startTime;
            lastLocalLatencyMs = latencyMs;
            localProbeCount.incrementAndGet();

            // Broadcast to dashboard latency monitor
            broadcastProbeResult(timestamp, latencyMs, true, null, "local");

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            lastLocalLatencyMs = latencyMs;
            localErrorCount.incrementAndGet();

            // Broadcast failure
            broadcastProbeResult(timestamp, latencyMs, false, e.getMessage(), "local");
        }
    }

    /**
     * Sends a frontend probe request through Azure's frontend/load balancer.
     * This is the slower probe for AppLens visibility.
     * Does NOT broadcast to dashboard to avoid noise.
     */
    private void sendFrontendProbe() {
        if (!frontendProbeEnabled || frontendProbeUrl == null) {
            return;
        }

        long startTime = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(frontendProbeUrl))
                .header("X-Probe-Request", "true")
                .header("X-Probe-Type", "frontend")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long latencyMs = System.currentTimeMillis() - startTime;
            lastFrontendLatencyMs = latencyMs;
            frontendProbeCount.incrementAndGet();

            // Log occasionally for visibility (every 60 probes = ~1 minute at 1s interval)
            if (frontendProbeCount.get() % 60 == 0) {
                logger.debug("[ProbeService] Frontend probe stats: count={}, lastLatency={}ms",
                        frontendProbeCount.get(), latencyMs);
            }

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            lastFrontendLatencyMs = latencyMs;
            frontendErrorCount.incrementAndGet();

            // Log errors (but don't spam)
            if (frontendErrorCount.get() % 10 == 1) {
                logger.warn("[ProbeService] Frontend probe error: {} (count={})",
                        e.getMessage(), frontendErrorCount.get());
            }
        }
    }

    /**
     * Broadcasts probe result to WebSocket clients.
     */
    private void broadcastProbeResult(long timestamp, long latencyMs, boolean success,
                                       String error, String probeType) {
        Map<String, Object> result = Map.of(
                "timestamp", timestamp,
                "latencyMs", latencyMs,
                "success", success,
                "error", error != null ? error : "",
                "probeType", probeType
        );
        messagingTemplate.convertAndSend("/topic/probe", result);
    }

    /**
     * Gets combined probe statistics for both local and frontend probes.
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "local", Map.of(
                        "probeUrl", localProbeUrl != null ? localProbeUrl : "",
                        "probeCount", localProbeCount.get(),
                        "errorCount", localErrorCount.get(),
                        "lastLatencyMs", lastLocalLatencyMs,
                        "intervalMs", config.getProbeIntervalMs()
                ),
                "frontend", Map.of(
                        "enabled", frontendProbeEnabled,
                        "probeUrl", frontendProbeUrl != null ? frontendProbeUrl : "",
                        "probeCount", frontendProbeCount.get(),
                        "errorCount", frontendErrorCount.get(),
                        "lastLatencyMs", lastFrontendLatencyMs,
                        "intervalMs", FRONTEND_PROBE_INTERVAL_MS
                )
        );
    }

    /**
     * Gets the last local probe latency (used by dashboard).
     */
    public long getLastLocalLatencyMs() {
        return lastLocalLatencyMs;
    }

    /**
     * Gets the last frontend probe latency (for diagnostics).
     */
    public long getLastFrontendLatencyMs() {
        return lastFrontendLatencyMs;
    }
}
