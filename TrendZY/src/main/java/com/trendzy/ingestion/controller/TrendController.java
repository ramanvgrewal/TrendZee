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
        
        // Build regex pattern matching the frontend behavior: ^(streetwear|STREETWEAR|Streetwear)$
        String pattern = "^(" + category + "|" + category.toUpperCase() + "|" + 
                         category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase() + ")$";
        
        List<Trend> trends = trendRepository.findByCategoryAndAestheticId(pattern);
        
        // The frontend expects the JSON matching the raw document, which the Spring Data REST serialization covers nicely.
        return ResponseEntity.ok(trends);
    }
}
