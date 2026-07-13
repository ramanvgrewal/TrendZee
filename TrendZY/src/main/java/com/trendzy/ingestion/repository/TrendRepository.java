package com.trendzy.ingestion.repository;

import com.trendzy.ingestion.model.Trend;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrendRepository extends MongoRepository<Trend, String> {

    @Query("{ '$or': [{'enrichmentStatus': 'PENDING'}, {'enrichmentStatus': null}], 'active': true }")
    List<Trend> findPendingEnrichment();

    // Infinite scroll using Slice
    @Query(value = "{ 'category': ?0, 'enrichmentStatus': 'COMPLETED' }",
            sort = "{ 'trendScore': -1, 'lastUpdatedAt': -1 }")
    Slice<Trend> findByCategory(String category, Pageable pageable);
    // Delete trends by category with score less than a threshold
    long deleteByCategoryAndTrendScoreLessThan(String category, double maxScore);

    // Delete trends that haven't been updated since a specific date
    long deleteByLastUpdatedAtBefore(java.time.LocalDateTime date);
}