package com.syscrafters.salestorm.reservation.entity;

import com.syscrafters.salestorm.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_customer_idempotency",
                columnNames = {"customer_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_reservation_expiry_status", columnList = "status, expires_at"),
                @Index(name = "idx_reservation_customer", columnList = "customer_id")
        })
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(Long customerId, Product product, Integer quantity,
                                LocalDateTime expiresAt, String idempotencyKey) {
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey;
        this.status = ReservationStatus.RESERVED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getReservationId() { return reservationId; }
    public Long getCustomerId() { return customerId; }
    public Product getProduct() { return product; }
    public Integer getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setStatus(ReservationStatus status) { this.status = status; }
}