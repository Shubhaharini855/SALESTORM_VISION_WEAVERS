package com.syscrafters.salestorm.inventory.controller;

import com.syscrafters.salestorm.inventory.dto.InventorySummaryResponse;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    public InventoryController(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping("/inventory/{productId}")
    public ResponseEntity<InventorySummaryResponse> getInventory(@PathVariable Long productId) {
        return inventoryRepository.findByProduct_ProductId(productId)
                .map(inventory -> ResponseEntity.ok(new InventorySummaryResponse(
                        inventory.getProduct().getProductId(),
                        inventory.getAvailableQuantity(),
                        inventory.getReservedQuantity(),
                        inventory.getSoldQuantity(),
                        inventory.getVersion())))
                .orElse(ResponseEntity.notFound().build());
    }
}
