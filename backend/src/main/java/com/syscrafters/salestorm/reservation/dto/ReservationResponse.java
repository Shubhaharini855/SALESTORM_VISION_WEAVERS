package com.syscrafters.salestorm.reservation.dto;

import com.syscrafters.salestorm.reservation.entity.InventoryReservation;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(Long reservationId, Long customerId, Long productId,
                                  Integer quantity, ReservationStatus status,
                                  LocalDateTime expiresAt, boolean duplicate) {
    public static ReservationResponse from(InventoryReservation reservation, boolean duplicate) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                reservation.getProduct().getProductId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                duplicate
        );
    }
}