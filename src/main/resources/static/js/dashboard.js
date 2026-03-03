/* =============================================================================
   Performance Problem Simulator - Java Blessed Image
   Dashboard Module (UI Logic)
   ============================================================================= */

// Global simulation functions (called by onclick handlers)

/**
 * Starts CPU stress simulation
 */
async function startCpuStress() {
    const durationSeconds = parseInt(document.getElementById('cpuDuration').value) || 30;
    const intensity = document.getElementById('cpuIntensity').value || 'HIGH';

    try {
        const response = await fetch('/api/simulations/cpu', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ intensity, durationSeconds })
        });
        const result = await response.json();
        console.log('[Dashboard] CPU Stress started:', result);
    } catch (error) {
        console.error('[Dashboard] Failed to start CPU stress:', error);
        Dashboard.addEvent('error', 'Failed to start CPU stress: ' + error.message);
    }
}

/**
 * Stops all CPU stress simulations
 */
async function stopCpuStress() {
    try {
        const response = await fetch('/api/simulations/cpu', {
            method: 'DELETE'
        });
        const result = await response.json();
        console.log('[Dashboard] CPU Stress stopped:', result);
    } catch (error) {
        console.error('[Dashboard] Failed to stop CPU stress:', error);
        Dashboard.addEvent('error', 'Failed to stop CPU stress: ' + error.message);
    }
}

/**
 * Stops all thread starvation simulations
 */
async function stopThreadStarvation() {
    try {
        const response = await fetch('/api/simulations/thread/starvation', {
            method: 'DELETE'
        });
        const result = await response.json();
        console.log('[Dashboard] Thread Starvation stopped:', result);
    } catch (error) {
        console.error('[Dashboard] Failed to stop thread starvation:', error);
        Dashboard.addEvent('error', 'Failed to stop thread starvation: ' + error.message);
    }
}

/**
 * Allocates memory (no auto-release)
 */
let isAllocating = false;

async function allocateMemory() {
    // Prevent double-submission
    if (isAllocating) {
        console.log('[Dashboard] Allocation already in progress, ignoring');
        return;
    }
    isAllocating = true;
    
    const btn = document.querySelector('#memory-form button[type="submit"]');
    if (btn) btn.disabled = true;
    
    const sizeMb = parseInt(document.getElementById('memoryTarget').value) || 512;

    try {
        const response = await fetch('/api/simulations/memory', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sizeMb })
        });
        const result = await response.json();
        console.log('[Dashboard] Memory allocated:', result);
    } catch (error) {
        console.error('[Dashboard] Failed to allocate memory:', error);
        Dashboard.addEvent('error', 'Failed to allocate memory: ' + error.message);
    } finally {
        isAllocating = false;
        if (btn) btn.disabled = false;
    }
}

/**
 * Releases all memory allocations
 */
async function releaseAllMemory() {
    try {
        const response = await fetch('/api/simulations/memory', {
            method: 'DELETE'
        });
        const result = await response.json();
        console.log('[Dashboard] Memory released:', result);
    } catch (error) {
        console.error('[Dashboard] Failed to release memory:', error);
        Dashboard.addEvent('error', 'Failed to release memory: ' + error.message);
    }
}

/**
 * Starts thread pool starvation simulation.
 * Server spawns N internal requests to block Tomcat servlet threads.
 */
async function startThreadStarvation() {
    const threadCount = parseInt(document.getElementById('starvationCount').value) || 50;
    const durationSeconds = parseInt(document.getElementById('starvationDuration').value) || 30;

    try {
        const response = await fetch('/api/simulations/thread/starvation', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ threadCount, durationSeconds })
        });
        const result = await response.json();
        console.log('[Dashboard] Thread Starvation simulation started:', result);
        
    } catch (error) {
        console.error('[Dashboard] Failed to start thread starvation:', error);
        Dashboard.addEvent('error', 'Failed to start thread starvation: ' + error.message);
    }
}

/**
 * Starts connection pool exhaustion simulation
 */
async function triggerConnectionPool() {
    const poolSize = parseInt(document.getElementById('poolSize').value) || 10;
    const queryDurationSeconds = parseInt(document.getElementById('queryDuration').value) || 30;
    const concurrentQueries = parseInt(document.getElementById('concurrentQueries').value) || 20;
    const connectionTimeoutSeconds = parseInt(document.getElementById('connectionTimeout').value) || 5;

    try {
        const response = await fetch('/api/simulations/connection-pool', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                poolSize,
                queryDurationSeconds,
                concurrentQueries,
                connectionTimeoutSeconds
            })
        });
        const result = await response.json();
        console.log('[Dashboard] Connection Pool simulation started:', result);
    } catch (error) {
        console.error('[Dashboard] Connection Pool simulation failed:', error);
        Dashboard.addEvent('error', 'Connection Pool simulation failed: ' + error.message);
    }
}

