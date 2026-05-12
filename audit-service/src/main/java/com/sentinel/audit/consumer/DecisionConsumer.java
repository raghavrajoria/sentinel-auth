package com.sentinel.audit.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.audit.model.Decision;
import com.sentinel.audit.repository.DecisionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DecisionConsumer {

    private final DecisionRepository decisionRepository;
    private final ObjectMapper objectMapper;

    public DecisionConsumer(DecisionRepository decisionRepository,
                            ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transactions.decisions", groupId = "audit-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());

            Decision decision = Decision.builder()
                    .txId(node.get("txId").asText())
                    .userId(node.get("userId").asText())
                    .decision(node.get("decision").asText())
                    .riskScore(node.get("riskScore").asInt())
                    .mlRiskScore(node.get("mlRiskScore").asInt())
                    .rulesFired(node.get("rulesFired").toString())
                    .latencyMs(node.get("latencyMs").asLong())
                    .mlFallback(node.get("mlFallback").asBoolean())
                    .createdAt(Instant.parse(node.get("timestamp").asText()))
                    .build();

            decisionRepository.save(decision);
            System.out.println("Saved decision to Postgres: " + decision.getTxId()
                    + " -> " + decision.getDecision());

        } catch (Exception e) {
            System.err.println("Failed to process Kafka message: " + e.getMessage());
        }
    }
}
