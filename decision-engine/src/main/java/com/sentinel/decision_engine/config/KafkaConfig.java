package com.sentinel.decision_engine.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic decisionsTopic() {
        return TopicBuilder.name("transactions.decisions")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rawTopic() {
        return TopicBuilder.name("transactions.raw")
                .partitions(3)
                .replicas(1)
                .build();
    }
}