package com.sentinel.decision_engine.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DecisionConsumer {

    @KafkaListener(topics = "transactions.decisions", groupId = "decision-engine-log")
    public void consume(ConsumerRecord<String, String> record) {
        System.out.println("KAFKA CONSUMED [" + record.topic() + "] key="
                + record.key() + " value=" + record.value());
    }
}