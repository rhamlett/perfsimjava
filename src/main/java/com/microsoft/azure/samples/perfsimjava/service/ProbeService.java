package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
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
 * PROBE TARGET:
 *   - In Azure (WEBSITE_HOSTNAME set): Routes through Azure frontend for
 *     AppLens visibility and accurate end-to-end latency measurement
 *   - Local development: Falls back to localhost for convenience
 *
 * CONFIGURATION:
 *   - HEALTH_PROBE_RATE: Probe interval in milliseconds (default: 200ms, min: 100ms)
 *   - Probes pause during idle state to reduce unnecessary traffic
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

    private final SimpMessagingTemplate messagingTemplate;
    private final AppConfig config;
    private final ApplicationContext applicationContext;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    
    // Lazy-loaded to avoid circular dependency
    private IdleService idleService;

    // Probe state
    private String probeUrl;
    private final AtomicLong probeCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private volatile long lastLatencyMs = 0;

    public ProbeService(SimpMessagingTemplate messagingTemplate, AppConfig config, 
                         ApplicationContext applicationContext) {
        this.messagingTemplate = messagingTemplate;
        this.config = config;
        this.applicationContext = applicationContext;

        // Create dedicated HTTP client for probing
        // Uses a small thread pool to avoid blocking
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newFixedThreadPool(2))
                .build();

        // Single-threaded scheduler for probe tasks
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "probe-service");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Gets the IdleService lazily to avoid circular dependency.
     */
    private IdleService getIdleService() {
        if (idleService == null) {
            idleService = applicationContext.getBean(IdleService.class);
        }
        return idleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));

        // Determine probe URL - use frontend when in Azure, localhost otherwise
        String hostname = System.getenv("WEBSITE_HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            probeUrl = "https://" + hostname + "/api/metrics/probe";
            logger.info("[ProbeService] Probe URL (frontend): {}", probeUrl);
        } else {
            probeUrl = "http://localhost:" + port + "/api/metrics/probe";
            logger.info("[ProbeService] Probe URL (localhost): {}", probeUrl);
        }

        // Start probe loop
        int intervalMs = config.getProbeIntervalMs();
        logger.info("[ProbeService] Starting probe loop at {}ms interval", intervalMs);
        scheduler.scheduleAtFixedRate(
                this::sendProbe,
                5000,  // Initial delay - wait for app to stabilize
                intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Sends a probe request and broadcasts the result to the dashboard.
     * Routes through Azure frontend when in Azure for AppLens visibility.
     * Skipped when application is idle to reduce unnecessary traffic.
     */
    private void sendProbe() {
        // Check if application is idle - skip probes if so
        if (getIdleService().isIdle()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        long timestamp = startTime;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(probeUrl))
                .header("X-Probe-Request", "true")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long latencyMs = System.currentTimeMillis() - startTime;
            lastLatencyMs = latencyMs;
            probeCount.incrementAndGet();

            // Broadcast to dashboard latency monitor
            broadcastProbeResult(timestamp, latencyMs, true, null);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            lastLatencyMs = latencyMs;
            errorCount.incrementAndGet();

            // Broadcast failure
            broadcastProbeResult(timestamp, latencyMs, false, e.getMessage());
            
            // Log errors occasionally (but don't spam)
            if (errorCount.get() % 10 == 1) {
                logger.warn("[ProbeService] Probe error: {} (count={})",
                        e.getMessage(), errorCount.get());
            }
        }
    }

    /**
     * Broadcasts probe result to WebSocket clients.
     */
    private void broadcastProbeResult(long timestamp, long latencyMs, boolean success, String error) {
        Map<String, Object> result = Map.of(
                "timestamp", timestamp,
                "latencyMs", latencyMs,
                "success", success,
                "error", error != null ? error : ""
        );
        messagingTemplate.convertAndSend("/topic/probe", result);
    }

    /**
     * Gets the last probe latency (used by dashboard).
     */
    public long getLastLatencyMs() {
        return lastLatencyMs;
    }
}
