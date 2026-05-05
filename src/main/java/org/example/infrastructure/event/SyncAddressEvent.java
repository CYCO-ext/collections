package org.example.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SyncAddressEvent(String id, String street, String city, String zipCode, Double latitude,
                               Double longitude) {
    @JsonCreator
    public SyncAddressEvent(
            @JsonProperty("id") String id,
            @JsonProperty("street") String street,
            @JsonProperty("city") String city,
            @JsonProperty("zipCode") String zipCode,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude) {
        this.id = id;
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}

