package com.syscrafters.salestorm.order.service;

import com.syscrafters.salestorm.order.dto.OrderResponse;
import com.syscrafters.salestorm.order.entity.OrderStatus;
import com.syscrafters.salestorm.order.entity.SalesOrder;
import com.syscrafters.salestorm.order.repository.SalesOrderRepository;
import com.syscrafters.salestorm.payment.entity.PaymentAttempt;
import com.syscrafters.salestorm.reservation.service.ReservationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final SalesOrderRepository orderRepository;

    public OrderService(SalesOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public SalesOrder createForPayment(PaymentAttempt payment, boolean simulateServiceFailure) {
        return orderRepository.findByPayment_PaymentId(payment.getPaymentId())
                .orElseGet(() -> orderRepository.save(new SalesOrder(payment,
                        simulateServiceFailure ? OrderStatus.PENDING_RECOVERY : OrderStatus.CONFIRMED)));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId) {
        return OrderResponse.from(orderRepository.findById(orderId)
                .orElseThrow(() -> new ReservationException("ORDER_NOT_FOUND", "Order was not found", 404)));
    }

    @Transactional
    public OrderResponse recover(Long orderId) {
        SalesOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ReservationException("ORDER_NOT_FOUND", "Order was not found", 404));
        order.setStatus(OrderStatus.CONFIRMED);
        return OrderResponse.from(order);
    }
}