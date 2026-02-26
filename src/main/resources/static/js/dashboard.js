/* =============================================================================
   Performance Problem Simulator - Java Blessed Image
   Dashboard Module (UI Logic)
   ============================================================================= */

// Global simulation functions (called by onclick handlers)

/**
 * Starts CPU stress simulation
 */
async function startCpuStress() {
    const workers = parseInt(document.getElementById('cpuWorkers').value) || 4;
    const durationMs = parseInt(document.getElementById('cpuDuration').value) || 30000;

    try {
        const response = await fetch('/api/simulations/cpu/stress', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ workers, durationMs })
        });
        const result = await response.json();
        console.log('[Dashboard] CPU Stress started:', result);
        Dashboard.addEvent('info', `CPU Stress started with ${workers} workers for ${durationMs}ms`);
    } catch (error) {
        console.error('[Dashboard] Failed to start CPU stress:', error);
        Dashboard.addEvent('error', 'Failed to start CPU stress: ' + error.message);
    }
}

/**
 * Starts memory pressure simulation
 */
async function startMemoryPressure() {
    const targetMb = parseInt(document.getElementById('memoryTarget').value) || 500;
    const durationMs = parseInt(document.getElementById('memoryDuration').value) || 30000;

    try {
        const response = await fetch('/api/simulations/memory/pressure', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ targetMb, durationMs })
        });
        const result = await response.json();
        console.log('[Dashboard] Memory Pressure started:', result);
        Dashboard.addEvent('info', `Memory Pressure started with target ${targetMb}MB for ${durationMs}ms`);
    } catch (error) {
        console.error('[Dashboard] Failed to start memory pressure:', error);
        Dashboard.addEvent('error', 'Failed to start memory pressure: ' + error.message);
    }
}

/**
 * Starts thread pool starvation simulation
 */
async function startThreadStarvation() {
    const blockedThreadCount = parseInt(document.getElementById('starvationCount').value) || 50;
    const durationMs = parseInt(document.getElementById('starvationDuration').value) || 30000;

    try {
        const response = await fetch('/api/simulations/thread/starvation', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ blockedThreadCount, durationMs })
        });
        const result = await response.json();
        console.log('[Dashboard] Thread Starvation started:', result);
        Dashboard.addEvent('warn', `Thread Starvation started with ${blockedThreadCount} threads for ${durationMs}ms`);
    } catch (error) {
        console.error('[Dashboard] Failed to start thread starvation:', error);
        Dashboard.addEvent('error', 'Failed to start thread starvation: ' + error.message);
    }
}

/**
 * Triggers a slow request
 */
async function triggerSlowRequest() {
    const delayMs = parseInt(document.getElementById('slowDelay').value) || 5000;
    const pattern = document.getElementById('slowPattern').value || 'SLEEP';

    try {
        const response = await fetch(`/api/simulations/slow/request?delayMs=${delayMs}&pattern=${pattern}`, {
            method: 'GET'
        });
        const result = await response.json();
        console.log('[Dashboard] Slow Request completed:', result);
        Dashboard.addEvent('info', `Slow Request completed after ${result.actualDurationMs}ms`);
    } catch (error) {
        console.error('[Dashboard] Slow request failed:', error);
        Dashboard.addEvent('error', 'Slow request failed: ' + error.message);
    }
}

/**
 * Triggers a crash simulation
 */
async function triggerCrash() {
    const type = document.getElementById('crashType').value || 'EXCEPTION';

    if (!confirm(`⚠️ Are you sure you want to trigger a ${type} crash? This may terminate the application.`)) {
        return;
    }

    try {
        const response = await fetch('/api/simulations/crash', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type })
        });
        const result = await response.json();
        console.log('[Dashboard] Crash triggered:', result);
        Dashboard.addEvent('error', `Crash triggered: ${type}`);
    } catch (error) {
        console.error('[Dashboard] Crash request failed:', error);
        Dashboard.addEvent('error', 'Application may have crashed');
    }
}

/**
 * Dashboard module
 */
