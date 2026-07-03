package com.trendzy.ingestion.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendzy.ingestion.config.KafkaConfig;
import com.trendzy.ingestion.model.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes raw trend signal events to Apache Kafka.
 *
 * <p>The downstream Python AI workers consume these events to trigger
 * clustering and analysis pipelines.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaSignalProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a signal event to Kafka asynchronously.
     *
     * @param signalId The MongoDB ID of the newly ingested TrendSignal.
     * @param platform The platform it originated from.
     */
    public void publishSignalEvent(String signalId, Platform platform) {
        SignalEventPayload payload = new SignalEventPayload(signalId, platform);

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[KAFKA] Serialization error for signal {}: {}", signalId, e.getMessage());
            return;
        }

        log.debug("[KAFKA] Attempting to publish signal: id={} to topic={}", 
                signalId, KafkaConfig.TOPIC_RAW_SIGNALS);

        // Send asynchronously and handle the CompletableFuture result
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaConfig.TOPIC_RAW_SIGNALS, signalId, jsonPayload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[KAFKA] ✅ Signal published: id={} | partition={} | offset={}",
                        signalId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("[KAFKA] ❌ Failed to publish signal: id={} | error={} | cause={}",
                        signalId, ex.getMessage(), 
                        ex.getCause() != null ? ex.getCause().getMessage() : "N/A");
            }
        });
    }

    /**
     * Internal record representing the JSON payload sent to Kafka.
     * Uses Java 14+ Record for concise immutability.
     */
    public record SignalEventPayload(String signalId, Platform platform) {
    }
}
