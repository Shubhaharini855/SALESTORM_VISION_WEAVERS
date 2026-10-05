package com.syscrafters.salestorm.payment.gateway;

import com.syscrafters.salestorm.payment.entity.PaymentMode;
import com.syscrafters.salestorm.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MockPaymentGateway {
    public PaymentStatus charge(BigDecimal amount, PaymentMode mode) {
        return switch (mode) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case FAILURE -> PaymentStatus.FAILURE;
            case TIMEOUT -> PaymentStatus.TIMEOUT;
        };
    }
}