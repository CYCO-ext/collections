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
    private String actorType;
    private String actorId;
    private String completionId;

    @JsonCreator
    public CollectionEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("generatorId") String generatorId,
            @JsonProperty("collectorId") String collectorId,
            @JsonProperty("status") String status,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("actorType") String actorType,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("completionId") String completionId) {
        this.eventType = eventType;
        this.requestId = requestId;
        this.generatorId = generatorId;
        this.collectorId = collectorId;
        this.status = status;
        this.timestamp = timestamp;
        this.actorType = actorType;
        this.actorId = actorId;
        this.completionId = completionId;
    }

    public CollectionEvent() {
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getGeneratorId() {
        return generatorId;
    }

    public void setGeneratorId(String generatorId) {
        this.generatorId = generatorId;
    }

    public String getCollectorId() {
        return collectorId;
    }

    public void setCollectorId(String collectorId) {
        this.collectorId = collectorId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getCompletionId() {
        return completionId;
    }

    public void setCompletionId(String completionId) {
        this.completionId = completionId;
    }
}
