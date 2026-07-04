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

    // Replicate the frontend's legacy $or logic for category matching
    @Query(value = "{ '$or': [ " +
           "{ 'category': { $regex: ?0, $options: 'i' } }, " +
           "{ 'aestheticId': { $regex: ?0, $options: 'i' } } ] }",
           sort = "{ 'trendScore': -1, 'lastUpdatedAt': -1 }")
    List<Trend> findByCategoryAndAestheticId(String categoryRegex);
}