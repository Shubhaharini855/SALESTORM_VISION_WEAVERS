package com.syscrafters.salestorm.product.service;

import com.syscrafters.salestorm.inventory.dto.InventorySummaryResponse;
import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.product.dto.ProductResponse;
import com.syscrafters.salestorm.product.entity.Product;
import com.syscrafters.salestorm.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> getProductById(Long productId) {
        return productRepository.findById(productId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<InventorySummaryResponse> getInventorySummary(Long productId) {
        return inventoryRepository.findByProduct_ProductId(productId)
                .map(this::toInventorySummary);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isFlashSale()
        );
    }

    private InventorySummaryResponse toInventorySummary(Inventory inventory) {
        return new InventorySummaryResponse(
                inventory.getProduct().getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getSoldQuantity(),
                inventory.getVersion()
        );
    }
}
