package org.example.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * @param eventType "COLLECTOR_CREATED", "COLLECTOR_UPDATED"
 */
public record SyncCollectorEvent(String eventType, String collectorId, String userId, String name,
                                 SyncAddressEvent address, List<String> acceptedMaterialIds, Double acceptanceRate) {
    @JsonCreator
    public SyncCollectorEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("collectorId") String collectorId,
            @JsonProperty("userId") String userId,
            @JsonProperty("name") String name,
            @JsonProperty("address") SyncAddressEvent address,
            @JsonProperty("acceptedMaterialIds") List<String> acceptedMaterialIds,
            @JsonProperty("acceptanceRate") Double acceptanceRate) {
        this.eventType = eventType;
        this.collectorId = collectorId;
        this.userId = userId;
        this.name = name;
        this.address = address;
        this.acceptedMaterialIds = acceptedMaterialIds;
        this.acceptanceRate = acceptanceRate;
    }
}

