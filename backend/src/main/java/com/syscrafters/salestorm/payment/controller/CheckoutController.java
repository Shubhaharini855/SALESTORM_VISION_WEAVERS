package com.syscrafters.salestorm.payment.controller;

import com.syscrafters.salestorm.payment.dto.CheckoutRequest;
import com.syscrafters.salestorm.payment.dto.CheckoutResponse;
import com.syscrafters.salestorm.payment.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/{reservationId}")
    public ResponseEntity<CheckoutResponse> checkout(
            @PathVariable Long reservationId,
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CheckoutResponse response = checkoutService.checkout(reservationId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}