package com.microsoft.azure.samples.perfsimjava.model;

import java.time.Instant;

/**
 * =============================================================================
 * SYSTEM METRICS SNAPSHOT
 * =============================================================================
 *
 * Contains a point-in-time snapshot of system metrics including CPU, memory,
 * thread pool, and JVM statistics. Broadcast to dashboard clients every
 * metricsIntervalMs (default 250ms).
 *
 * PORTING NOTES:
 *   - Node.js: Uses os.cpus(), process.memoryUsage(), perf_hooks for event loop lag
 *   - Python: Uses psutil for CPU/memory, asyncio for event loop metrics
 *   - C#: Uses System.Diagnostics.Process and PerformanceCounter
 *   - PHP: Uses sys_getloadavg(), memory_get_usage()
 */
public class SystemMetrics {

    private Instant timestamp;
    private CpuMetrics cpu;
    private MemoryMetrics memory;
    private ThreadMetrics thread;
    private ProcessMetrics process;

    public SystemMetrics() {
        this.timestamp = Instant.now();
    }

    // Getters and Setters

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public CpuMetrics getCpu() {
        return cpu;
    }

    public void setCpu(CpuMetrics cpu) {
        this.cpu = cpu;
    }

    public MemoryMetrics getMemory() {
        return memory;
    }

    public void setMemory(MemoryMetrics memory) {
        this.memory = memory;
    }

    public ThreadMetrics getThread() {
        return thread;
    }

    public void setThread(ThreadMetrics thread) {
        this.thread = thread;
    }

    public ProcessMetrics getProcess() {
        return process;
    }

    public void setProcess(ProcessMetrics process) {
        this.process = process;
    }

    /**
     * CPU metrics including usage percentage.
     */
    public static class CpuMetrics {
        private double usagePercent;
        private int availableProcessors;

        public double getUsagePercent() {
            return usagePercent;
        }

        public void setUsagePercent(double usagePercent) {
            this.usagePercent = usagePercent;
        }

        public int getAvailableProcessors() {
            return availableProcessors;
        }

        public void setAvailableProcessors(int availableProcessors) {
            this.availableProcessors = availableProcessors;
        }
    }

    /**
     * Memory metrics for JVM heap and system memory.
     */
    public static class MemoryMetrics {
        private double heapUsedMb;
        private double heapMaxMb;
        private double nonHeapUsedMb;
        private double totalSystemMb;
        private double freeSystemMb;

        public double getHeapUsedMb() {
            return heapUsedMb;
        }

        public void setHeapUsedMb(double heapUsedMb) {
            this.heapUsedMb = heapUsedMb;
        }

        public double getHeapMaxMb() {
            return heapMaxMb;
        }

        public void setHeapMaxMb(double heapMaxMb) {
            this.heapMaxMb = heapMaxMb;
        }

        public double getNonHeapUsedMb() {
            return nonHeapUsedMb;
        }

        public void setNonHeapUsedMb(double nonHeapUsedMb) {
            this.nonHeapUsedMb = nonHeapUsedMb;
        }

        public double getTotalSystemMb() {
            return totalSystemMb;
        }

        public void setTotalSystemMb(double totalSystemMb) {
            this.totalSystemMb = totalSystemMb;
        }

        public double getFreeSystemMb() {
            return freeSystemMb;
        }

        public void setFreeSystemMb(double freeSystemMb) {
            this.freeSystemMb = freeSystemMb;
        }
    }

    /**
     * Thread pool metrics - shows active threads and pool utilization.
     * This is the Java equivalent of Node.js event loop lag monitoring.
     */
    public static class ThreadMetrics {
        private int activeCount;
        private int poolSize;
        private int peakThreadCount;
        private long totalStartedThreadCount;
        private long queuedTaskCount;

        public int getActiveCount() {
            return activeCount;
        }

        public void setActiveCount(int activeCount) {
            this.activeCount = activeCount;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public int getPeakThreadCount() {
            return peakThreadCount;
        }

        public void setPeakThreadCount(int peakThreadCount) {
            this.peakThreadCount = peakThreadCount;
        }

        public long getTotalStartedThreadCount() {
            return totalStartedThreadCount;
        }

        public void setTotalStartedThreadCount(long totalStartedThreadCount) {
            this.totalStartedThreadCount = totalStartedThreadCount;
        }

        public long getQueuedTaskCount() {
            return queuedTaskCount;
        }

        public void setQueuedTaskCount(long queuedTaskCount) {
            this.queuedTaskCount = queuedTaskCount;
        }
    }

    /**
     * Process-level metrics.
     */
    public static class ProcessMetrics {
        private long pid;
        private long jvmStartTime;
        private long uptimeSeconds;
        private int gcCount;
        private long gcTimeMs;
        private double gcOverheadPercent;

        public long getPid() {
            return pid;
        }

        public void setPid(long pid) {
            this.pid = pid;
        }

        public long getJvmStartTime() {
            return jvmStartTime;
        }

        public void setJvmStartTime(long jvmStartTime) {
            this.jvmStartTime = jvmStartTime;
        }

        public long getUptimeSeconds() {
            return uptimeSeconds;
        }

        public void setUptimeSeconds(long uptimeSeconds) {
            this.uptimeSeconds = uptimeSeconds;
        }

        public int getGcCount() {
            return gcCount;
        }

        public void setGcCount(int gcCount) {
            this.gcCount = gcCount;
        }

        public long getGcTimeMs() {
            return gcTimeMs;
        }

        public void setGcTimeMs(long gcTimeMs) {
            this.gcTimeMs = gcTimeMs;
        }

        public double getGcOverheadPercent() {
            return gcOverheadPercent;
        }

        public void setGcOverheadPercent(double gcOverheadPercent) {
            this.gcOverheadPercent = gcOverheadPercent;
        }
    }
}
