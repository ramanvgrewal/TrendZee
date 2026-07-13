package com.trendzy.ingestion.repository;

import com.trendzy.ingestion.model.ArchivedTrend;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArchivedTrendRepository extends MongoRepository<ArchivedTrend, String> {
    
    List<ArchivedTrend> findByUserIdOrderByArchivedAtDesc(String userId);

    Optional<ArchivedTrend> findByUserIdAndOriginalTrendId(String userId, String originalTrendId);

    void deleteByUserIdAndOriginalTrendId(String userId, String originalTrendId);
}
