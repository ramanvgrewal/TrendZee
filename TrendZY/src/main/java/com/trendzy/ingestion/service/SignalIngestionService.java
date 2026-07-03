package com.trendzy.ingestion.service;

import com.microsoft.playwright.Playwright;
import com.trendzy.ingestion.model.TrendSignal;
import com.trendzy.ingestion.repository.TrendSignalRepository;
import com.trendzy.ingestion.scraper.instagram.InstagramExploreClient;
import com.trendzy.ingestion.kafka.KafkaSignalProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignalIngestionService {

    private final InstagramExploreClient instagramExploreClient;
    private final TrendSignalRepository trendSignalRepository;
    private final KafkaSignalProducer kafkaSignalProducer;

    // The un-hackable lock to protect your RAM
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Let the controller check if it's currently busy
    public boolean isIngestionRunning() {
        return isRunning.get();
    }

    @Async
    public void runIngestionCycle(String category) {
        // Compare-and-Set: If it's false, set it to true and proceed. If it's already true, return.
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("[INGESTION] 🚨 Cycle already running! Ignoring duplicate background execution.");
            return;
        }

        log.info("[INGESTION] ════════ STARTING INGESTION CYCLE — category={} ════════", category);

        List<TrendSignal> allSignals = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            // Instagram Extraction
            try {
                allSignals.addAll(instagramExploreClient.fetchExploreSignals(playwright, category));
            } catch (Exception e) {
                log.error("[INGESTION] Instagram scraper failed: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("[INGESTION] Fatal Playwright error: {}", e.getMessage(), e);
        } finally {
            // Stamp category on every signal before saving
            for (TrendSignal signal : allSignals) {
                signal.setCategory(category);
            }

            // Save data to Mongo
            int newSignals = processAndSaveSignals(allSignals);
            log.info("[INGESTION] CYCLE COMPLETE | category={} | New Signals: {} | Total Scraped: {}",
                    category, newSignals, allSignals.size());

            // 🔓 RELEASING THE LOCK - guaranteed to happen even if Playwright throws an exception
            isRunning.set(false);
            log.info("[INGESTION] 🔓 Lock released. Ready for next cycle.");
        }
    }

    private int processAndSaveSignals(List<TrendSignal> allSignals) {
        int newSignals = 0;
        for (TrendSignal signal : allSignals) {
            // The DB unique index handles race conditions, but we do a quick app-level check first to avoid throwing unnecessary exception logs
            if (!trendSignalRepository.existsBySourceUrl(signal.getSourceUrl())) {
                try {
                    TrendSignal saved = trendSignalRepository.save(signal);
                    kafkaSignalProducer.publishSignalEvent(saved.getId(), saved.getPlatform());
                    newSignals++;
                } catch (Exception e) {
                    log.error("[INGESTION] Failed to process signal {}: {}", signal.getSourceUrl(), e.getMessage());
                }
            }
        }
        return newSignals;
    }
}