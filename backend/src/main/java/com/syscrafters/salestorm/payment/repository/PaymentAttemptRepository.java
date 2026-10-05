package com.syscrafters.salestorm.payment.repository;

import com.syscrafters.salestorm.payment.entity.PaymentAttempt;
import com.syscrafters.salestorm.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);
    long countByStatus(PaymentStatus status);
}