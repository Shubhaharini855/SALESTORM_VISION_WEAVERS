package com.syscrafters.salestorm.order.repository;

import com.syscrafters.salestorm.order.entity.SalesOrder;
import com.syscrafters.salestorm.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    Optional<SalesOrder> findByPayment_PaymentId(Long paymentId);
    long countByStatus(OrderStatus status);
}