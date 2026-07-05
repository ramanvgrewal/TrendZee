package com.trendzy.ingestion.controller;

import com.trendzy.ingestion.model.Trend;
import com.trendzy.ingestion.repository.TrendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/trends")
@RequiredArgsConstructor
@Slf4j
public class TrendController {

    private final TrendRepository trendRepository;

    @GetMapping
    public ResponseEntity<List<Trend>> getTrends(
            @RequestParam(defaultValue = "streetwear") String category) {
        
        log.info("[CTRL] Fetching trends for category: {}", category);
        
        // Normalize to uppercase to match the values stored by the AI worker
        String normalized = category.trim().toUpperCase();
        
        List<Trend> trends = trendRepository.findByCategory(normalized);
        
        // The frontend expects the JSON matching the raw document, which the Spring Data REST serialization covers nicely.
        return ResponseEntity.ok(trends);
    }
}
