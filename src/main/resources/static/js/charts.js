/* =============================================================================
   Performance Problem Simulator - Java Blessed Image
   Charts Module (Chart.js Integration)
   ============================================================================= */

const ChartsModule = (function() {
    'use strict';

    // Chart configuration
    const MAX_DATA_POINTS = 60;
    const CHART_UPDATE_INTERVAL = 250; // ms for most charts
    const LATENCY_UPDATE_INTERVAL = 100; // ms for request latency

    // Chart colors
    const colors = {
        primary: '#00a86b',
        primaryLight: 'rgba(0, 168, 107, 0.3)',
        danger: '#dc3545',
        dangerLight: 'rgba(220, 53, 69, 0.3)',
        warning: '#ffc107',
        warningLight: 'rgba(255, 193, 7, 0.3)',
        info: '#17a2b8',
        infoLight: 'rgba(23, 162, 184, 0.3)',
        text: '#e4e4e4',
        grid: '#2d4a7c'
    };

    // Chart instances
    let cpuChart = null;
    let memoryChart = null;
    let threadChart = null;
    let latencyChart = null;

    // Data buffers
    const dataBuffers = {
        cpu: [],
        memory: [],
        threads: [],
        latency: []
    };

    // Labels buffer
    let labels = [];

    /**
     * Creates common chart options
     */
    function getCommonOptions(yAxisLabel, suggestedMax = 100) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 0
            },
            plugins: {
                legend: {
                    display: false
                }
            },
            scales: {
                x: {
                    display: false
                },
                y: {
                    beginAtZero: true,
                    suggestedMax: suggestedMax,
                    grid: {
                        color: colors.grid,
                        lineWidth: 0.5
                    },
                    ticks: {
                        color: colors.text,
                        font: {
                            size: 10
                        }
                    }
                }
            }
        };
    }

    /**
     * Initializes all charts
     */
    function init() {
        // Initialize labels
        for (let i = 0; i < MAX_DATA_POINTS; i++) {
            labels.push('');
            dataBuffers.cpu.push(0);
            dataBuffers.memory.push(0);
            dataBuffers.threads.push(0);
            dataBuffers.latency.push(0);
        }

        // CPU Chart
        const cpuCtx = document.getElementById('cpuChart');
        if (cpuCtx) {
            cpuChart = new Chart(cpuCtx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        data: dataBuffers.cpu,
                        borderColor: colors.primary,
                        backgroundColor: colors.primaryLight,
                        fill: true,
                        tension: 0.3,
                        borderWidth: 2,
                        pointRadius: 0
                    }]
                },
                options: getCommonOptions('CPU %', 100)
            });
        }

        // Memory Chart
        const memoryCtx = document.getElementById('memoryChart');
        if (memoryCtx) {
            memoryChart = new Chart(memoryCtx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        data: dataBuffers.memory,
                        borderColor: colors.warning,
                        backgroundColor: colors.warningLight,
                        fill: true,
                        tension: 0.3,
                        borderWidth: 2,
                        pointRadius: 0
                    }]
                },
                options: getCommonOptions('Memory MB', 1000)
            });
        }

        // Thread Chart
        const threadCtx = document.getElementById('threadChart');
        if (threadCtx) {
            threadChart = new Chart(threadCtx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        data: dataBuffers.threads,
                        borderColor: colors.info,
                        backgroundColor: colors.infoLight,
                        fill: true,
                        tension: 0.3,
                        borderWidth: 2,
                        pointRadius: 0
                    }]
                },
                options: getCommonOptions('Threads', 500)
            });
        }

        // Latency Chart
        const latencyCtx = document.getElementById('latencyChart');
        if (latencyCtx) {
            latencyChart = new Chart(latencyCtx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        data: dataBuffers.latency,
                        borderColor: colors.danger,
                        backgroundColor: colors.dangerLight,
                        fill: true,
                        tension: 0.3,
                        borderWidth: 2,
                        pointRadius: 0
                    }]
                },
                options: getCommonOptions('Latency ms', 200)
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
     * Updates CPU chart with new value
     */
    function updateCpu(value) {
        updateBuffer(dataBuffers.cpu, value);
        if (cpuChart) {
            cpuChart.data.datasets[0].data = dataBuffers.cpu;
            cpuChart.update('none');
        }
    }

    /**
     * Updates Memory chart with new value
     */
    function updateMemory(value) {
        updateBuffer(dataBuffers.memory, value);
        if (memoryChart) {
            memoryChart.data.datasets[0].data = dataBuffers.memory;
            memoryChart.update('none');
        }
    }

    /**
     * Updates Thread chart with new value
     */
    function updateThreads(value) {
        updateBuffer(dataBuffers.threads, value);
        if (threadChart) {
            threadChart.data.datasets[0].data = dataBuffers.threads;
            threadChart.update('none');
        }
    }

    /**
     * Updates Latency chart with new value
     */
    function updateLatency(value) {
        updateBuffer(dataBuffers.latency, value);
        if (latencyChart) {
            latencyChart.data.datasets[0].data = dataBuffers.latency;
            latencyChart.update('none');
        }
    }

    /**
     * Updates all charts with metrics data
     */
    function updateAll(metrics) {
        if (metrics.cpu) {
            updateCpu(metrics.cpu.usagePercent || 0);
        }
        if (metrics.memory) {
            updateMemory(metrics.memory.heapUsedMb || 0);
        }
        if (metrics.thread) {
            updateThreads(metrics.thread.activeCount || 0);
        }
    }

    /**
     * Destroys all charts
     */
    function destroy() {
        if (cpuChart) cpuChart.destroy();
        if (memoryChart) memoryChart.destroy();
        if (threadChart) threadChart.destroy();
        if (latencyChart) latencyChart.destroy();
    }

    // Public API
    return {
        init,
        updateCpu,
        updateMemory,
        updateThreads,
        updateLatency,
        updateAll,
        destroy
    };
})();
