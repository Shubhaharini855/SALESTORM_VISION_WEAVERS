package com.syscrafters.salestorm.reservation;

import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.product.entity.Product;
import com.syscrafters.salestorm.product.repository.ProductRepository;
import com.syscrafters.salestorm.reservation.dto.CreateReservationRequest;
import com.syscrafters.salestorm.reservation.dto.ReservationResponse;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;
import com.syscrafters.salestorm.reservation.repository.ReservationRepository;
import com.syscrafters.salestorm.reservation.service.ReservationService;
import com.syscrafters.salestorm.reservation.service.ReservationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:salestorm-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "salestorm.reservation.cleanup-interval-ms=3600000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationServiceConcurrencyTest {
    private static final int INITIAL_STOCK = 100;
    private static final int REQUEST_COUNT = 10_000;

    @Autowired private ReservationService reservationService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long productId;

    @BeforeEach
    @Transactional
    void resetInventory() {
        reservationRepository.deleteAll();
        Product product = productRepository.findAll().stream().findFirst().orElseGet(() ->
                productRepository.save(new Product("Product X", "Flash sale test product", new java.math.BigDecimal("19.99"))));
        productId = product.getProductId();
        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId)
                .orElseGet(() -> new Inventory(product, INITIAL_STOCK));
        inventory.setAvailableQuantity(INITIAL_STOCK);
        inventory.setReservedQuantity(0);
        inventory.setSoldQuantity(0);
        inventoryRepository.saveAndFlush(inventory);
    }

    @Test
    void tenThousandConcurrentRequestsNeverReserveMoreThanAvailableStock() throws Exception {
        int workerCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(REQUEST_COUNT);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                final int requestNumber = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        reservationService.create(request(requestNumber + 1L), "concurrent-" + requestNumber);
                        successes.incrementAndGet();
                    } catch (ReservationException expectedRejection) {
                        assertTrue(expectedRejection.getStatus() == 409 || expectedRejection.getStatus() == 503,
                                "only unavailable inventory or bounded contention should reject a request");
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }

            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        Inventory finalInventory = inventoryRepository.findByProduct_ProductId(productId).orElseThrow();
        assertTrue(successes.get() <= INITIAL_STOCK, "successful reservations must not exceed initial stock");
        assertEquals(INITIAL_STOCK, successes.get(), "all available units should be reserved exactly once");
        assertEquals(REQUEST_COUNT, successes.get() + rejected.get(), "every request should succeed or be a business rejection");
        assertEquals(successes.get(), finalInventory.getReservedQuantity());
        assertEquals(INITIAL_STOCK - successes.get(), finalInventory.getAvailableQuantity());
        assertTrue(finalInventory.getAvailableQuantity() >= 0);
        assertTrue(finalInventory.getReservedQuantity() >= 0);
        assertTrue(finalInventory.getSoldQuantity() >= 0);
        assertTrue(finalInventory.getSoldQuantity() + finalInventory.getReservedQuantity() <= INITIAL_STOCK);
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingReservationWithoutReservingAgain() {
        CreateReservationRequest request = request(77L);
        ReservationResponse first = reservationService.create(request, "duplicate-key");
        ReservationResponse second = reservationService.create(request, "duplicate-key");

        assertEquals(first.reservationId(), second.reservationId());
        assertFalse(first.duplicate());
        assertTrue(second.duplicate());
        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId).orElseThrow();
        assertEquals(1, inventory.getReservedQuantity());
        assertEquals(1, reservationRepository.count());
    }

    @Test
    void expiryReleasesStockOnlyOnce() {
        ReservationResponse reservation = reservationService.create(request(88L), "expiry-key");

        assertEquals(1, reservationService.expireReservations(LocalDateTime.now().plusMinutes(10)));
        assertEquals(0, reservationService.expireReservations(LocalDateTime.now().plusMinutes(10)));

        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId).orElseThrow();
        assertEquals(INITIAL_STOCK, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(ReservationStatus.EXPIRED, reservationRepository.findById(reservation.reservationId()).orElseThrow().getStatus());
    }

    private CreateReservationRequest request(Long customerId) {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setCustomerId(customerId);
        request.setProductId(productId);
        request.setQuantity(1);
        return request;
    }
}