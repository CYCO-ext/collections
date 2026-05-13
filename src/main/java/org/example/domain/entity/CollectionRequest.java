package org.example.domain.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CollectionRequest {
    public enum Status {
        PENDING, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED
    }

    private String id;
    private String generatorId;
    private String addressId;
    private List<String> materialIds;
    private Double weight;
    private Status status;
    private String selectedCollectorId;
    private Boolean generatorConfirmed;
    private Boolean collectorConfirmed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CollectionRequest() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.PENDING;
        this.generatorConfirmed = false;
        this.collectorConfirmed = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public CollectionRequest(String generatorId, String addressId, List<String> materialIds, Double weight) {
        this();
        this.generatorId = generatorId;
        this.addressId = addressId;
        this.materialIds = materialIds;
        this.weight = weight;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGeneratorId() {
        return generatorId;
    }

    public void setGeneratorId(String generatorId) {
        this.generatorId = generatorId;
    }

    public String getAddressId() {
        return addressId;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    public List<String> getMaterialIds() {
        return materialIds;
    }

    public void setMaterialIds(List<String> materialIds) {
        this.materialIds = materialIds;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getSelectedCollectorId() {
        return selectedCollectorId;
    }

    public void setSelectedCollectorId(String selectedCollectorId) {
        this.selectedCollectorId = selectedCollectorId;
    }

    public Boolean getGeneratorConfirmed() {
        return generatorConfirmed;
    }

    public void setGeneratorConfirmed(Boolean generatorConfirmed) {
        this.generatorConfirmed = generatorConfirmed;
        this.updatedAt = LocalDateTime.now();
    }

    public Boolean getCollectorConfirmed() {
        return collectorConfirmed;
    }

    public void setCollectorConfirmed(Boolean collectorConfirmed) {
        this.collectorConfirmed = collectorConfirmed;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean canStartCollection() {
        return Status.PENDING.equals(status) && selectedCollectorId != null;
    }

    public boolean canMarkCompleted() {
        return Status.IN_PROGRESS.equals(status) &&
                Boolean.TRUE.equals(generatorConfirmed) &&
                Boolean.TRUE.equals(collectorConfirmed);
    }

    public boolean canCancel() {
        return Status.PENDING.equals(status) || Status.IN_PROGRESS.equals(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectionRequest that = (CollectionRequest) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

