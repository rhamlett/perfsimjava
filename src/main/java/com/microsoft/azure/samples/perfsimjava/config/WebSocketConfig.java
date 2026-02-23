package com.microsoft.azure.samples.perfsimjava.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * =============================================================================
 * WEBSOCKET CONFIGURATION — Real-Time Communication Setup
 * =============================================================================
 *
 * PURPOSE:
 *   Configures Spring WebSocket with STOMP protocol for real-time dashboard
 *   updates. All metrics, events, and simulation status changes are pushed
 *   to connected clients via WebSocket.
 *
 * ARCHITECTURE:
 *   - /ws endpoint: WebSocket/SockJS connection point
 *   - /topic/* destinations: Broadcast channels for metrics, events, etc.
 *   - /app/* destinations: Client-to-server message prefixes
 *
 * MESSAGE FLOW:
 *   Server → /topic/metrics → All subscribed clients (broadcast)
 *   Server → /topic/events → All subscribed clients (broadcast)
 *   Server → /topic/simulations → All subscribed clients (broadcast)
 *   Server → /topic/probe → All subscribed clients (latency probe results)
 *
 * PORTING NOTES:
 *   - Node.js Socket.IO: Similar concept with io.emit() for broadcast
 *   - Python Flask-SocketIO: emit() with broadcast=True
 *   - PHP Ratchet: WsServer with topic broadcast
 *   - C# SignalR: Clients.All.SendAsync() for broadcast
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple in-memory message broker for broadcasting
        // Messages sent to /topic/* will be broadcast to all subscribers
        registry.enableSimpleBroker("/topic");

        // Prefix for client-to-server messages (if needed)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the WebSocket endpoint
        // SockJS is enabled as fallback for browsers that don't support WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Also register a raw WebSocket endpoint without SockJS
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*");
    }
}
