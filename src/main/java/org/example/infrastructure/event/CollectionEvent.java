package org.example.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CollectionEvent {
    private String eventType;
    private String requestId;
    private String generatorId;
    private String collectorId;
    private String status;
    private String timestamp;

    @JsonCreator
    public CollectionEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("generatorId") String generatorId,
            @JsonProperty("collectorId") String collectorId,
            @JsonProperty("status") String status,
            @JsonProperty("timestamp") String timestamp) {
        this.eventType = eventType;
        this.requestId = requestId;
        this.generatorId = generatorId;
        this.collectorId = collectorId;
        this.status = status;
        this.timestamp = timestamp;
    }

    public CollectionEvent() {}

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getGeneratorId() { return generatorId; }
    public void setGeneratorId(String generatorId) { this.generatorId = generatorId; }

    public String getCollectorId() { return collectorId; }
    public void setCollectorId(String collectorId) { this.collectorId = collectorId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}