const Dashboard = (function() {
    'use strict';

    const MAX_EVENTS = 100;
    const eventLog = [];

    /**
     * Initializes the dashboard
     */
    function init() {
        console.log('[Dashboard] Initializing...');

        // Initialize charts
        ChartsModule.init();

        // Connect to WebSocket
        SocketClient.connect();

        // Set up WebSocket event handlers
        SocketClient.on('onMetrics', handleMetrics);
        SocketClient.on('onProbeResult', handleProbeResult);
        SocketClient.on('onEvent', handleEvent);
        SocketClient.on('onSimulationUpdate', handleSimulationUpdate);

        // Load initial data
        loadSkuInfo();
        loadActiveSimulations();

        // Add initial event
        addEvent('success', 'Dashboard initialized');

        console.log('[Dashboard] Initialization complete');
    }

    /**
     * Handles metrics updates from WebSocket
     */
    function handleMetrics(metrics) {
        // Update charts
        ChartsModule.updateAll(metrics);

        // Update current metrics display
        updateCurrentMetrics(metrics);
    }

    /**
     * Handles probe results from WebSocket
     */
    function handleProbeResult(result) {
        // Update latency chart
        if (result.latencyMs !== undefined) {
            ChartsModule.updateLatency(result.latencyMs);
            
            // Update current latency display
            const latencyCurrentEl = document.getElementById('latency-current');
            if (latencyCurrentEl) {
                latencyCurrentEl.textContent = result.latencyMs.toFixed(1) + 'ms';
                
                // Add color class based on threshold
                latencyCurrentEl.classList.remove('good', 'warning', 'danger');
                if (result.latencyMs < 150) {
                    latencyCurrentEl.classList.add('good');
                } else if (result.latencyMs < 1000) {
                    latencyCurrentEl.classList.add('warning');
                } else {
                    latencyCurrentEl.classList.add('danger');
                }
            }
            
            // Update probe visualization
            updateProbeVisualization(result.latencyMs);
            
            // Update latency stats
            updateLatencyStats(result.latencyMs);
        }
    }

    // Latency history for stats
    const latencyHistory = [];
    const MAX_LATENCY_HISTORY = 600; // 60 seconds at 100ms intervals
    let criticalCount = 0;

    /**
     * Updates latency statistics
     */
    function updateLatencyStats(latency) {
        latencyHistory.push(latency);
        if (latencyHistory.length > MAX_LATENCY_HISTORY) {
            latencyHistory.shift();
        }
        
        // Check for critical
        if (latency > 30000) {
            criticalCount++;
        }
        
        // Calculate stats
        const sum = latencyHistory.reduce((a, b) => a + b, 0);
        const avg = sum / latencyHistory.length;
        const max = Math.max(...latencyHistory);
        
        // Update display
        const avgEl = document.getElementById('latency-avg');
        const maxEl = document.getElementById('latency-max');
        const critEl = document.getElementById('latency-critical');
        
        if (avgEl) avgEl.textContent = avg.toFixed(1) + 'ms';
        if (maxEl) maxEl.textContent = max.toFixed(1) + 'ms';
        if (critEl) critEl.textContent = criticalCount;
    }

    // Probe visualization history
    const probeHistory = [];
    const MAX_PROBE_DOTS = 30;

    /**
     * Updates probe visualization dots
     */
    function updateProbeVisualization(latency) {
        let status = 'good';
        if (latency >= 30000) status = 'failed';
        else if (latency >= 1000) status = 'slow';
        else if (latency >= 150) status = 'degraded';
        
        probeHistory.push(status);
        if (probeHistory.length > MAX_PROBE_DOTS) {
            probeHistory.shift();
        }
        
        const vizEl = document.getElementById('probe-visualization');
        if (vizEl) {
            vizEl.innerHTML = probeHistory.map(s => 
                `<span class="probe-dot-inline ${s === 'good' ? '' : s}"></span>`
            ).join('');
        }
    }

    /**
     * Handles event log entries from WebSocket
     */
    function handleEvent(event) {
        addEventToLog(event);
    }

    /**
     * Handles simulation updates from WebSocket
     */
    function handleSimulationUpdate(update) {
        console.log('[Dashboard] Simulation update:', update);
        loadActiveSimulations();
    }

    /**
     * Updates the current metrics display (metric tiles)
     */
    function updateCurrentMetrics(metrics) {
        // CPU Tile
        const cpuEl = document.getElementById('cpu-value');
        const cpuBar = document.getElementById('cpu-bar');
        if (cpuEl && metrics.cpu) {
            const cpuValue = metrics.cpu.usagePercent || 0;
            cpuEl.textContent = cpuValue.toFixed(1);
            if (cpuBar) cpuBar.style.width = Math.min(cpuValue, 100) + '%';
        }

        // Memory Tile
        const memEl = document.getElementById('memory-value');
        const memBar = document.getElementById('memory-bar');
        const memTotal = document.getElementById('memory-total');
        if (memEl && metrics.memory) {
            const heapUsed = metrics.memory.heapUsedMb || 0;
            const heapMax = metrics.memory.heapMaxMb || 1000;
            memEl.textContent = heapUsed.toFixed(0);
            if (memBar) memBar.style.width = Math.min((heapUsed / heapMax) * 100, 100) + '%';
            if (memTotal) memTotal.textContent = 'of ' + (heapMax / 1024).toFixed(1) + ' GB';
        }

        // Threads Tile
        const threadEl = document.getElementById('threads-value');
        const threadBar = document.getElementById('threads-bar');
        if (threadEl && metrics.thread) {
            const threadCount = metrics.thread.activeCount || 0;
            threadEl.textContent = threadCount;
            if (threadBar) threadBar.style.width = Math.min((threadCount / 500) * 100, 100) + '%';
        }

        // GC Tile
        const gcEl = document.getElementById('gc-value');
        const gcBar = document.getElementById('gc-bar');
        if (gcEl && metrics.gc) {
            gcEl.textContent = metrics.gc.totalCollections || 0;
            if (gcBar) gcBar.style.width = Math.min((metrics.gc.totalCollections / 100) * 100, 100) + '%';
        }
    }

    /**
     * Adds an event to the event log UI
     */
    function addEvent(level, message) {
        const event = {
            level: level.toUpperCase(),
            message: message,
            timestamp: new Date().toISOString()
        };
        addEventToLog(event);
    }

    /**
     * Adds an event entry to the log
     */
    function addEventToLog(event) {
        eventLog.unshift(event);
        if (eventLog.length > MAX_EVENTS) {
            eventLog.pop();
        }

        const logEl = document.getElementById('eventLog');
        if (logEl) {
            const levelClass = event.level.toLowerCase();
            const time = new Date(event.timestamp).toLocaleTimeString();
            
            const eventDiv = document.createElement('div');
            eventDiv.className = `event ${levelClass}`;
            eventDiv.innerHTML = `<span class="timestamp">${time}</span> ${event.message}`;
            
            logEl.insertBefore(eventDiv, logEl.firstChild);

            // Remove excess events
            while (logEl.children.length > MAX_EVENTS) {
                logEl.removeChild(logEl.lastChild);
            }
        }
    }

    /**
     * Loads SKU info from the server
     */
    async function loadSkuInfo() {
        try {
            const response = await fetch('/api/admin/sku');
            const data = await response.json();
            
            // Update SKU badge in header
            const skuBadge = document.getElementById('sku-badge');
            if (skuBadge) {
                skuBadge.textContent = data.isAzure 
                    ? `SKU: ${data.sku}`
                    : 'SKU: Local';
            }
            
            // Update footer
            const skuEl = document.getElementById('skuInfo');
            if (skuEl) {
                skuEl.textContent = data.isAzure 
                    ? `Azure App Service (${data.sku})`
                    : 'Local Development';
            }
        } catch (error) {
            console.error('[Dashboard] Failed to load SKU info:', error);
        }
    }

    /**
     * Loads active simulations from the server
     */
    async function loadActiveSimulations() {
        try {
            const response = await fetch('/api/simulations');
            const data = await response.json();
            
            const listEl = document.getElementById('active-simulations-list');
            if (listEl) {
                if (data.simulations && data.simulations.length > 0) {
                    listEl.innerHTML = '<div class="simulations-list">' + data.simulations.map(sim => `
                        <div class="simulation-badge ${getSimClass(sim.type)}">
                            <div class="spinner"></div>
                            <span>${formatSimType(sim.type)}</span>
                        </div>
                    `).join('') + '</div>';
                } else {
                    listEl.innerHTML = '<p class="no-simulations">No active simulations</p>';
                }
            }
        } catch (error) {
            console.error('[Dashboard] Failed to load simulations:', error);
        }
    }

    /**
     * Gets CSS class for simulation type
     */
    function getSimClass(type) {
        if (type.includes('CPU')) return 'cpu';
        if (type.includes('MEMORY')) return 'memory';
        if (type.includes('THREAD')) return 'threads';
        return '';
    }

    /**
     * Formats simulation type for display
     */
    function formatSimType(type) {
        const typeMap = {
            'CPU_STRESS': '🔥 CPU Stress',
            'MEMORY_PRESSURE': '💾 Memory Pressure',
            'THREAD_STARVATION': '⏳ Thread Starvation',
            'SLOW_REQUEST': '🐢 Slow Request',
            'CRASH_FAILFAST': '💥 Crash (Exit)',
            'CRASH_STACKOVERFLOW': '💥 Stack Overflow',
            'CRASH_EXCEPTION': '💥 Exception',
            'CRASH_OOM': '💥 Out of Memory'
        };
        return typeMap[type] || type;
    }

    // Public API
    return {
        init,
        addEvent,
        loadActiveSimulations
    };
})();

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    Dashboard.init();
});
