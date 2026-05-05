package org.example.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param eventType "USER_CREATED", "USER_UPDATED"
 */
public record SyncUserEvent(String eventType, String userId, String name, String email) {
    @JsonCreator
    public SyncUserEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("userId") String userId,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email) {
        this.eventType = eventType;
        this.userId = userId;
        this.name = name;
        this.email = email;
    }
}

