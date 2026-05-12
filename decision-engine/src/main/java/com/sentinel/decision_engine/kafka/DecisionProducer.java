package com.sentinel.decision_engine.kafka;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.decision_engine.model.DecisionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DecisionProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DecisionProducer(KafkaTemplate<String, String> kafkaTemplate,ObjectMapper objectMapper){
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishDecision(DecisionEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("transactions.decisions", event.getTxId(), json);
            System.out.println("Published decision to Kafka: " + event.getTxId()
                    + " -> " + event.getDecision());
        } catch (Exception e) {
            System.err.println("Failed to publish to Kafka: " + e.getMessage());
        }
    }
}
