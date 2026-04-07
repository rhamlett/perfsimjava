/* =============================================================================
   Performance Problem Simulator - Java
   Charts Module (Chart.js Integration) - Updated for new layout
   ============================================================================= */

const ChartsModule = (function() {
    'use strict';

    // Chart configuration
    const MAX_DATA_POINTS = 240;  // Upper charts: 240 points at 250ms = 60 seconds
    const MAX_LATENCY_DATA_POINTS = 600;  // Latency chart: 600 points at 100ms = 60 seconds

    // Latency threshold values (in milliseconds)
    const LATENCY_THRESHOLDS = {
        GOOD: 150,        // < 150ms = Good
        DEGRADED: 1000,   // 150ms - 1s = Degraded
        SEVERE: 30000     // 1s - 30s = Severe, > 30s = Critical
    };

    // Latency threshold colors (matching CSS variables)
    const LATENCY_COLORS = {
        good: '#1d6f1d',           // --color-success
        goodLight: 'rgba(29, 111, 29, 0.2)',
        degraded: '#ffb900',       // --color-warning
        degradedLight: 'rgba(255, 185, 0, 0.2)',
        severe: '#d13438',         // --color-danger
        severeLight: 'rgba(209, 52, 56, 0.2)',
        critical: '#8b0000',       // dark red
        criticalLight: 'rgba(139, 0, 0, 0.2)'
    };

    // RGB values for smooth color interpolation
    const LATENCY_RGB = {
        good:     { r: 29,  g: 111, b: 29  }, // Green
        degraded: { r: 255, g: 185, b: 0   }, // Yellow
        severe:   { r: 209, g: 52,  b: 56  }, // Red
        critical: { r: 139, g: 0,   b: 0   }  // Dark Red
    };

    /**
     * Interpolates between two RGB colors.
     * @param {Object} color1 - Start color {r, g, b}
     * @param {Object} color2 - End color {r, g, b}
     * @param {number} t - Interpolation factor (0-1)
     * @returns {string} - RGB color string
     */
    function lerpColor(color1, color2, t) {
        t = Math.max(0, Math.min(1, t)); // Clamp to 0-1
        const r = Math.round(color1.r + (color2.r - color1.r) * t);
        const g = Math.round(color1.g + (color2.g - color1.g) * t);
        const b = Math.round(color1.b + (color2.b - color1.b) * t);
        return `rgb(${r}, ${g}, ${b})`;
    }

    /**
     * Gets a smoothly interpolated color for a latency value.
     * Blends between threshold colors based on where the value falls.
     * @param {number} latencyMs - Latency value in milliseconds
     * @returns {string} - RGB color string
     */
    function getInterpolatedLatencyColor(latencyMs) {
        if (latencyMs <= 0) return lerpColor(LATENCY_RGB.good, LATENCY_RGB.good, 0);
        
        // 0-150ms: green → yellow
        if (latencyMs <= LATENCY_THRESHOLDS.GOOD) {
            const t = latencyMs / LATENCY_THRESHOLDS.GOOD;
            return lerpColor(LATENCY_RGB.good, LATENCY_RGB.degraded, t);
        }
        
        // 150-1000ms: yellow → red
        if (latencyMs <= LATENCY_THRESHOLDS.DEGRADED) {
            const t = (latencyMs - LATENCY_THRESHOLDS.GOOD) / (LATENCY_THRESHOLDS.DEGRADED - LATENCY_THRESHOLDS.GOOD);
            return lerpColor(LATENCY_RGB.degraded, LATENCY_RGB.severe, t);
        }
        
        // 1000-30000ms: red → dark red
        if (latencyMs <= LATENCY_THRESHOLDS.SEVERE) {
            const t = (latencyMs - LATENCY_THRESHOLDS.DEGRADED) / (LATENCY_THRESHOLDS.SEVERE - LATENCY_THRESHOLDS.DEGRADED);
            return lerpColor(LATENCY_RGB.severe, LATENCY_RGB.critical, t);
        }
        
        // >30000ms: solid dark red
        return lerpColor(LATENCY_RGB.critical, LATENCY_RGB.critical, 1);
    }

    /**
     * Gets a smoothly interpolated RGBA color for a latency value (for gradient fills).
     * @param {number} latencyMs - Latency value in milliseconds
     * @param {number} alpha - Alpha value (0-1)
     * @returns {string} - RGBA color string
     */
    function getInterpolatedLatencyColorRGBA(latencyMs, alpha) {
        let r, g, b;
        
        if (latencyMs <= 0) {
            r = LATENCY_RGB.good.r; g = LATENCY_RGB.good.g; b = LATENCY_RGB.good.b;
        } else if (latencyMs <= LATENCY_THRESHOLDS.GOOD) {
            const t = latencyMs / LATENCY_THRESHOLDS.GOOD;
            r = Math.round(LATENCY_RGB.good.r + (LATENCY_RGB.degraded.r - LATENCY_RGB.good.r) * t);
            g = Math.round(LATENCY_RGB.good.g + (LATENCY_RGB.degraded.g - LATENCY_RGB.good.g) * t);
            b = Math.round(LATENCY_RGB.good.b + (LATENCY_RGB.degraded.b - LATENCY_RGB.good.b) * t);
        } else if (latencyMs <= LATENCY_THRESHOLDS.DEGRADED) {
            const t = (latencyMs - LATENCY_THRESHOLDS.GOOD) / (LATENCY_THRESHOLDS.DEGRADED - LATENCY_THRESHOLDS.GOOD);
            r = Math.round(LATENCY_RGB.degraded.r + (LATENCY_RGB.severe.r - LATENCY_RGB.degraded.r) * t);
            g = Math.round(LATENCY_RGB.degraded.g + (LATENCY_RGB.severe.g - LATENCY_RGB.degraded.g) * t);
            b = Math.round(LATENCY_RGB.degraded.b + (LATENCY_RGB.severe.b - LATENCY_RGB.degraded.b) * t);
        } else if (latencyMs <= LATENCY_THRESHOLDS.SEVERE) {
            const t = (latencyMs - LATENCY_THRESHOLDS.DEGRADED) / (LATENCY_THRESHOLDS.SEVERE - LATENCY_THRESHOLDS.DEGRADED);
            r = Math.round(LATENCY_RGB.severe.r + (LATENCY_RGB.critical.r - LATENCY_RGB.severe.r) * t);
            g = Math.round(LATENCY_RGB.severe.g + (LATENCY_RGB.critical.g - LATENCY_RGB.severe.g) * t);
            b = Math.round(LATENCY_RGB.severe.b + (LATENCY_RGB.critical.b - LATENCY_RGB.severe.b) * t);
        } else {
            r = LATENCY_RGB.critical.r; g = LATENCY_RGB.critical.g; b = LATENCY_RGB.critical.b;
        }
        
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }

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

    /**
     * Returns the appropriate color for a latency value
     */
    function getLatencyColor(value, light = false) {
        if (value >= LATENCY_THRESHOLDS.SEVERE) {
            return light ? LATENCY_COLORS.criticalLight : LATENCY_COLORS.critical;
        } else if (value >= LATENCY_THRESHOLDS.DEGRADED) {
            return light ? LATENCY_COLORS.severeLight : LATENCY_COLORS.severe;
        } else if (value >= LATENCY_THRESHOLDS.GOOD) {
            return light ? LATENCY_COLORS.degradedLight : LATENCY_COLORS.degraded;
        }
        return light ? LATENCY_COLORS.goodLight : LATENCY_COLORS.good;
    }

    /**
     * Creates a vertical gradient for the latency chart with smooth color blending.
     * Adds many intermediate color stops for seamless transitions between thresholds.
     * @param {Chart} chart - The Chart.js instance
     */
    function createLatencyGradient(chart) {
        const ctx = chart.ctx;
        const chartArea = chart.chartArea;
        const yScale = chart.scales.y;
        
        const fallback = colors.latencyLight;
        if (!chartArea || !yScale) return fallback;
        
        const gradient = ctx.createLinearGradient(0, chartArea.bottom, 0, chartArea.top);
        const yMax = yScale.max || 200;
        
        // Add many color stops for smooth blending (20 stops from bottom to top)
        const numStops = 20;
        for (let i = 0; i <= numStops; i++) {
            const position = i / numStops; // 0 = bottom, 1 = top
            const latencyAtPosition = position * yMax;
            
            // Alpha increases slightly with latency for better visual distinction
            const alpha = 0.25 + (position * 0.25); // 0.25 at bottom to 0.50 at top
            
            const color = getInterpolatedLatencyColorRGBA(latencyAtPosition, alpha);
            gradient.addColorStop(position, color);
        }
        
        return gradient;
    }

    // Chart instances
    let cpuMemoryChart = null;
    let threadsGcChart = null;
    let latencyChart = null;

    // Display update intervals (fixed rate, independent of data arrival)
    let upperChartsInterval = null;
    let latencyChartInterval = null;

    // Latest values (updated by WebSocket, rendered by intervals)
    let latestCpu = 0;
    let latestMemory = 0;
    let latestThreads = 0;
    let latestGc = 0;
    let latestLatency = 0;

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
    let latencyTimeLabels = [];

    /**
     * Formats time for x-axis labels (UTC)
     */
    function formatTime(date) {
        return date.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'UTC' });
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
        // Initialize labels and data buffers for upper charts (250ms intervals, 60 seconds)
        const now = new Date();
        for (let i = MAX_DATA_POINTS - 1; i >= 0; i--) {
            const time = new Date(now.getTime() - i * 250);
            timeLabels.push(formatTime(time));
            labels.push('');
            dataBuffers.cpu.push(0);
            dataBuffers.memory.push(0);
            dataBuffers.threads.push(0);
            dataBuffers.gc.push(0);
        }

        // Initialize latency chart separately (100ms intervals, 60 seconds)
        for (let i = MAX_LATENCY_DATA_POINTS - 1; i >= 0; i--) {
            const time = new Date(now.getTime() - i * 100);
            latencyTimeLabels.push(formatTime(time));
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
                            label: 'GC Overhead (%)',
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

        // Latency Chart with threshold-based gradient coloring
        const latencyCtx = document.getElementById('latencyChart');
        if (latencyCtx) {
            // Plugin to update gradient fill on each render
            const gradientPlugin = {
                id: 'latencyGradient',
                beforeDraw: (chart) => {
                    const fillGradient = createLatencyGradient(chart);
                    chart.data.datasets[0].backgroundColor = fillGradient;
                }
            };

            latencyChart = new Chart(latencyCtx, {
                type: 'line',
                plugins: [gradientPlugin],
                data: {
                    labels: latencyTimeLabels,
                    datasets: [{
                        label: 'Latency (ms)',
                        data: dataBuffers.latency,
                        // Segment-based border color - smooth gradient based on data value
                        segment: {
                            borderColor: (ctx) => {
                                const p0 = ctx.p0.parsed?.y;
                                const p1 = ctx.p1.parsed?.y;
                                if (p0 == null || p1 == null) return 'rgba(0,0,0,0)';
                                const value = Math.max(p0, p1);
                                return getInterpolatedLatencyColor(value);
                            },
                        },
                        borderColor: colors.latency,          // Default/fallback
                        backgroundColor: colors.latencyLight, // Default/fallback, replaced by gradient plugin
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

        // Start fixed-rate display updates (independent of data arrival)
        // Upper charts: 250ms display rate
        upperChartsInterval = setInterval(renderUpperCharts, 250);
        // Latency chart: 100ms display rate
        latencyChartInterval = setInterval(renderLatencyChart, 100);
    }

    /**
     * Renders upper charts at fixed 250ms rate
     */
    function renderUpperCharts() {
        updateTimeLabels();
        updateBuffer(dataBuffers.cpu, latestCpu);
        updateBuffer(dataBuffers.memory, latestMemory);
        updateBuffer(dataBuffers.threads, latestThreads);
        updateBuffer(dataBuffers.gc, latestGc);

        if (cpuMemoryChart) {
            cpuMemoryChart.data.labels = timeLabels;
            cpuMemoryChart.data.datasets[0].data = dataBuffers.cpu;
            cpuMemoryChart.data.datasets[1].data = dataBuffers.memory;
            cpuMemoryChart.update('none');
        }
        if (threadsGcChart) {
            threadsGcChart.data.labels = timeLabels;
            threadsGcChart.data.datasets[0].data = dataBuffers.threads;
            threadsGcChart.data.datasets[1].data = dataBuffers.gc;
            threadsGcChart.update('none');
        }
    }

    /**
     * Renders latency chart at fixed 100ms rate
     */
    function renderLatencyChart() {
        const now = new Date();
        latencyTimeLabels.push(formatTime(now));
        if (latencyTimeLabels.length > MAX_LATENCY_DATA_POINTS) {
            latencyTimeLabels.shift();
        }
        dataBuffers.latency.push(latestLatency);
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
     * Updates CPU value (stores latest, rendered by interval)
     */
    function updateCpu(value) {
        latestCpu = value;
    }

    /**
     * Updates Memory value (stores latest, rendered by interval)
     */
    function updateMemory(value) {
        latestMemory = value;
    }

    /**
     * Updates Thread count (stores latest, rendered by interval)
     */
    function updateThreads(value) {
        latestThreads = value;
    }

    /**
     * Updates GC overhead (stores latest, rendered by interval)
     */
    function updateGc(value) {
        latestGc = value;
    }

    /**
     * Updates Latency value (stores latest, rendered by interval)
     */
    function updateLatency(value) {
        latestLatency = value;
    }

    /**
     * Updates all metrics (stores latest values, rendered by intervals)
     */
    function updateAll(metrics) {
        if (metrics.cpu) {
            latestCpu = metrics.cpu.usagePercent || 0;
        }
        if (metrics.memory) {
            latestMemory = metrics.memory.heapUsedMb || 0;
        }
        if (metrics.thread) {
            latestThreads = metrics.thread.activeCount || 0;
        }
        if (metrics.process) {
            latestGc = metrics.process.gcOverheadPercent || 0;
        }
    }

    /**
     * Destroys all charts and stops intervals
     */
    function destroy() {
        if (upperChartsInterval) clearInterval(upperChartsInterval);
        if (latencyChartInterval) clearInterval(latencyChartInterval);
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
