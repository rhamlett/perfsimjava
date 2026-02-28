package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import com.microsoft.azure.samples.perfsimjava.model.EventLogEntry;
import com.microsoft.azure.samples.perfsimjava.model.SystemMetrics;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;

/**
 * =============================================================================
 * METRICS SERVICE — Real-Time System Metrics Collection
 * =============================================================================
 *
 * PURPOSE:
 *   Collects system metrics (CPU, memory, threads, GC) and broadcasts them
 *   to WebSocket clients for the dashboard. Uses JMX MBeans for JVM-level
 *   metrics and OS-level statistics.
 *
 * METRICS COLLECTED:
 *   1. CPU Usage — System-wide CPU load using OperatingSystemMXBean
 *   2. Memory — JVM heap (used/max), non-heap, and system memory
 *   3. Threads — Active count, pool size, peak count
 *   4. GC — Collection count and cumulative time
 *   5. Process — PID, uptime
 *
 * BROADCAST INTERVAL:
 *   Metrics are collected and broadcast at the configured interval
 *   (default 250ms). The dashboard updates in real-time.
 *
 * PORTING NOTES:
 *   - Node.js: os.cpus(), process.memoryUsage(), perf_hooks
 *   - Python: psutil for CPU/memory, Platform.java() equivalent
 *   - C#: System.Diagnostics.Process, PerformanceCounter
 *   - PHP: sys_getloadavg(), memory_get_usage()
 */
@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    private static final double BYTES_TO_MB = 1024.0 * 1024.0;

    private final SimpMessagingTemplate messagingTemplate;
    private final AppConfig config;
    private final EventLogService eventLogService;

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final long processId;
    private final Instant startTime;

    public MetricsService(SimpMessagingTemplate messagingTemplate, AppConfig config, EventLogService eventLogService) {
        this.messagingTemplate = messagingTemplate;
        this.config = config;
        this.eventLogService = eventLogService;

        // Initialize MXBeans
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.processId = ProcessHandle.current().pid();
        this.startTime = Instant.now();
    }

    @PostConstruct
    public void init() {
        long totalMemMb = (long) (osBean.getTotalMemorySize() / BYTES_TO_MB);
        int cpuCount = osBean.getAvailableProcessors();
        logger.info("[Metrics] Host memory: {} MB, CPU cores: {}", totalMemMb, cpuCount);
        
        // Log server startup event with PID
        eventLogService.info(
                EventLogEntry.EventType.SERVER_STARTED,
                String.format("Server started (PID: %d)", processId)
        );
    }
    
    /**
     * Gets the current process ID.
     */
    public long getProcessId() {
        return processId;
    }

    /**
     * Scheduled task to collect and broadcast metrics.
     * Runs at the configured interval (default 250ms).
     */
    @Scheduled(fixedRateString = "${perfsim.metrics-interval-ms:250}")
    public void broadcastMetrics() {
        SystemMetrics metrics = collectMetrics();
        messagingTemplate.convertAndSend("/topic/metrics", metrics);
    }

    /**
     * Collects a snapshot of current system metrics.
     * Called by the scheduled broadcaster and by REST endpoints.
     */
    public SystemMetrics collectMetrics() {
        SystemMetrics metrics = new SystemMetrics();

        // CPU metrics
        SystemMetrics.CpuMetrics cpu = new SystemMetrics.CpuMetrics();
        double cpuLoad = osBean.getCpuLoad();
        // getCpuLoad returns -1 if not available, convert to percentage
        cpu.setUsagePercent(cpuLoad >= 0 ? Math.round(cpuLoad * 10000.0) / 100.0 : 0);
        cpu.setAvailableProcessors(osBean.getAvailableProcessors());
        metrics.setCpu(cpu);

        // Memory metrics
        SystemMetrics.MemoryMetrics memory = new SystemMetrics.MemoryMetrics();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        memory.setHeapUsedMb(Math.round(heapUsage.getUsed() / BYTES_TO_MB * 100.0) / 100.0);
        memory.setHeapMaxMb(heapUsage.getMax() > 0 
                ? Math.round(heapUsage.getMax() / BYTES_TO_MB * 100.0) / 100.0 
                : Math.round(heapUsage.getCommitted() / BYTES_TO_MB * 100.0) / 100.0);
        memory.setNonHeapUsedMb(Math.round(nonHeapUsage.getUsed() / BYTES_TO_MB * 100.0) / 100.0);
        memory.setTotalSystemMb(Math.round(osBean.getTotalMemorySize() / BYTES_TO_MB * 100.0) / 100.0);
        memory.setFreeSystemMb(Math.round(osBean.getFreeMemorySize() / BYTES_TO_MB * 100.0) / 100.0);
        metrics.setMemory(memory);

        // Thread metrics
        SystemMetrics.ThreadMetrics thread = new SystemMetrics.ThreadMetrics();
        thread.setActiveCount(threadBean.getThreadCount());
        thread.setPeakThreadCount(threadBean.getPeakThreadCount());
        thread.setTotalStartedThreadCount(threadBean.getTotalStartedThreadCount());
        // Pool size would need access to the executor, using thread count as approximation
        thread.setPoolSize(threadBean.getThreadCount());
        metrics.setThread(thread);

        // Process metrics
        SystemMetrics.ProcessMetrics process = new SystemMetrics.ProcessMetrics();
        process.setPid(processId);
        process.setUptimeSeconds(Duration.between(startTime, Instant.now()).getSeconds());

        // GC metrics
        int gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gcBean.getCollectionCount();
            gcTimeMs += gcBean.getCollectionTime();
        }
        process.setGcCount(gcCount);
        process.setGcTimeMs(gcTimeMs);
        metrics.setProcess(process);

        return metrics;
    }

    /**
     * Gets a lightweight probe response for latency monitoring.
     */
    public ProbeResponse getProbeResponse() {
        return new ProbeResponse(
                System.currentTimeMillis(),
                "ok",
                processId
        );
    }

    /**
     * Simple probe response for latency monitoring.
     */
    public record ProbeResponse(long timestamp, String status, long pid) {}
}
