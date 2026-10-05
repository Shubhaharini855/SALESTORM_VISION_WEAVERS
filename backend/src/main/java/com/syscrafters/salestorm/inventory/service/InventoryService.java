package com.syscrafters.salestorm.inventory.service;

import com.syscrafters.salestorm.inventory.dto.InventorySummaryResponse;
import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse getSummary(Long productId) {
        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found for product: " + productId));

        return new InventorySummaryResponse(
                inventory.getProduct().getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getSoldQuantity(),
                inventory.getVersion()
        );
    }
}
