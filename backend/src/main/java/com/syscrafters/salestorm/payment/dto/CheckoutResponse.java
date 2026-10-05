package com.syscrafters.salestorm.payment.dto;

import com.syscrafters.salestorm.payment.entity.PaymentAttempt;
import com.syscrafters.salestorm.payment.entity.PaymentStatus;
import com.syscrafters.salestorm.order.entity.SalesOrder;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;

import java.math.BigDecimal;

public record CheckoutResponse(Long paymentId, Long reservationId, BigDecimal amount,
                               PaymentStatus paymentStatus, ReservationStatus reservationStatus,
                               boolean duplicate, Long orderId, String orderStatus) {
    public static CheckoutResponse from(PaymentAttempt attempt, boolean duplicate, SalesOrder order) {
        return new CheckoutResponse(attempt.getPaymentId(), attempt.getReservation().getReservationId(),
                attempt.getAmount(), attempt.getStatus(), attempt.getReservation().getStatus(), duplicate,
                order == null ? null : order.getOrderId(), order == null ? null : order.getStatus().name());
    }
}