package com.syscrafters.salestorm.payment.service;

import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.order.entity.SalesOrder;
import com.syscrafters.salestorm.order.repository.SalesOrderRepository;
import com.syscrafters.salestorm.order.service.OrderService;
import com.syscrafters.salestorm.payment.dto.CheckoutRequest;
import com.syscrafters.salestorm.payment.dto.CheckoutResponse;
import com.syscrafters.salestorm.payment.entity.PaymentAttempt;
import com.syscrafters.salestorm.payment.entity.PaymentStatus;
import com.syscrafters.salestorm.payment.gateway.MockPaymentGateway;
import com.syscrafters.salestorm.payment.repository.PaymentAttemptRepository;
import com.syscrafters.salestorm.reservation.entity.InventoryReservation;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;
import com.syscrafters.salestorm.reservation.repository.ReservationRepository;
import com.syscrafters.salestorm.reservation.service.ReservationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CheckoutService {
    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final SalesOrderRepository orderRepository;
    private final OrderService orderService;
    private final MockPaymentGateway paymentGateway;

    public CheckoutService(ReservationRepository reservationRepository,
                           InventoryRepository inventoryRepository,
                           PaymentAttemptRepository paymentAttemptRepository,
                           MockPaymentGateway paymentGateway,
                           SalesOrderRepository orderRepository,
                           OrderService orderService) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentGateway = paymentGateway;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Transactional
    public CheckoutResponse checkout(Long reservationId, CheckoutRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        PaymentAttempt existing = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existingResponse(existing, reservationId, request);
        }

        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationException("RESERVATION_NOT_FOUND", "Reservation was not found", 404));

        // Recheck after taking the reservation lock so concurrent retries replay the first attempt.
        existing = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existingResponse(existing, reservationId, request);
        }

        if (reservation.getStatus() != ReservationStatus.RESERVED &&
                reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new ReservationException("RESERVATION_NOT_PAYABLE", "Reservation is not available for checkout", 409);
        }
        if (!reservation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ReservationException("RESERVATION_EXPIRED", "Reservation has expired", 409);
        }

        BigDecimal amount = reservation.getProduct().getPrice().multiply(BigDecimal.valueOf(reservation.getQuantity()));
        reservation.setStatus(ReservationStatus.PAYMENT_PENDING);
        PaymentStatus result = paymentGateway.charge(amount, request.mode());

        if (result == PaymentStatus.SUCCESS) {
            Inventory inventory = findInventory(reservation);
            int updated = inventoryRepository.confirmReservedQuantity(inventory.getInventoryId(), reservation.getQuantity());
            if (updated != 1) {
                throw new IllegalStateException("Reserved inventory invariant violated for reservation " + reservationId);
            }
            reservation.setStatus(ReservationStatus.CONFIRMED);
        } else if (result == PaymentStatus.FAILURE) {
            releaseReservation(reservation, reservationId);
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
        }

        PaymentAttempt attempt = paymentAttemptRepository.saveAndFlush(new PaymentAttempt(
                reservation, amount, request.mode(), result, idempotencyKey));
        SalesOrder order = result == PaymentStatus.SUCCESS
            ? orderService.createForPayment(attempt, request.simulateOrderServiceFailure())
            : null;
        return CheckoutResponse.from(attempt, false, order);
    }

    private CheckoutResponse existingResponse(PaymentAttempt existing, Long reservationId, CheckoutRequest request) {
        if (!existing.getReservation().getReservationId().equals(reservationId) || existing.getMode() != request.mode()) {
            throw new ReservationException("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key was already used with a different checkout request", 409);
        }
        SalesOrder order = existing.getStatus() == PaymentStatus.SUCCESS
            ? orderRepository.findByPayment_PaymentId(existing.getPaymentId()).orElse(null)
            : null;
        return CheckoutResponse.from(existing, true, order);
    }

    private void releaseReservation(InventoryReservation reservation, Long reservationId) {
        Inventory inventory = findInventory(reservation);
        int updated = inventoryRepository.releaseReservedQuantity(inventory.getInventoryId(), reservation.getQuantity());
        if (updated != 1) {
            throw new IllegalStateException("Reserved inventory invariant violated for reservation " + reservationId);
        }
    }

    private Inventory findInventory(InventoryReservation reservation) {
        return inventoryRepository.findByProduct_ProductId(reservation.getProduct().getProductId())
                .orElseThrow(() -> new IllegalStateException("Inventory missing for reservation"));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new ReservationException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 120 characters", 400);
        }
    }
}