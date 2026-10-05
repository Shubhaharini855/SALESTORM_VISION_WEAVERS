package com.syscrafters.salestorm.order.dto;

import com.syscrafters.salestorm.order.entity.SalesOrder;
import com.syscrafters.salestorm.order.entity.OrderStatus;

import java.math.BigDecimal;

public record OrderResponse(Long orderId, Long paymentId, Long reservationId,
                            BigDecimal amount, OrderStatus status) {
    public static OrderResponse from(SalesOrder order) {
        return new OrderResponse(order.getOrderId(), order.getPayment().getPaymentId(),
                order.getPayment().getReservation().getReservationId(),
                order.getPayment().getAmount(), order.getStatus());
    }
}