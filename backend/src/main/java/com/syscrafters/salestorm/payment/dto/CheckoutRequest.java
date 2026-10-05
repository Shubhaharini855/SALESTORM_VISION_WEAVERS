package com.syscrafters.salestorm.payment.dto;

import com.syscrafters.salestorm.payment.entity.PaymentMode;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull PaymentMode mode, boolean simulateOrderServiceFailure) {
	public CheckoutRequest(PaymentMode mode) {
		this(mode, false);
	}
}