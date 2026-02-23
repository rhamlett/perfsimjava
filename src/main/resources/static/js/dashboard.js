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
        // Update latency chart (higher frequency - 100ms)
        if (result.latencyMs !== undefined) {
            ChartsModule.updateLatency(result.latencyMs);
            
            // Update latency display
            const latencyEl = document.getElementById('currentLatency');
            if (latencyEl) {
                latencyEl.textContent = result.latencyMs.toFixed(0) + ' ms';
            }
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
     * Updates the current metrics display
     */
    function updateCurrentMetrics(metrics) {
        // CPU
        const cpuEl = document.getElementById('currentCpu');
        if (cpuEl && metrics.cpu) {
            cpuEl.textContent = metrics.cpu.usagePercent.toFixed(1) + '%';
        }

        // Memory
        const memEl = document.getElementById('currentMemory');
        if (memEl && metrics.memory) {
            memEl.textContent = metrics.memory.heapUsedMb.toFixed(0) + ' MB';
        }

        // Threads
        const threadEl = document.getElementById('currentThreads');
        if (threadEl && metrics.thread) {
            threadEl.textContent = metrics.thread.activeCount;
        }

        // GC Count
        const gcCountEl = document.getElementById('currentGcCount');
        if (gcCountEl && metrics.gc) {
            gcCountEl.textContent = metrics.gc.totalCollections;
        }

        // GC Time
        const gcTimeEl = document.getElementById('currentGcTime');
        if (gcTimeEl && metrics.gc) {
            gcTimeEl.textContent = metrics.gc.totalTimeMs + ' ms';
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
            
            const listEl = document.getElementById('simulationList');
            if (listEl) {
                if (data.simulations && data.simulations.length > 0) {
                    listEl.innerHTML = data.simulations.map(sim => `
                        <div class="simulation-item">
                            <span class="sim-type">${formatSimType(sim.type)}</span>
                            <span class="sim-status">${sim.status}</span>
                        </div>
                    `).join('');
                } else {
                    listEl.innerHTML = '<p class="no-simulations">No active simulations</p>';
                }
            }
        } catch (error) {
            console.error('[Dashboard] Failed to load simulations:', error);
        }
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
