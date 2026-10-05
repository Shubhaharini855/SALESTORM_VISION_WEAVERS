package com.syscrafters.salestorm.product.dto;

import java.math.BigDecimal;

public class ProductResponse {
    private Long productId;
    private String name;
    private String description;
    private BigDecimal price;
    private boolean flashSale;

    public ProductResponse() {
    }

    public ProductResponse(Long productId, String name, String description, BigDecimal price, boolean flashSale) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.flashSale = flashSale;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isFlashSale() {
        return flashSale;
    }

    public void setFlashSale(boolean flashSale) {
        this.flashSale = flashSale;
    }
}
