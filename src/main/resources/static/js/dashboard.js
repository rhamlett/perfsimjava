/* =============================================================================
   Performance Problem Simulator - Java Blessed Image
   Dashboard Module (UI Logic)
   ============================================================================= */

// Global simulation functions (called by onclick handlers)

/**
 * Starts CPU stress simulation
 */
async function startCpuStress() {
    SocketClient.ensureWebSocket();
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
    SocketClient.ensureWebSocket();
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
    SocketClient.ensureWebSocket();
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
    SocketClient.ensureWebSocket();
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
 * Starts failed requests simulation (generates HTTP 5xx errors)
 */
async function triggerFailedRequests() {
    SocketClient.ensureWebSocket();
    const numberOfRequests = parseInt(document.getElementById('numberOfFailedRequests').value) || 10;

    try {
        const response = await fetch('/api/simulations/failed-requests', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ numberOfRequests })
        });
        const result = await response.json();
        console.log('[Dashboard] Failed Requests simulation started:', result);
    } catch (error) {
        console.error('[Dashboard] Failed Requests simulation failed:', error);
        Dashboard.addEvent('error', 'Failed Requests simulation failed: ' + error.message);
    }
}

/**
 * Triggers a crash simulation
 */
async function triggerCrash() {
    SocketClient.ensureWebSocket();
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

    const eventLog = [];
    let lastKnownJvmStartTime = null;

    /**
     * Initializes the dashboard
     */
    async function init() {
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

        // Set up copy event log button
        initCopyEventLogButton();

        // Load initial event log with deterministic order
        await loadEventLog();

        // Load non-event-log data (fire-and-forget)
        loadActiveSimulations();
        loadBuildInfo();

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
                const prevTime = new Date(lastKnownJvmStartTime).toLocaleTimeString('en-US', { hour12: false, timeZone: 'UTC' });
                const newTime = new Date(currentJvmStartTime).toLocaleTimeString('en-US', { hour12: false, timeZone: 'UTC' });
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
        // Update latency chart immediately at probe rate
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
        
        // Update connection status based on idle state changes
        if (event.event === 'GOING_IDLE') {
            const statusEl = document.getElementById('connection-status');
            if (statusEl) {
                statusEl.classList.remove('status-connected', 'status-disconnected', 'status-reconnecting', 'status-idle');
                statusEl.classList.add('status-idle');
                statusEl.textContent = 'Idle';
            }
            // Intentionally close WebSocket to prevent reconnect-induced status flicker
            SocketClient.closeForIdle();
        } else if (event.event === 'WAKING_UP') {
            // WebSocket will have already reconnected (via ensureWebSocket on button click)
            // and onConnect will have set the status to Connected. This is a belt-and-
            // suspenders update for any edge cases where the event arrives first.
            const statusEl = document.getElementById('connection-status');
            if (statusEl) {
                statusEl.classList.remove('status-connected', 'status-disconnected', 'status-reconnecting', 'status-idle');
                statusEl.classList.add('status-connected');
                statusEl.textContent = 'Connected';
            }
        }
    }

    /**
     * Handles simulation updates from WebSocket
     */
    function handleSimulationUpdate(update) {
        console.log('[Dashboard] Simulation update:', update);
        loadActiveSimulations();
    }

    /**
     * Returns the appropriate stress class based on value and thresholds
     * @param {number} value - Current value
     * @param {number} warningThreshold - Yellow threshold
     * @param {number} criticalThreshold - Red threshold
     * @returns {string} CSS class name
     */
    function getStressClass(value, warningThreshold, criticalThreshold) {
        if (value >= criticalThreshold) return 'stress-critical';
        if (value >= warningThreshold) return 'stress-warning';
        return 'stress-normal';
    }

    /**
     * Applies stress color class to an element
     * @param {HTMLElement} el - Element to style
     * @param {string} stressClass - CSS class to apply
     */
    function applyStressClass(el, stressClass) {
        if (!el) return;
        el.classList.remove('stress-normal', 'stress-warning', 'stress-critical');
        el.classList.add(stressClass);
    }

    /**
     * Updates the current metrics display (metric tiles)
     */
    function updateCurrentMetrics(metrics) {
        // CPU Tile - yellow at 60%, red at 80%
        const cpuEl = document.getElementById('cpu-value');
        const cpuBar = document.getElementById('cpu-bar');
        if (cpuEl && metrics.cpu) {
            const cpuValue = metrics.cpu.usagePercent || 0;
            cpuEl.textContent = cpuValue.toFixed(1);
            if (cpuBar) cpuBar.style.width = Math.min(cpuValue, 100) + '%';
            applyStressClass(cpuEl, getStressClass(cpuValue, 60, 80));
        }

        // Memory Tile - based on heap percentage: yellow at 60%, red at 80%
        const memEl = document.getElementById('memory-value');
        const memBar = document.getElementById('memory-bar');
        const memTotal = document.getElementById('memory-total');
        const systemMem = document.getElementById('system-memory');
        if (memEl && metrics.memory) {
            const heapUsed = metrics.memory.heapUsedMb || 0;
            const heapMax = metrics.memory.heapMaxMb || 1000;
            const totalSystem = metrics.memory.totalSystemMb || 0;
            const heapPercent = (heapUsed / heapMax) * 100;
            memEl.textContent = heapUsed.toFixed(0);
            if (memBar) memBar.style.width = Math.min(heapPercent, 100) + '%';
            if (memTotal) memTotal.textContent = 'of ' + (heapMax / 1024).toFixed(1) + ' GB heap';
            if (systemMem) systemMem.textContent = 'System: ' + (totalSystem / 1024).toFixed(1) + ' GB';
            applyStressClass(memEl, getStressClass(heapPercent, 60, 80));
        }

        // Threads Tile - yellow at 200 threads, red at 400 threads
        const threadEl = document.getElementById('threads-value');
        const threadBar = document.getElementById('threads-bar');
        if (threadEl && metrics.thread) {
            const threadCount = metrics.thread.activeCount || 0;
            threadEl.textContent = threadCount;
            if (threadBar) threadBar.style.width = Math.min((threadCount / 500) * 100, 100) + '%';
            applyStressClass(threadEl, getStressClass(threadCount, 200, 400));
        }

        // GC Tile (shows GC Overhead %) - yellow at 2%, red at 5%
        const gcEl = document.getElementById('gc-value');
        const gcBar = document.getElementById('gc-bar');
        if (gcEl && metrics.process) {
            const gcOverhead = metrics.process.gcOverheadPercent || 0;
            gcEl.textContent = gcOverhead.toFixed(1);
            // Bar fills at 10% overhead (considered high)
            if (gcBar) gcBar.style.width = Math.min((gcOverhead / 10) * 100, 100) + '%';
            applyStressClass(gcEl, getStressClass(gcOverhead, 2, 5));
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
     * Gets the emoji for a simulation type or event type
     */
    function getSimulationEmoji(simulationType, eventType) {
        const emojiMap = {
            'CPU_STRESS': '🔥',
            'MEMORY_PRESSURE': '📊',
            'THREAD_STARVATION': '🧵',
            'CONNECTION_POOL_EXHAUSTION': '🔌',
            'FAILED_REQUESTS': '❌',
            'CRASH_EXCEPTION': '💥',
            'CRASH_MEMORY': '💥',
            'CRASH_FAILFAST': '💥',
            'CRASH_STACKOVERFLOW': '💥',
            'LOAD_TEST_STATS': '📊'
        };
        return emojiMap[simulationType] || emojiMap[eventType] || '';
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
            'FAILED_REQUESTS': 'sim-failed-requests',
            'CRASH_EXCEPTION': 'sim-crash',
            'CRASH_MEMORY': 'sim-crash',
            'CRASH_FAILFAST': 'sim-crash',
            'CRASH_STACKOVERFLOW': 'sim-crash'
        };
        return classMap[simulationType] || '';
    }

    /**
     * Initializes the copy event log button
     */
    function initCopyEventLogButton() {
        const copyBtn = document.getElementById('copy-event-log-btn');
        if (copyBtn) {
            copyBtn.addEventListener('click', copyEventLogToClipboard);
        }
    }

    /**
     * Copies all event log content to clipboard
     */
    function copyEventLogToClipboard() {
        const copyBtn = document.getElementById('copy-event-log-btn');
        
        // Format event log entries as text (include emojis like the UI display)
        const logText = eventLog.map(event => {
            const time = new Date(event.timestamp).toLocaleTimeString('en-US', { hour12: false, timeZone: 'UTC' }) + ' UTC';
            const level = event.level ? `[${event.level}]` : '';
            const emoji = getSimulationEmoji(event.simulationType, event.event);
            const prefix = emoji ? emoji + ' ' : '';
            return `${time} ${level} ${prefix}${event.message}`;
        }).join('\n');

        const textToCopy = logText || 'No events to copy';
        
        // Show copied feedback
        function showCopiedFeedback() {
            if (copyBtn) {
                const iconSpan = copyBtn.querySelector('.copy-icon');
                const textSpan = copyBtn.querySelector('.copy-text');
                const originalIcon = iconSpan.textContent;
                const originalText = textSpan.textContent;
                
                copyBtn.classList.add('copied');
                iconSpan.textContent = '✓';
                textSpan.textContent = 'Copied!';
                
                // Reset after 2 seconds
                setTimeout(() => {
                    copyBtn.classList.remove('copied');
                    iconSpan.textContent = originalIcon;
                    textSpan.textContent = originalText;
                }, 2000);
            }
        }

        // Try modern clipboard API first, fall back to execCommand
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(textToCopy)
                .then(showCopiedFeedback)
                .catch(function(error) {
                    console.error('[Dashboard] Clipboard API failed, trying fallback:', error);
                    fallbackCopyToClipboard(textToCopy, showCopiedFeedback);
                });
        } else {
            fallbackCopyToClipboard(textToCopy, showCopiedFeedback);
        }
    }

    /**
     * Fallback copy method using execCommand for older browsers or when clipboard API fails
     */
    function fallbackCopyToClipboard(text, onSuccess) {
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        textArea.style.top = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        
        try {
            const successful = document.execCommand('copy');
            if (successful && onSuccess) {
                onSuccess();
            } else {
                console.error('[Dashboard] execCommand copy failed');
                addEvent('error', 'Failed to copy event log to clipboard');
            }
        } catch (error) {
            console.error('[Dashboard] Fallback copy failed:', error);
            addEvent('error', 'Failed to copy event log to clipboard');
        }
        
        document.body.removeChild(textArea);
    }

    /**
     * Adds an event entry to the log
     * @param {Object} event - The event object
     * @param {boolean} skipRender - If true, skip DOM rendering (used for batch init)
     */
    function addEventToLog(event, skipRender) {
        eventLog.unshift(event);
        if (skipRender) return;

        const logEl = document.getElementById('eventLog');
        if (logEl) {
            const levelClass = event.level.toLowerCase();
            const simClass = getSimulationClass(event.simulationType);
            const time = new Date(event.timestamp).toLocaleTimeString('en-US', { hour12: false, timeZone: 'UTC' }) + ' UTC';
            const emoji = getSimulationEmoji(event.simulationType, event.event);
            const prefix = emoji ? emoji + ' ' : '';
            
            const eventDiv = document.createElement('div');
            const nonSimClass = simClass ? '' : 'non-sim';
            eventDiv.className = `event ${levelClass} ${simClass} ${nonSimClass}`.trim();
            
            // Check if this is a simulation start/complete/stop event that should have clickable ID
            const isSimulationBoundaryEvent = event.simulationId && 
                event.simulationType !== 'CRASH_EXCEPTION' &&
                (event.event === 'SIMULATION_STARTED' || 
                 event.event === 'SIMULATION_COMPLETED' || 
                 event.event === 'SIMULATION_STOPPED' ||
                 event.event === 'MEMORY_ALLOCATED' ||
                 event.event === 'MEMORY_RELEASED');
            
            if (isSimulationBoundaryEvent) {
                // Make the message text clickable with dotted underline
                eventDiv.innerHTML = `<span class="timestamp">${time}</span> ${prefix}<span class="sim-message" 
                    data-sim-id="${event.simulationId}" 
                    title="Click to copy Simulation ID: ${event.simulationId}"
                    onclick="Dashboard.copySimulationId('${event.simulationId}', this)">${event.message}</span>`;
            } else {
                // Regular event without clickable simulation ID
                eventDiv.innerHTML = `<span class="timestamp">${time}</span> ${prefix}${event.message}`;
            }
            
            logEl.insertBefore(eventDiv, logEl.firstChild);
        }
    }

    /**
     * Re-renders the entire event log sorted by timestamp descending (newest first).
     * Used after batch-loading initial events with deterministic timestamps.
     */
    function renderEventLog() {
        const logEl = document.getElementById('eventLog');
        if (!logEl) return;

        // Sort newest first
        eventLog.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));

        logEl.innerHTML = '';
        eventLog.forEach(event => {
            const levelClass = event.level.toLowerCase();
            const simClass = getSimulationClass(event.simulationType);
            const time = new Date(event.timestamp).toLocaleTimeString('en-US', { hour12: false, timeZone: 'UTC' }) + ' UTC';
            const emoji = getSimulationEmoji(event.simulationType, event.event);
            const prefix = emoji ? emoji + ' ' : '';
            const nonSimClass = simClass ? '' : 'non-sim';

            const eventDiv = document.createElement('div');
            eventDiv.className = `event ${levelClass} ${simClass} ${nonSimClass}`.trim();

            const isSimulationBoundaryEvent = event.simulationId &&
                event.simulationType !== 'CRASH_EXCEPTION' &&
                (event.event === 'SIMULATION_STARTED' ||
                 event.event === 'SIMULATION_COMPLETED' ||
                 event.event === 'SIMULATION_STOPPED' ||
                 event.event === 'MEMORY_ALLOCATED' ||
                 event.event === 'MEMORY_RELEASED');

            if (isSimulationBoundaryEvent) {
                eventDiv.innerHTML = `<span class="timestamp">${time}</span> ${prefix}<span class="sim-message"
                    data-sim-id="${event.simulationId}"
                    title="Click to copy Simulation ID: ${event.simulationId}"
                    onclick="Dashboard.copySimulationId('${event.simulationId}', this)">${event.message}</span>`;
            } else {
                eventDiv.innerHTML = `<span class="timestamp">${time}</span> ${prefix}${event.message}`;
            }

            logEl.appendChild(eventDiv);
        });
    }

    /**
     * Copies simulation ID to clipboard with visual feedback
     */
    function copySimulationId(simulationId, element) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(simulationId).then(() => {
                showCopyFeedback(element, true);
            }).catch(() => {
                fallbackCopySimulationId(simulationId, element);
            });
        } else {
            fallbackCopySimulationId(simulationId, element);
        }
    }

    /**
     * Fallback copy method using textarea
     */
    function fallbackCopySimulationId(simulationId, element) {
        const textArea = document.createElement('textarea');
        textArea.value = simulationId;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        textArea.style.top = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        
        try {
            const successful = document.execCommand('copy');
            showCopyFeedback(element, successful);
        } catch (error) {
            console.error('[Dashboard] Failed to copy simulation ID:', error);
            showCopyFeedback(element, false);
        }
        
        document.body.removeChild(textArea);
    }

    /**
     * Shows visual feedback after copy attempt
     */
    function showCopyFeedback(element, success) {
        if (!element) return;
        
        const originalTitle = element.getAttribute('title');
        const originalText = element.textContent;
        
        if (success) {
            element.classList.add('copied');
            element.setAttribute('title', 'Copied!');
            
            // Reset after 1.5 seconds
            setTimeout(() => {
                element.classList.remove('copied');
                element.setAttribute('title', originalTitle);
            }, 1500);
        } else {
            element.setAttribute('title', 'Copy failed');
            setTimeout(() => {
                element.setAttribute('title', originalTitle);
            }, 1500);
        }
    }

    /**
     * Loads the initial event log with deterministic order using timestamp offsets.
     * Messages are listed oldest-first; renderEventLog() sorts newest-first.
     * This ensures consistent ordering across all sister projects.
     */
    async function loadEventLog() {
        eventLog.length = 0;
        const baseTime = Date.now();

        // 1. Liability disclaimers (oldest — appear at bottom of log)
        addEventToLog({
            level: 'WARN',
            event: 'DISCLAIMER',
            message: '⚖️ Deploy only in isolated, non-production environments. Licensed under MIT License.',
            timestamp: new Date(baseTime).toISOString()
        }, true);
        addEventToLog({
            level: 'WARN',
            event: 'DISCLAIMER',
            message: '⚖️ This software is provided "AS IS" without warranty. The author shall not be liable for any damages arising from use or misuse.',
            timestamp: new Date(baseTime + 1).toISOString()
        }, true);

        // 2. Dashboard config info
        let probeRate = 200;
        let idleTimeoutStr = '5m';
        try {
            const configResponse = await fetch('/api/health/config');
            const config = await configResponse.json();
            probeRate = config.latencyProbeIntervalMs || 200;
            const idleTimeout = config.idleTimeoutMinutes;
            idleTimeoutStr = idleTimeout === 0 ? 'disabled' : `${idleTimeout}m`;

            // Show GitHub link if configured
            if (config.githubUserName && config.githubRepoName) {
                const githubLink = document.getElementById('github-repo-link');
                if (githubLink) {
                    githubLink.href = `https://github.com/${config.githubUserName}/${config.githubRepoName}`;
                    githubLink.style.display = '';
                }
            }
        } catch (error) {
            console.log('[Dashboard] Could not load config values for event log');
        }
        addEventToLog({
            level: 'SUCCESS',
            message: `Dashboard initialized (probe rate: ${probeRate}ms, idle timeout: ${idleTimeoutStr})`,
            timestamp: new Date(baseTime + 2).toISOString()
        }, true);

        // 3. Environment/SKU info
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

            addEventToLog({
                level: 'INFO',
                message: `Application is currently running on ${data.sku} SKU on worker ${data.computerName}`,
                timestamp: new Date(baseTime + 3).toISOString()
            }, true);
        } catch (error) {
            console.log('[Dashboard] Could not load environment info for event log');
        }

        // 4. Connected message
        addEventToLog({
            level: 'INFO',
            message: 'Connected to metrics hub',
            timestamp: new Date(baseTime + 4).toISOString()
        }, true);

        // 5. Wake from idle message (newest — appears at top of log)
        if (window._wokeFromIdle) {
            addEventToLog({
                level: 'INFO',
                message: 'App waking up from idle state. There may be gaps in diagnostics and logs.',
                timestamp: new Date(baseTime + 5).toISOString()
            }, true);
            window._wokeFromIdle = false;
        }

        renderEventLog();
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
        if (type.includes('FAILED_REQUESTS')) return 'failed-requests';
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
            'FAILED_REQUESTS': '❌ Failed Requests',
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
        loadActiveSimulations,
        copySimulationId
    };
})();

// Initialize dashboard when DOM is ready
// recordActivity fires an HTTP request first so the server wakes before the
// WebSocket connects — ensuring the first broadcast arrives with is_idle: false.
document.addEventListener('DOMContentLoaded', async function() {
    try {
        await SocketClient.recordActivity();
    } catch (e) {
        // Non-blocking — WS will retry on its own
    }
    Dashboard.init();
});
