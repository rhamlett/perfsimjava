package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
 * HOW IT WORKS:
 *   1. A dedicated thread sends HTTP GET requests to /api/metrics/probe
 *   2. Measures round-trip time for each request
 *   3. Broadcasts results to dashboard via WebSocket
 *   4. Dashboard displays latency chart and statistics
 *
 * WHY INTERNAL PROBING:
 *   Unlike the Node.js version which uses a separate sidecar process,
 *   Java's thread-per-request model allows us to probe from a dedicated
 *   thread within the same JVM. The probe thread has its own HTTP client
 *   and doesn't share resources with servlet threads.
 *
 *   For Azure App Service, you can also configure external health probes
 *   that go through the Azure frontend for AppLens visibility.
 *
 * AZURE INTEGRATION:
 *   When WEBSITE_HOSTNAME is set (Azure App Service), probes can be
 *   configured to go through the Azure frontend load balancer, making
 *   the traffic visible in AppLens diagnostics.
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
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private String probeUrl;
    private final AtomicLong probeCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private volatile long lastLatencyMs = 0;

    public ProbeService(SimpMessagingTemplate messagingTemplate, AppConfig config) {
        this.messagingTemplate = messagingTemplate;
        this.config = config;
        
        // Create dedicated HTTP client for probing
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newFixedThreadPool(2))
                .build();
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "probe-service");
            t.setDaemon(true);
            return t;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // Determine probe URL
        String hostname = System.getenv("WEBSITE_HOSTNAME");
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));

        if (hostname != null && !hostname.isEmpty()) {
            // Azure App Service - probe through frontend
            probeUrl = "https://" + hostname + "/api/metrics/probe";
            logger.info("[ProbeService] Azure mode: probing through {}", probeUrl);
        } else {
            // Local development - probe localhost
            probeUrl = "http://localhost:" + port + "/api/metrics/probe";
            logger.info("[ProbeService] Local mode: probing {}", probeUrl);
        }

        // Start the probe loop
        int intervalMs = config.getProbeIntervalMs();
        logger.info("[ProbeService] Starting probe loop at {}ms interval", intervalMs);
        
        scheduler.scheduleAtFixedRate(
                this::sendProbe,
                5000,  // Initial delay - wait for app to fully stabilize
                intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Sends a probe request and broadcasts the result.
     */
    private void sendProbe() {
        long startTime = System.currentTimeMillis();
        long timestamp = startTime;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(probeUrl))
                .header("X-Probe-Request", "true")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long latencyMs = System.currentTimeMillis() - startTime;
            lastLatencyMs = latencyMs;
            probeCount.incrementAndGet();

            // Broadcast to dashboard
            broadcastProbeResult(timestamp, latencyMs, true, null);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            lastLatencyMs = latencyMs;
            errorCount.incrementAndGet();

            // Broadcast failure
            broadcastProbeResult(timestamp, latencyMs, false, e.getMessage());
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

        try {
            messagingTemplate.convertAndSend("/topic/probe", result);
        } catch (Exception e) {
            logger.debug("Failed to broadcast probe result: {}", e.getMessage());
        }
    }

    /**
     * Gets the last recorded latency.
     */
    public long getLastLatencyMs() {
        return lastLatencyMs;
    }

    /**
     * Gets probe statistics.
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "probeCount", probeCount.get(),
                "errorCount", errorCount.get(),
                "lastLatencyMs", lastLatencyMs,
                "probeUrl", probeUrl,
                "intervalMs", config.getProbeIntervalMs()
        );
    }
}
