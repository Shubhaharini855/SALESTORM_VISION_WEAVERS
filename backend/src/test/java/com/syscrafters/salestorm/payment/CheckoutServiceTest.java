package com.syscrafters.salestorm.payment;

import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.payment.dto.CheckoutRequest;
import com.syscrafters.salestorm.payment.dto.CheckoutResponse;
import com.syscrafters.salestorm.payment.entity.PaymentMode;
import com.syscrafters.salestorm.payment.entity.PaymentStatus;
import com.syscrafters.salestorm.payment.repository.PaymentAttemptRepository;
import com.syscrafters.salestorm.payment.service.CheckoutService;
import com.syscrafters.salestorm.order.entity.OrderStatus;
import com.syscrafters.salestorm.order.repository.SalesOrderRepository;
import com.syscrafters.salestorm.order.service.OrderService;
import com.syscrafters.salestorm.product.entity.Product;
import com.syscrafters.salestorm.product.repository.ProductRepository;
import com.syscrafters.salestorm.reservation.dto.CreateReservationRequest;
import com.syscrafters.salestorm.reservation.dto.ReservationResponse;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;
import com.syscrafters.salestorm.reservation.repository.ReservationRepository;
import com.syscrafters.salestorm.reservation.service.ReservationException;
import com.syscrafters.salestorm.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:salestorm-checkout-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "salestorm.reservation.cleanup-interval-ms=3600000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CheckoutServiceTest {
    private static final int INITIAL_STOCK = 10;

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReservationService reservationService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired private SalesOrderRepository orderRepository;
    @Autowired private OrderService orderService;

    private Long productId;

    @BeforeEach
    @Transactional
    void resetInventoryAndPayments() {
        orderRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        reservationRepository.deleteAll();
        Product product = productRepository.findAll().stream().findFirst().orElseGet(() ->
                productRepository.save(new Product("Checkout test product", "Payment test product", new BigDecimal("19.99"))));
        productId = product.getProductId();
        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId)
                .orElseGet(() -> new Inventory(product, INITIAL_STOCK));
        inventory.setAvailableQuantity(INITIAL_STOCK);
        inventory.setReservedQuantity(0);
        inventory.setSoldQuantity(0);
        inventoryRepository.saveAndFlush(inventory);
    }

    @Test
    void successConfirmsReservationAndMovesStockToSold() {
        ReservationResponse reservation = reserve("success-reservation");

        CheckoutRequest request = new CheckoutRequest(PaymentMode.SUCCESS);
        CheckoutResponse result = checkoutService.checkout(reservation.reservationId(), request, "success-payment");
        CheckoutResponse replay = checkoutService.checkout(reservation.reservationId(), request, "success-payment");

        assertEquals(PaymentStatus.SUCCESS, result.paymentStatus());
        assertEquals(ReservationStatus.CONFIRMED, result.reservationStatus());
        assertEquals(OrderStatus.CONFIRMED.name(), result.orderStatus());
        assertEquals(result.orderId(), replay.orderId());
        assertTrue(replay.duplicate());
        assertEquals(new BigDecimal("19.99"), result.amount());
        assertInventory(9, 0, 1);
        assertEquals(1, orderRepository.count());
    }

    @Test
    void simulatedOrderServiceFailureCanRecoverWithoutChargingAgain() {
        ReservationResponse reservation = reserve("order-recovery-reservation");

        CheckoutResponse result = checkoutService.checkout(reservation.reservationId(),
                new CheckoutRequest(PaymentMode.SUCCESS, true), "order-recovery-payment");

        assertEquals(PaymentStatus.SUCCESS, result.paymentStatus());
        assertEquals(OrderStatus.PENDING_RECOVERY.name(), result.orderStatus());
        assertInventory(9, 0, 1);

        assertEquals(OrderStatus.CONFIRMED, orderService.recover(result.orderId()).status());
        assertEquals(1, paymentAttemptRepository.count());
        assertEquals(1, orderRepository.count());
        assertInventory(9, 0, 1);
    }

    @Test
    void failureReleasesStockAndDuplicateCheckoutDoesNotReleaseTwice() {
        ReservationResponse reservation = reserve("failure-reservation");
        CheckoutRequest request = new CheckoutRequest(PaymentMode.FAILURE);

        CheckoutResponse first = checkoutService.checkout(reservation.reservationId(), request, "failure-payment");
        CheckoutResponse replay = checkoutService.checkout(reservation.reservationId(), request, "failure-payment");

        assertEquals(PaymentStatus.FAILURE, first.paymentStatus());
        assertEquals(ReservationStatus.PAYMENT_FAILED, first.reservationStatus());
        assertTrue(replay.duplicate());
        assertEquals(first.paymentId(), replay.paymentId());
        assertInventory(INITIAL_STOCK, 0, 0);
        assertEquals(1, paymentAttemptRepository.count());
    }

    @Test
    void timeoutRecordsUnknownOutcomeAndKeepsReservationAndStockPending() {
        ReservationResponse reservation = reserve("timeout-reservation");

        CheckoutResponse result = checkoutService.checkout(reservation.reservationId(),
                new CheckoutRequest(PaymentMode.TIMEOUT), "timeout-payment");

        assertEquals(PaymentStatus.TIMEOUT, result.paymentStatus());
        assertEquals(ReservationStatus.PAYMENT_PENDING, result.reservationStatus());
        assertInventory(INITIAL_STOCK - 1, 1, 0);
    }

    @Test
    void reusingPaymentKeyWithDifferentModeIsRejected() {
        ReservationResponse reservation = reserve("conflict-reservation");
        checkoutService.checkout(reservation.reservationId(), new CheckoutRequest(PaymentMode.FAILURE), "conflict-payment");

        ReservationException exception = assertThrows(ReservationException.class, () ->
                checkoutService.checkout(reservation.reservationId(), new CheckoutRequest(PaymentMode.SUCCESS), "conflict-payment"));

        assertEquals("IDEMPOTENCY_CONFLICT", exception.getCode());
        assertInventory(INITIAL_STOCK, 0, 0);
    }

    private ReservationResponse reserve(String key) {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setCustomerId(1L);
        request.setProductId(productId);
        request.setQuantity(1);
        return reservationService.create(request, key);
    }

    private void assertInventory(int available, int reserved, int sold) {
        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId).orElseThrow();
        assertEquals(available, inventory.getAvailableQuantity());
        assertEquals(reserved, inventory.getReservedQuantity());
        assertEquals(sold, inventory.getSoldQuantity());
    }
}