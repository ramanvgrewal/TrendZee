package com.trendzy.ingestion.repository;

import com.trendzy.ingestion.model.Trend;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrendRepository extends MongoRepository<Trend, String> {

    @Query("{ '$or': [{'enrichmentStatus': 'PENDING'}, {'enrichmentStatus': null}], 'active': true }")
    List<Trend> findPendingEnrichment();

    // Exact match on category field (values are stored uppercase, e.g. "STREETWEAR")
    @Query(value = "{ 'category': ?0 }",
           sort = "{ 'trendScore': -1, 'lastUpdatedAt': -1 }")
    List<Trend> findByCategory(String category);
}