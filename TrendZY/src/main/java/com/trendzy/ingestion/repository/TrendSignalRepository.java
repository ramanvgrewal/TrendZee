package com.trendzy.ingestion.repository;

import com.trendzy.ingestion.model.TrendSignal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for {@link TrendSignal} documents.
 */
@Repository
public interface TrendSignalRepository extends MongoRepository<TrendSignal, String> {

    /**
     * Checks whether a signal with the given source URL already exists.
     * Called by the ingestion service and scraper to prevent duplicate ingestion.
     */
    boolean existsBySourceUrl(String sourceUrl);
}
