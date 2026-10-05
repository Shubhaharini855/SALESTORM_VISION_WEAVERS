package com.syscrafters.salestorm.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.syscrafters.salestorm.inventory.entity.Inventory;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProduct_ProductId(Long productId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE inventory SET available_quantity = available_quantity - :quantity, " +
        "reserved_quantity = reserved_quantity + :quantity, version = version + 1, " +
        "updated_at = CURRENT_TIMESTAMP WHERE inventory_id = :inventoryId " +
        "AND version = :version AND available_quantity >= :quantity", nativeQuery = true)
    int reserveIfVersionMatches(@Param("inventoryId") Long inventoryId,
                @Param("version") Long version,
                @Param("quantity") int quantity);

    @Modifying
    @Transactional
    @Query(value = "UPDATE inventory SET available_quantity = available_quantity + :quantity, " +
        "reserved_quantity = reserved_quantity - :quantity, version = version + 1, " +
        "updated_at = CURRENT_TIMESTAMP WHERE inventory_id = :inventoryId " +
        "AND reserved_quantity >= :quantity", nativeQuery = true)
    int releaseReservedQuantity(@Param("inventoryId") Long inventoryId,
                @Param("quantity") int quantity);

    @Modifying
    @Transactional
    @Query(value = "UPDATE inventory SET reserved_quantity = reserved_quantity - :quantity, " +
        "sold_quantity = sold_quantity + :quantity, version = version + 1, " +
        "updated_at = CURRENT_TIMESTAMP WHERE inventory_id = :inventoryId " +
        "AND reserved_quantity >= :quantity", nativeQuery = true)
    int confirmReservedQuantity(@Param("inventoryId") Long inventoryId,
                @Param("quantity") int quantity);
}
