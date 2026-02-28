/* =============================================================================
   Performance Problem Simulator - Java Blessed Image
   Charts Module (Chart.js Integration) - Updated for new layout
   ============================================================================= */

const ChartsModule = (function() {
    'use strict';

    // Chart configuration
    const MAX_DATA_POINTS = 60;  // Upper charts: 60 points at 250ms = 15 seconds
    const MAX_LATENCY_DATA_POINTS = 150;  // Latency chart: 150 points at 100ms = 15 seconds (same visual pacing)

    // Chart colors matching PerfSimNode
    const colors = {
        cpu: '#0078d4',
        cpuLight: 'rgba(0, 120, 212, 0.2)',
        memory: '#1d6f1d',
        memoryLight: 'rgba(29, 111, 29, 0.2)',
        threads: '#8764b8',
        threadsLight: 'rgba(135, 100, 184, 0.2)',
        gc: '#ffb900',
        gcLight: 'rgba(255, 185, 0, 0.2)',
        latency: '#1d6f1d',
        latencyLight: 'rgba(29, 111, 29, 0.2)',
        text: '#323130',
        grid: '#e1dfdd'
    };

    // Chart instances
    let cpuMemoryChart = null;
    let threadsGcChart = null;
    let latencyChart = null;

    // Data buffers
    const dataBuffers = {
        cpu: [],
        memory: [],
        threads: [],
        gc: [],
        latency: []
    };

    // Labels buffer
    let labels = [];
    let timeLabels = [];
    let latencyTimeLabels = [];  // Separate labels for latency chart (100ms rate)

    /**
     * Formats time for x-axis labels
     */
    function formatTime(date) {
        return date.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }

    /**
     * Creates common chart options
     */
    function getCommonOptions() {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 0
            },
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    display: false
                }
            },
            scales: {
                x: {
                    display: true,
                    grid: {
                        color: colors.grid,
                        lineWidth: 0.5
                    },
                    ticks: {
                        color: colors.text,
                        font: { size: 9 },
                        maxTicksLimit: 6,
                        maxRotation: 0
                    }
                },
                y: {
                    beginAtZero: true,
                    position: 'left',
                    grid: {
                        color: colors.grid,
                        lineWidth: 0.5
                    },
                    ticks: {
                        color: colors.text,
                        font: { size: 10 }
                    }
                }
            }
        };
    }

    /**
     * Initializes all charts
     */
    function init() {
        // Initialize labels and data buffers
        const now = new Date();
        for (let i = MAX_DATA_POINTS - 1; i >= 0; i--) {
            const time = new Date(now.getTime() - i * 1000);
            timeLabels.push(formatTime(time));
            labels.push('');
            dataBuffers.cpu.push(0);
            dataBuffers.memory.push(0);
            dataBuffers.threads.push(0);
            dataBuffers.gc.push(0);
            dataBuffers.latency.push(0);
        }

        // CPU & Memory Combined Chart
        const cpuMemoryCtx = document.getElementById('cpuMemoryChart');
        if (cpuMemoryCtx) {
            cpuMemoryChart = new Chart(cpuMemoryCtx, {
                type: 'line',
                data: {
                    labels: timeLabels,
                    datasets: [
                        {
                            label: 'CPU %',
                            data: dataBuffers.cpu,
                            borderColor: colors.cpu,
                            backgroundColor: colors.cpuLight,
                            fill: true,
                            tension: 0.3,
                            borderWidth: 1,
                            pointRadius: 0,
                            yAxisID: 'y'
                        },
                        {
                            label: 'Memory MB',
                            data: dataBuffers.memory,
                            borderColor: colors.memory,
                            backgroundColor: colors.memoryLight,
                            fill: true,
                            tension: 0.3,
                            borderWidth: 1,
                            pointRadius: 0,
                            yAxisID: 'y1'
                        }
                    ]
                },
                options: {
                    ...getCommonOptions(),
                    scales: {
                        ...getCommonOptions().scales,
                        y: {
                            ...getCommonOptions().scales.y,
                            suggestedMax: 100,
                            title: {
                                display: false
                            }
                        },
                        y1: {
                            beginAtZero: true,
                            position: 'right',
                            grid: {
                                drawOnChartArea: false
                            },
                            ticks: {
                                color: colors.text,
                                font: { size: 10 }
                            }
                        }
                    }
                }
            });
        }

        // Threads & GC Combined Chart
        const threadsGcCtx = document.getElementById('threadsGcChart');
        if (threadsGcCtx) {
            threadsGcChart = new Chart(threadsGcCtx, {
                type: 'line',
                data: {
                    labels: timeLabels,
                    datasets: [
                        {
                            label: 'Threads',
                            data: dataBuffers.threads,
                            borderColor: colors.threads,
                            backgroundColor: colors.threadsLight,
                            fill: true,
                            tension: 0.3,
                            borderWidth: 1,
                            pointRadius: 0,
                            yAxisID: 'y'
                        },
                        {
                            label: 'GC Time (ms)',
                            data: dataBuffers.gc,
                            borderColor: colors.gc,
                            backgroundColor: colors.gcLight,
                            fill: true,
                            tension: 0.3,
                            borderWidth: 1,
                            pointRadius: 0,
                            yAxisID: 'y1'
                        }
                    ]
                },
                options: {
                    ...getCommonOptions(),
                    scales: {
                        ...getCommonOptions().scales,
                        y: {
                            ...getCommonOptions().scales.y,
                            title: {
                                display: false
                            }
                        },
                        y1: {
                            beginAtZero: true,
                            position: 'right',
                            grid: {
                                drawOnChartArea: false
                            },
                            ticks: {
                                color: colors.text,
                                font: { size: 10 }
                            }
                        }
                    }
                }
            });
        }

        // Latency Chart
        const latencyCtx = document.getElementById('latencyChart');
        if (latencyCtx) {
            latencyChart = new Chart(latencyCtx, {
                type: 'line',
                data: {
                    labels: latencyTimeLabels,
                    datasets: [{
                        label: 'Latency (ms)',
                        data: dataBuffers.latency,
                        borderColor: colors.latency,
                        backgroundColor: colors.latencyLight,
                        fill: true,
                        tension: 0.3,
                        borderWidth: 1,
                        pointRadius: 0
                    }]
                },
                options: {
                    ...getCommonOptions(),
                    scales: {
                        ...getCommonOptions().scales,
                        y: {
                            ...getCommonOptions().scales.y,
                            suggestedMax: 200
                        }
                    }
                }
            });
        }

        console.log('[Charts] Initialized all charts');
    }

    /**
     * Updates a data buffer with a new value
     */
    function updateBuffer(buffer, value) {
        buffer.push(value);
        if (buffer.length > MAX_DATA_POINTS) {
            buffer.shift();
        }
    }

    /**
     * Updates time labels
     */
    function updateTimeLabels() {
        const now = new Date();
        timeLabels.push(formatTime(now));
        if (timeLabels.length > MAX_DATA_POINTS) {
            timeLabels.shift();
        }
    }

    /**
     * Updates CPU value
     */
    function updateCpu(value) {
        updateBuffer(dataBuffers.cpu, value);
        if (cpuMemoryChart) {
            cpuMemoryChart.data.datasets[0].data = dataBuffers.cpu;
            cpuMemoryChart.update('none');
        }
    }

    /**
     * Updates Memory value
     */
    function updateMemory(value) {
        updateBuffer(dataBuffers.memory, value);
        if (cpuMemoryChart) {
            cpuMemoryChart.data.datasets[1].data = dataBuffers.memory;
            cpuMemoryChart.update('none');
        }
    }

    /**
     * Updates Thread count
     */
    function updateThreads(value) {
        updateBuffer(dataBuffers.threads, value);
        if (threadsGcChart) {
            threadsGcChart.data.datasets[0].data = dataBuffers.threads;
            threadsGcChart.update('none');
        }
    }

    /**
     * Updates GC time
     */
    function updateGc(value) {
        updateBuffer(dataBuffers.gc, value);
        if (threadsGcChart) {
            threadsGcChart.data.datasets[1].data = dataBuffers.gc;
            threadsGcChart.update('none');
        }
    }

    /**
     * Updates latency time labels (called at 100ms rate)
     */
    function updateLatencyTimeLabels() {
        const now = new Date();
        latencyTimeLabels.push(formatTime(now));
        if (latencyTimeLabels.length > MAX_LATENCY_DATA_POINTS) {
            latencyTimeLabels.shift();
        }
    }

    /**
     * Updates Latency value
     */
    function updateLatency(value) {
        updateLatencyTimeLabels();  // Update labels at same 100ms rate as data
        dataBuffers.latency.push(value);
        if (dataBuffers.latency.length > MAX_LATENCY_DATA_POINTS) {
            dataBuffers.latency.shift();
        }
        if (latencyChart) {
            latencyChart.data.labels = latencyTimeLabels;
            latencyChart.data.datasets[0].data = dataBuffers.latency;
            latencyChart.update('none');
        }
    }

    /**
     * Updates all charts with metrics data
     */
    function updateAll(metrics) {
        updateTimeLabels();
        
        if (metrics.cpu) {
            updateCpu(metrics.cpu.usagePercent || 0);
        }
        if (metrics.memory) {
            updateMemory(metrics.memory.heapUsedMb || 0);
        }
        if (metrics.thread) {
            updateThreads(metrics.thread.activeCount || 0);
        }
        if (metrics.gc) {
            updateGc(metrics.gc.totalTimeMs || 0);
        }
        
        // Update chart labels (upper charts only - latency has its own)
        if (cpuMemoryChart) {
            cpuMemoryChart.data.labels = timeLabels;
        }
        if (threadsGcChart) {
            threadsGcChart.data.labels = timeLabels;
        }
        // Note: latency chart labels are updated in updateLatency() at 100ms rate
    }

    /**
     * Destroys all charts
     */
    function destroy() {
        if (cpuMemoryChart) cpuMemoryChart.destroy();
        if (threadsGcChart) threadsGcChart.destroy();
        if (latencyChart) latencyChart.destroy();
    }

    // Public API
    return {
        init,
        updateCpu,
        updateMemory,
        updateThreads,
        updateGc,
        updateLatency,
        updateAll,
        destroy
    };
})();
