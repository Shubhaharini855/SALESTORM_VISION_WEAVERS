package com.syscrafters.salestorm.demo;

import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.product.entity.Product;
import com.syscrafters.salestorm.product.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final int initialStock;

    public DemoDataInitializer(ProductRepository productRepository, InventoryRepository inventoryRepository,
                               @Value("${salestorm.demo.initial-stock:100}") int initialStock) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.initialStock = initialStock;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            Product product = new Product("Product X", "Flash sale demo product", new BigDecimal("19.99"));
            product = productRepository.save(product);

            Inventory inventory = new Inventory(product, initialStock);
            inventoryRepository.save(inventory);
        }
    }
}
