package com.trendzy.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "product_clicks")
public class ProductClick {

    @Id
    private String id;
    private String userId; // null if anonymous
    private String trendId;
    private String source; // "underdog", "amazon", "flipkart"
    private String url;
    private Instant timestamp;

    public ProductClick() {}

    public ProductClick(String userId, String trendId, String source, String url, Instant timestamp) {
        this.userId = userId;
        this.trendId = trendId;
        this.source = source;
        this.url = url;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTrendId() {
        return trendId;
    }

    public void setTrendId(String trendId) {
        this.trendId = trendId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
