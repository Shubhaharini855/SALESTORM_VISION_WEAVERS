package com.syscrafters.salestorm.inventory.dto;

public class InventorySummaryResponse {
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;
    private Long version;

    public InventorySummaryResponse() {
    }

    public InventorySummaryResponse(Long productId, Integer availableQuantity, Integer reservedQuantity, Integer soldQuantity, Long version) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.soldQuantity = soldQuantity;
        this.version = version;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(Integer soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
