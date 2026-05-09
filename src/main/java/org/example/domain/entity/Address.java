package org.example.domain.entity;

import java.util.Objects;

public class Address {
    private String id;
    private String street;
    private String city;
    private String zipCode;
    private String number;
    private String state;
    private Double latitude;
    private Double longitude;
    private String enrichmentStatus;
    private String enrichmentSource;

    public Address() {
    }

    public Address(String id, String street, String city, String zipCode, Double latitude, Double longitude) {
        this.id = id;
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.enrichmentStatus = "PENDING";
    }

    public Address(String id, String street, String city, String zipCode, String number, String state, Double latitude, Double longitude, String enrichmentStatus, String enrichmentSource) {
        this.id = id;
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
        this.number = number;
        this.state = state;
        this.latitude = latitude;
        this.longitude = longitude;
        this.enrichmentStatus = enrichmentStatus != null ? enrichmentStatus : "PENDING";
        this.enrichmentSource = enrichmentSource;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getEnrichmentStatus() {
        return enrichmentStatus;
    }

    public void setEnrichmentStatus(String enrichmentStatus) {
        this.enrichmentStatus = enrichmentStatus;
    }

    public String getEnrichmentSource() {
        return enrichmentSource;
    }

    public void setEnrichmentSource(String enrichmentSource) {
        this.enrichmentSource = enrichmentSource;
    }

    public Double distance(Address other) {
        if (this.latitude == null || this.longitude == null || other.latitude == null || other.longitude == null) {
            return Double.MAX_VALUE;
        }
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double lon1 = Math.toRadians(this.longitude);
        double lon2 = Math.toRadians(other.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(id, address.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

