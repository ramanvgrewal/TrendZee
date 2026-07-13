package com.trendzy.ingestion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "archived_trends")
public class ArchivedTrend {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String originalTrendId;

    private Trend trendSnapshot;

    @Builder.Default
    private LocalDateTime archivedAt = LocalDateTime.now();
}
