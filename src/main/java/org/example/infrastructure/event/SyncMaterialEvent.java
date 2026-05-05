package org.example.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param eventType "MATERIAL_CREATED", "MATERIAL_UPDATED"
 */
public record SyncMaterialEvent(String eventType, String materialId, String name, String category) {
    @JsonCreator
    public SyncMaterialEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("materialId") String materialId,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category) {
        this.eventType = eventType;
        this.materialId = materialId;
        this.name = name;
        this.category = category;
    }
}

