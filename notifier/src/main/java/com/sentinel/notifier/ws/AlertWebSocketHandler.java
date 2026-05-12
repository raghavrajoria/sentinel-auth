package com.sentinel.notifier.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions
            = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("WebSocket client connected: " + session.getId()
                + " total=" + sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {
        sessions.remove(session);
        System.out.println("WebSocket client disconnected: " + session.getId()
                + " total=" + sessions.size());
    }

    public void broadcast(String message) {
        sessions.removeIf(s -> !s.isOpen());
        sessions.forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                System.err.println("Failed to send to session "
                        + session.getId() + ": " + e.getMessage());
            }
        });
    }
}