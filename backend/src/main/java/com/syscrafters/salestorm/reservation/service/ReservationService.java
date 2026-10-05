package com.syscrafters.salestorm.reservation.service;

import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.product.entity.Product;
import com.syscrafters.salestorm.product.repository.ProductRepository;
import com.syscrafters.salestorm.reservation.dto.CreateReservationRequest;
import com.syscrafters.salestorm.reservation.dto.ReservationResponse;
import com.syscrafters.salestorm.reservation.entity.InventoryReservation;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;
import com.syscrafters.salestorm.reservation.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_VERSION_RETRIES = 100;
    private static final List<ReservationStatus> EXPIRABLE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.PAYMENT_PENDING);

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final EntityManager entityManager;
    private final int expirySeconds;

    public ReservationService(InventoryRepository inventoryRepository,
                              ProductRepository productRepository,
                              ReservationRepository reservationRepository,
                              EntityManager entityManager,
                              @Value("${salestorm.reservation.expiry-seconds:300}") int expirySeconds) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.entityManager = entityManager;
        this.expirySeconds = expirySeconds;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new ReservationException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 120 characters", 400);
        }

        InventoryReservation existing = reservationRepository
                .findByCustomerIdAndIdempotencyKey(request.getCustomerId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existingResponse(existing, request);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ReservationException("PRODUCT_NOT_FOUND", "Product was not found", 404));
        Inventory inventory = inventoryRepository.findByProduct_ProductId(request.getProductId())
                .orElseThrow(() -> new ReservationException("INVENTORY_NOT_FOUND", "Inventory was not found", 404));

        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            entityManager.refresh(inventory);
            if (inventory.getAvailableQuantity() < request.getQuantity()) {
                throw new ReservationException("INVENTORY_UNAVAILABLE", "Requested quantity is no longer available", 409);
            }

            int updated = inventoryRepository.reserveIfVersionMatches(
                    inventory.getInventoryId(), inventory.getVersion(), request.getQuantity());
            if (updated == 1) {
                InventoryReservation reservation = reservationRepository.save(new InventoryReservation(
                        request.getCustomerId(), product, request.getQuantity(),
                        LocalDateTime.now().plusSeconds(expirySeconds), idempotencyKey));
                log.info("Reservation created reservationId={} customerId={} productId={} quantity={}",
                        reservation.getReservationId(), request.getCustomerId(), request.getProductId(), request.getQuantity());
                return ReservationResponse.from(reservation, false);
            }

            InventoryReservation racedDuplicate = reservationRepository
                    .findByCustomerIdAndIdempotencyKey(request.getCustomerId(), idempotencyKey)
                    .orElse(null);
            if (racedDuplicate != null) {
                return existingResponse(racedDuplicate, request);
            }
        }

        throw new ReservationException("INVENTORY_BUSY", "Inventory is temporarily busy; retry the request with the same idempotency key", 503);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long reservationId) {
        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException("RESERVATION_NOT_FOUND", "Reservation was not found", 404));
        return ReservationResponse.from(reservation, false);
    }

    @Transactional
    public int expireReservations(LocalDateTime now) {
        List<InventoryReservation> expired = reservationRepository.findExpiredForUpdate(EXPIRABLE_STATUSES, now);
        int released = 0;
        for (InventoryReservation candidate : expired) {
            InventoryReservation reservation = reservationRepository.findByIdForUpdate(candidate.getReservationId())
                    .orElseThrow();
            if (!EXPIRABLE_STATUSES.contains(reservation.getStatus()) || reservation.getExpiresAt().isAfter(now)) {
                continue;
            }

            Inventory inventory = inventoryRepository.findByProduct_ProductId(reservation.getProduct().getProductId())
                    .orElseThrow(() -> new IllegalStateException("Inventory missing for reservation"));
            int updated = inventoryRepository.releaseReservedQuantity(inventory.getInventoryId(), reservation.getQuantity());
            if (updated != 1) {
                throw new IllegalStateException("Reserved inventory invariant violated for reservation " + reservation.getReservationId());
            }
            reservation.setStatus(ReservationStatus.EXPIRED);
            released++;
            log.info("Reservation expired and released reservationId={}", reservation.getReservationId());
        }
        return released;
    }

    @Transactional
    public ReservationResponse expireReservationNow(Long reservationId) {
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationException("RESERVATION_NOT_FOUND", "Reservation was not found", 404));
        if (EXPIRABLE_STATUSES.contains(reservation.getStatus())) {
            Inventory inventory = inventoryRepository.findByProduct_ProductId(reservation.getProduct().getProductId())
                    .orElseThrow(() -> new IllegalStateException("Inventory missing for reservation"));
            int updated = inventoryRepository.releaseReservedQuantity(inventory.getInventoryId(), reservation.getQuantity());
            if (updated != 1) {
                throw new IllegalStateException("Reserved inventory invariant violated for reservation " + reservationId);
            }
            reservation.setStatus(ReservationStatus.EXPIRED);
        }
        return ReservationResponse.from(reservation, false);
    }

    private ReservationResponse existingResponse(InventoryReservation existing, CreateReservationRequest request) {
        if (!existing.getProduct().getProductId().equals(request.getProductId()) ||
                !existing.getQuantity().equals(request.getQuantity())) {
            throw new ReservationException("IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used with a different request", 409);
        }
        return ReservationResponse.from(existing, true);
    }
}