package org.example.domain.entity;

import java.util.List;
import java.util.Objects;

public class Collector {
    private String id;
    private String userId;
    private String name;
    private Address address;
    private List<String> acceptedMaterialIds;
    private Double acceptanceRate;

    public Collector() {
    }

    public Collector(String id, String userId, String name, Address address, List<String> acceptedMaterialIds, Double acceptanceRate) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.address = address;
        this.acceptedMaterialIds = acceptedMaterialIds;
        this.acceptanceRate = acceptanceRate;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public List<String> getAcceptedMaterialIds() {
        return acceptedMaterialIds;
    }

    public void setAcceptedMaterialIds(List<String> acceptedMaterialIds) {
        this.acceptedMaterialIds = acceptedMaterialIds;
    }

    public Double getAcceptanceRate() {
        return acceptanceRate;
    }

    public void setAcceptanceRate(Double acceptanceRate) {
        this.acceptanceRate = acceptanceRate;
    }

    public boolean acceptsMaterial(String materialId) {
        return acceptedMaterialIds != null && acceptedMaterialIds.contains(materialId);
    }

    public boolean acceptsAllMaterials(List<String> materialIds) {
        if (acceptedMaterialIds == null) return false;
        return acceptedMaterialIds.containsAll(materialIds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Collector collector = (Collector) o;
        return Objects.equals(id, collector.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

