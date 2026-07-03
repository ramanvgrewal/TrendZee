package com.trendzy.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuration for Apache Kafka.
 * 
 * <p>Ensures that the required topics exist with the correct partitioning
 * and replication factors before the producer attempts to publish events.
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_RAW_SIGNALS = "raw-signals-topic";

    /**
     * Defines the topic where raw ingested signals are published.
     * 
     * <p>We use 3 partitions to allow for concurrent processing by multiple
     * Python AI workers (Consumer Group).
     */
    @Bean
    public NewTopic rawSignalsTopic() {
        return TopicBuilder.name(TOPIC_RAW_SIGNALS)
                .partitions(3)
                .replicas(1) // Increase to 3 in production environments
                .build();
    }
}
