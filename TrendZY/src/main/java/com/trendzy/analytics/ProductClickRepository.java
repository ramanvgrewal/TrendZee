package com.trendzy.analytics;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductClickRepository extends MongoRepository<ProductClick, String> {
}
