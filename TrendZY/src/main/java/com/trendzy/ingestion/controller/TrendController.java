package com.trendzy.ingestion.controller;

import com.trendzy.ingestion.model.Trend;
import com.trendzy.ingestion.repository.TrendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/trends")
@RequiredArgsConstructor
@Slf4j
public class TrendController {

    private final TrendRepository trendRepository;

    @GetMapping
    public ResponseEntity<Slice<Trend>> getTrends(
            @RequestParam(defaultValue = "streetwear") String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        log.info("[CTRL] Fetching trends for category: {}, page: {}, size: {}",
                category, page, size);

        // Normalize to uppercase to match values stored in MongoDB
        String normalized = category.trim().toUpperCase();

        Pageable pageable = PageRequest.of(page, size);

        Slice<Trend> trends = trendRepository.findByCategory(normalized, pageable);

        return ResponseEntity.ok(trends);
    }
}