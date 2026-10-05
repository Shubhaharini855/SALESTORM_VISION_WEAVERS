package com.syscrafters.salestorm.demo;

import com.syscrafters.salestorm.inventory.dto.InventorySummaryResponse;
import com.syscrafters.salestorm.inventory.entity.Inventory;
import com.syscrafters.salestorm.inventory.repository.InventoryRepository;
import com.syscrafters.salestorm.order.entity.OrderStatus;
import com.syscrafters.salestorm.order.repository.SalesOrderRepository;
import com.syscrafters.salestorm.payment.entity.PaymentStatus;
import com.syscrafters.salestorm.payment.repository.PaymentAttemptRepository;
import com.syscrafters.salestorm.product.repository.ProductRepository;
import com.syscrafters.salestorm.reservation.dto.ReservationResponse;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;
import com.syscrafters.salestorm.reservation.repository.ReservationRepository;
import com.syscrafters.salestorm.reservation.service.ReservationException;
import com.syscrafters.salestorm.reservation.service.ReservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentAttemptRepository paymentRepository;
    private final SalesOrderRepository orderRepository;
    private final ReservationService reservationService;
    private final int initialStock;

    public DemoController(InventoryRepository inventoryRepository,
                          ProductRepository productRepository,
                          ReservationRepository reservationRepository,
                          PaymentAttemptRepository paymentRepository,
                          SalesOrderRepository orderRepository,
                          ReservationService reservationService,
                          @Value("${salestorm.demo.initial-stock:100}") int initialStock) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.reservationService = reservationService;
        this.initialStock = initialStock;
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<DemoSummaryResponse> summary(@RequestParam Long productId) {
        Inventory inventory = inventoryRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new ReservationException("INVENTORY_NOT_FOUND", "Inventory was not found", 404));
        InventorySummaryResponse stock = new InventorySummaryResponse(productId,
                inventory.getAvailableQuantity(), inventory.getReservedQuantity(),
                inventory.getSoldQuantity(), inventory.getVersion());
        long occupied = (long) stock.getReservedQuantity() + stock.getSoldQuantity();
        long released = reservationRepository.countByStatus(ReservationStatus.PAYMENT_FAILED)
                + reservationRepository.countByStatus(ReservationStatus.EXPIRED)
                + reservationRepository.countByStatus(ReservationStatus.RELEASED);
        return ResponseEntity.ok(new DemoSummaryResponse(initialStock, stock, reservationRepository.count(),
            reservationRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.FAILURE), released,
                orderRepository.countByStatus(OrderStatus.PENDING_RECOVERY),
                Math.max(0, occupied - initialStock)));
    }

    @PostMapping("/reservations/{reservationId}/expire")
    public ResponseEntity<ReservationResponse> expire(@PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.expireReservationNow(reservationId));
    }

    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<DemoSummaryResponse> reset() {
        orderRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        for (Inventory inventory : inventoryRepository.findAll()) {
            inventory.setAvailableQuantity(initialStock);
            inventory.setReservedQuantity(0);
            inventory.setSoldQuantity(0);
        }
        inventoryRepository.flush();
        Long productId = productRepository.findAll().stream().findFirst()
                .map(product -> product.getProductId())
                .orElseThrow(() -> new ReservationException("PRODUCT_NOT_FOUND", "Product was not found", 404));
        return summary(productId);
    }
}