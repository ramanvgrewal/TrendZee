package com.trendzy.ingestion.controller;

import com.trendzy.ingestion.model.Trend;
import com.trendzy.ingestion.model.TrendSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
// haha i am the admin.
    private final MongoTemplate mongoTemplate;

    @DeleteMapping("/trends/no-underdog")
    public ResponseEntity<?> cleanTrendsWithoutUnderdog() {
        log.info("[ADMIN] Cleaning trends without underdog products...");
        
        Query query = new Query(
                new Criteria().orOperator(
                        Criteria.where("signalProducts.underdog").exists(false),
                        Criteria.where("signalProducts.underdog").is(null)
                )
        );
        
        long deletedCount = mongoTemplate.remove(query, Trend.class).getDeletedCount();
        log.info("[ADMIN] Deleted {} trends without underdog products.", deletedCount);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "deletedCount", deletedCount,
                "message", "Cleared trends with missing underdog data."
        ));
    }

    @DeleteMapping("/signals/orphans")
    public ResponseEntity<?> cleanOrphanSignals() {
        log.info("[ADMIN] Cleaning orphan signals...");
        
        // Find all signal IDs currently attached to any trend
        Query allTrendsQuery = new Query();
        allTrendsQuery.fields().include("supportingSignalIds");
        
        List<Trend> allTrends = mongoTemplate.find(allTrendsQuery, Trend.class);
        
        Set<String> activeSignalIds = allTrends.stream()
                .filter(t -> t.getSupportingSignalIds() != null)
                .flatMap(t -> t.getSupportingSignalIds().stream())
                .collect(Collectors.toSet());
        
        // Delete signals whose ID is not in the active set
        Query orphanQuery = new Query(Criteria.where("_id").nin(activeSignalIds));
        long deletedCount = mongoTemplate.remove(orphanQuery, TrendSignal.class).getDeletedCount();
        
        log.info("[ADMIN] Deleted {} orphan signals.", deletedCount);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "deletedCount", deletedCount,
                "message", "Cleared orphan signals not attached to any trend."
        ));
    }
}
