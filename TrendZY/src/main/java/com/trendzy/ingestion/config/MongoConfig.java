package com.trendzy.ingestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"com.trendzy.ingestion.repository", "com.trendzy.security", "com.trendzy.analytics"})
@EnableMongoAuditing
public class MongoConfig {
}
