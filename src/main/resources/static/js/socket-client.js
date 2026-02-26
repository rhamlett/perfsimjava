/* =============================================================================
   Performance Problem Simulator - Java Blessed Image
   WebSocket Client (STOMP over SockJS)
   ============================================================================= */

const SocketClient = (function() {
    'use strict';

    // Connection state
    let stompClient = null;
    let connected = false;
    let reconnectAttempts = 0;
    const maxReconnectAttempts = 10;
    const reconnectDelay = 3000;

    // Callbacks
    const callbacks = {
        onConnect: [],
        onDisconnect: [],
        onMetrics: [],
        onProbeResult: [],
        onEvent: [],
        onSimulationUpdate: []
    };

    /**
     * Registers a callback for an event type
     */
    function on(event, callback) {
        if (callbacks[event]) {
            callbacks[event].push(callback);
        }
    }

    /**
     * Triggers all callbacks for an event type
     */
    function trigger(event, data) {
        if (callbacks[event]) {
            callbacks[event].forEach(cb => {
                try {
                    cb(data);
                } catch (e) {
                    console.error(`[SocketClient] Callback error for ${event}:`, e);
                }
            });
        }
    }

    /**
     * Updates the connection status UI
     */
    function updateConnectionStatus(status) {
        const statusEl = document.getElementById('connection-status');
        if (statusEl) {
            // Remove all status classes
            statusEl.classList.remove('status-connected', 'status-disconnected', 'status-reconnecting');
            
            switch (status) {
                case 'connected':
                    statusEl.classList.add('status-connected');
                    statusEl.textContent = 'Connected';
                    break;
                case 'disconnected':
                    statusEl.classList.add('status-disconnected');
                    statusEl.textContent = 'Disconnected';
                    break;
                case 'connecting':
                    statusEl.classList.add('status-reconnecting');
                    statusEl.textContent = 'Connecting...';
                    break;
            }
        }
    }

    /**
     * Connects to the WebSocket server
     */
    function connect() {
        updateConnectionStatus('connecting');

        // Create SockJS connection
        const socket = new SockJS('/ws');
        
        // Create STOMP client over SockJS
        stompClient = new StompJs.Client({
            webSocketFactory: () => socket,
            debug: function(str) {
                // Uncomment for debugging
                // console.log('[STOMP] ' + str);
            },
            reconnectDelay: reconnectDelay,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000
        });

        // Connection established
        stompClient.onConnect = function(frame) {
            console.log('[SocketClient] Connected to WebSocket server');
            connected = true;
            reconnectAttempts = 0;
            updateConnectionStatus('connected');
            trigger('onConnect', frame);

            // Subscribe to topics
            subscribeToTopics();
        };

        // Connection error
        stompClient.onStompError = function(frame) {
            console.error('[SocketClient] STOMP error:', frame.headers['message']);
            console.error('[SocketClient] Error details:', frame.body);
        };

        // WebSocket close
        stompClient.onWebSocketClose = function(event) {
            console.log('[SocketClient] WebSocket closed');
            connected = false;
            updateConnectionStatus('disconnected');
            trigger('onDisconnect', event);
            
            // Attempt reconnection
            if (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++;
                console.log(`[SocketClient] Reconnecting... (attempt ${reconnectAttempts})`);
                setTimeout(connect, reconnectDelay);
            }
        };

        // Activate the STOMP client
        stompClient.activate();
    }

    /**
     * Subscribes to all required STOMP topics
     */
    function subscribeToTopics() {
        // Subscribe to metrics updates (every 250ms)
        stompClient.subscribe('/topic/metrics', function(message) {
            try {
                const metrics = JSON.parse(message.body);
                trigger('onMetrics', metrics);
            } catch (e) {
                console.error('[SocketClient] Error parsing metrics:', e);
            }
        });

        // Subscribe to probe results (every 100ms)
        stompClient.subscribe('/topic/probe', function(message) {
            try {
                const probeResult = JSON.parse(message.body);
                trigger('onProbeResult', probeResult);
            } catch (e) {
                console.error('[SocketClient] Error parsing probe result:', e);
            }
        });

        // Subscribe to event log
        stompClient.subscribe('/topic/events', function(message) {
            try {
                const event = JSON.parse(message.body);
                trigger('onEvent', event);
            } catch (e) {
                console.error('[SocketClient] Error parsing event:', e);
            }
        });

        // Subscribe to simulation updates
        stompClient.subscribe('/topic/simulations', function(message) {
            try {
                const update = JSON.parse(message.body);
                trigger('onSimulationUpdate', update);
            } catch (e) {
                console.error('[SocketClient] Error parsing simulation update:', e);
            }
        });

        console.log('[SocketClient] Subscribed to all topics');
    }

    /**
     * Disconnects from the WebSocket server
     */
    function disconnect() {
        if (stompClient && stompClient.active) {
            stompClient.deactivate();
            connected = false;
            updateConnectionStatus('disconnected');
            console.log('[SocketClient] Disconnected');
        }
    }

    /**
     * Sends a message to a STOMP destination
     */
    function send(destination, body) {
        if (stompClient && connected) {
            stompClient.publish({
                destination: destination,
                body: JSON.stringify(body)
            });
        } else {
            console.warn('[SocketClient] Cannot send - not connected');
        }
    }

    /**
     * Checks if connected
     */
    function isConnected() {
        return connected;
    }

    // Public API
    return {
        connect,
        disconnect,
        send,
        on,
        isConnected
    };
})();
