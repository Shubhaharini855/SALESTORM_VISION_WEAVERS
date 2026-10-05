package com.syscrafters.salestorm.reservation.controller;

import com.syscrafters.salestorm.reservation.service.ReservationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<ApiError> handleReservation(ReservationException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                Instant.now(), exception.getStatus(), exception.getCode(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "VALIDATION_ERROR", message, request.getRequestURI()));
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
    }
}