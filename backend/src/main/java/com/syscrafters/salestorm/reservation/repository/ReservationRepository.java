package com.syscrafters.salestorm.reservation.repository;

import com.syscrafters.salestorm.reservation.entity.InventoryReservation;
import com.syscrafters.salestorm.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);
    long countByStatus(ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.reservationId = :reservationId")
    Optional<InventoryReservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.status in :statuses and r.expiresAt <= :now")
    List<InventoryReservation> findExpiredForUpdate(@Param("statuses") List<ReservationStatus> statuses,
                                                     @Param("now") LocalDateTime now);
}