/**
 * Stops connection pool simulations
 */
async function stopConnectionPool() {
    try {
        const response = await fetch('/api/simulations/connection-pool', {
            method: 'DELETE'
        });
        const result = await response.json();
        console.log('[Dashboard] Connection Pool simulation stopped:', result);
    } catch (error) {
        console.error('[Dashboard] Failed to stop connection pool simulation:', error);
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
    let lastKnownJvmStartTime = null;

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
        loadBuildInfo();

        // Add initial event
        addEvent('success', 'Dashboard initialized');

        console.log('[Dashboard] Initialization complete');
    }

    /**
     * Handles metrics updates from WebSocket
     */
    function handleMetrics(metrics) {
        // Check for JVM start time change (server restart)
        if (metrics.process && metrics.process.jvmStartTime) {
            const currentJvmStartTime = metrics.process.jvmStartTime;
            if (lastKnownJvmStartTime !== null && lastKnownJvmStartTime !== currentJvmStartTime) {
                const prevTime = new Date(lastKnownJvmStartTime).toLocaleTimeString();
                const newTime = new Date(currentJvmStartTime).toLocaleTimeString();
                // Use crash styling for restart notification
                addEventToLog({
                    level: 'WARN',
                    message: `APPLICATION RESTARTED! Started at ${newTime} (previously ${prevTime}). This may indicate an unexpected crash (OOM, StackOverflow, etc.)`,
                    timestamp: new Date().toISOString(),
                    simulationType: 'CRASH_EXCEPTION'
                });
            }
            lastKnownJvmStartTime = currentJvmStartTime;
        }
        
        // Update charts
        ChartsModule.updateAll(metrics);

        // Update current metrics display
        updateCurrentMetrics(metrics);
    }

    /**
     * Handles probe results from WebSocket
     */
    function handleProbeResult(result) {
        // Update latency chart immediately at probe rate (100ms)
        if (result.latencyMs !== undefined) {
            ChartsModule.updateLatency(result.latencyMs);
            
            // Update current latency display
            const latencyCurrentEl = document.getElementById('latency-current');
            if (latencyCurrentEl) {
                latencyCurrentEl.textContent = result.latencyMs.toFixed(1) + 'ms';
                
                // Add color class based on threshold (matches advertised thresholds)
                latencyCurrentEl.classList.remove('good', 'degraded', 'severe', 'critical');
                if (result.latencyMs < 150) {
                    latencyCurrentEl.classList.add('good');      // Good (<150ms)
                } else if (result.latencyMs < 1000) {
                    latencyCurrentEl.classList.add('degraded'); // Degraded (150ms-1s)
                } else if (result.latencyMs < 30000) {
                    latencyCurrentEl.classList.add('severe');   // Severe (>1s)
                } else {
                    latencyCurrentEl.classList.add('critical'); // Critical (>30s)
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
        
        // Update display with color classes matching thresholds
        const avgEl = document.getElementById('latency-avg');
        const maxEl = document.getElementById('latency-max');
        const critEl = document.getElementById('latency-critical');
        
        if (avgEl) {
            avgEl.textContent = avg.toFixed(1) + 'ms';
            avgEl.classList.remove('good', 'degraded', 'severe', 'critical');
            if (avg < 150) avgEl.classList.add('good');
            else if (avg < 1000) avgEl.classList.add('degraded');
            else if (avg < 30000) avgEl.classList.add('severe');
            else avgEl.classList.add('critical');
        }
        if (maxEl) {
            maxEl.textContent = max.toFixed(1) + 'ms';
            maxEl.classList.remove('good', 'degraded', 'severe', 'critical');
            if (max < 150) maxEl.classList.add('good');
            else if (max < 1000) maxEl.classList.add('degraded');
            else if (max < 30000) maxEl.classList.add('severe');
            else maxEl.classList.add('critical');
        }
        if (critEl) {
            critEl.textContent = criticalCount;
            critEl.classList.remove('good', 'critical');
            if (criticalCount > 0) critEl.classList.add('critical');
            else critEl.classList.add('good');
        }
    }

    // Probe visualization history
    const probeHistory = [];
    const MAX_PROBE_DOTS = 24;

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
        const systemMem = document.getElementById('system-memory');
        if (memEl && metrics.memory) {
            const heapUsed = metrics.memory.heapUsedMb || 0;
            const heapMax = metrics.memory.heapMaxMb || 1000;
            const totalSystem = metrics.memory.totalSystemMb || 0;
            memEl.textContent = heapUsed.toFixed(0);
            if (memBar) memBar.style.width = Math.min((heapUsed / heapMax) * 100, 100) + '%';
            if (memTotal) memTotal.textContent = 'of ' + (heapMax / 1024).toFixed(1) + ' GB heap';
            if (systemMem) systemMem.textContent = 'System: ' + (totalSystem / 1024).toFixed(1) + ' GB';
        }

        // Threads Tile
        const threadEl = document.getElementById('threads-value');
        const threadBar = document.getElementById('threads-bar');
        if (threadEl && metrics.thread) {
            const threadCount = metrics.thread.activeCount || 0;
            threadEl.textContent = threadCount;
            if (threadBar) threadBar.style.width = Math.min((threadCount / 500) * 100, 100) + '%';
        }

        // GC Tile (shows GC Overhead %)
        const gcEl = document.getElementById('gc-value');
        const gcBar = document.getElementById('gc-bar');
        if (gcEl && metrics.process) {
            const gcOverhead = metrics.process.gcOverheadPercent || 0;
            gcEl.textContent = gcOverhead.toFixed(1);
            // Bar fills at 10% overhead (considered high)
            if (gcBar) gcBar.style.width = Math.min((gcOverhead / 10) * 100, 100) + '%';
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
     * Gets the emoji for a simulation type
     */
    function getSimulationEmoji(simulationType) {
        const emojiMap = {
            'CPU_STRESS': '🔥',
            'MEMORY_PRESSURE': '📊',
            'THREAD_STARVATION': '🧵',
            'CONNECTION_POOL_EXHAUSTION': '🔌',
            'CRASH_EXCEPTION': '💥',
            'CRASH_MEMORY': '💥',
            'CRASH_FAILFAST': '💥',
            'CRASH_STACKOVERFLOW': '💥'
        };
        return emojiMap[simulationType] || '';
    }

    /**
     * Gets the CSS class for a simulation type
     */
    function getSimulationClass(simulationType) {
        const classMap = {
            'CPU_STRESS': 'sim-cpu',
            'MEMORY_PRESSURE': 'sim-memory',
            'THREAD_STARVATION': 'sim-threads',
            'CONNECTION_POOL_EXHAUSTION': 'sim-connection-pool',
            'CRASH_EXCEPTION': 'sim-crash',
            'CRASH_MEMORY': 'sim-crash',
            'CRASH_FAILFAST': 'sim-crash',
            'CRASH_STACKOVERFLOW': 'sim-crash'
        };
        return classMap[simulationType] || '';
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
            const simClass = getSimulationClass(event.simulationType);
            const time = new Date(event.timestamp).toLocaleTimeString();
            const emoji = getSimulationEmoji(event.simulationType);
            const prefix = emoji ? emoji + ' ' : '';
            
            const eventDiv = document.createElement('div');
            eventDiv.className = `event ${levelClass} ${simClass}`.trim();
            eventDiv.innerHTML = `<span class="timestamp">${time}</span> ${prefix}${event.message}`;
            
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
            const response = await fetch('/api/health/environment');
            const data = await response.json();
            
            // Update SKU badge in header
            const skuBadge = document.getElementById('sku-badge');
            if (skuBadge) {
                skuBadge.textContent = data.isAzure 
                    ? `SKU: ${data.sku}`
                    : 'SKU: Local';
            }
        } catch (error) {
            console.error('[Dashboard] Failed to load SKU info:', error);
        }
    }

    /**
     * Loads footer info (credits and build time) from the server
     */
    async function loadBuildInfo() {
        try {
            const response = await fetch('/api/health/footer');
            const data = await response.json();
            
            // Update footer credits if PAGE_FOOTER env var is set
            const footerCredits = document.getElementById('footer-credits');
            if (footerCredits) {
                if (data.footer) {
                    footerCredits.innerHTML = data.footer;
                } else {
                    footerCredits.style.display = 'none';
                }
            }
            
            // Update build info
            const buildInfo = document.getElementById('build-info');
            if (buildInfo) {
                buildInfo.textContent = `Build: ${data.buildTime}`;
            }
        } catch (error) {
            console.log('[Dashboard] Could not load footer info');
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
        if (type.includes('CONNECTION_POOL')) return 'connection-pool';
        return '';
    }

    /**
     * Formats simulation type for display
     */
    function formatSimType(type) {
        const typeMap = {
            'CPU_STRESS': '🔥 CPU Stress',
            'MEMORY_PRESSURE': '💾 Memory Pressure',
            'THREAD_STARVATION': '🧵 Thread Starvation',
            'CONNECTION_POOL_EXHAUSTION': '🔌 Connection Pool',
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
