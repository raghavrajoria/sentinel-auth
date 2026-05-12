package com.sentinel.notifier.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.notifier.ws.AlertWebSocketHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AlertConsumer {

    private final AlertWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public AlertConsumer(AlertWebSocketHandler webSocketHandler,
                         ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transactions.decisions", groupId = "notifier-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String decision = node.get("decision").asText();

            if ("BLOCK".equals(decision)) {
                String alert = objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "type", "BLOCK_ALERT",
                                "tx_id", node.get("txId").asText(),
                                "user_id", node.get("userId").asText(),
                                "risk_score", node.get("riskScore").asInt(),
                                "rules_fired", node.get("rulesFired"),
                                "timestamp", Instant.now().toString()
                        )
                );
                webSocketHandler.broadcast(alert);
                System.out.println("BLOCK alert broadcast: "
                        + node.get("txId").asText());
            }

        } catch (Exception e) {
            System.err.println("Failed to process alert: " + e.getMessage());
        }
    }
}