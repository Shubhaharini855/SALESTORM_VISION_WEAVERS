package com.syscrafters.salestorm.reservation.entity;

public enum ReservationStatus {
    RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    PAYMENT_FAILED,
    EXPIRED,
    RELEASED,
    TIMEOUT
}
