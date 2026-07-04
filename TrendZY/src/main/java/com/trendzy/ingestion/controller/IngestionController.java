package com.trendzy.ingestion.controller;

import com.trendzy.ingestion.model.Category;
import com.trendzy.ingestion.service.SignalIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v2/ingestion")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final SignalIngestionService ingestionService;

    @Value("${INGEST_AUTH_CODE:}")
    private String ingestAuthCode;

    @PostMapping("/trigger")
    public ResponseEntity<?> triggerIngestion(
            @RequestParam(defaultValue = "STREETWEAR") String category,
            @RequestHeader(value = "X-Ingest-Code", defaultValue = "") String authCode) {

        // ── Validate Auth Code ──
        if (!ingestAuthCode.isEmpty() && !ingestAuthCode.equals(authCode)) {
            log.warn("[CTRL] 🚨 Unauthorized ingestion attempt with code: {}", authCode);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", "Invalid or missing X-Ingest-Code header"
            ));
        }

        // ── Validate category ──
        Category cat;
        try {
            cat = Category.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(Category.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            log.warn("[CTRL] ❌ Invalid category '{}'. Valid: {}", category, valid);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "BAD_REQUEST",
                    "message", "Invalid category '" + category + "'. Must be one of: " + valid
            ));
        }

        // 🛡️ RAM Protection: Check the lock BEFORE triggering
        if (ingestionService.isIngestionRunning()) {
            log.warn("[CTRL] 🚨 Rejecting request: Ingestion cycle is already active.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "CONFLICT",
                    "message", "An ingestion cycle is currently running. Please wait for it to finish to protect server memory."
            ));
        }

        log.info("[CTRL] ✅ Triggering V2 ingestion cycle — category={}", cat.name());
        ingestionService.runIngestionCycle(cat.name());

        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "category", cat.name(),
                "message", "Ingestion cycle started asynchronously for category: " + cat.name()
        ));
    }
}