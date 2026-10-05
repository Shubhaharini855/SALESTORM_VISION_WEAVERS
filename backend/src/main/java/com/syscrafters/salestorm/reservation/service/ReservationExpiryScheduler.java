package com.syscrafters.salestorm.reservation.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@EnableScheduling
public class ReservationExpiryScheduler {
    private final ReservationService reservationService;

    public ReservationExpiryScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${salestorm.reservation.cleanup-interval-ms:5000}")
    public void releaseExpiredReservations() {
        reservationService.expireReservations(LocalDateTime.now());
    }
}