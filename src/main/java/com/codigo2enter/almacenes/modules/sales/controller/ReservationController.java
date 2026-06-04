package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.service.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Reservaciones", description = "Consulta de reservas de stock activas")
@RestController
@RequestMapping("/api/v1/sales/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/summary")
    public ResponseEntity<ReservationSummaryDTO> getSummary() {
        return ResponseEntity.ok(reservationService.getSummary());
    }

    @GetMapping("/products")
    public ResponseEntity<List<ReservedProductDTO>> getReservedProducts() {
        return ResponseEntity.ok(reservationService.getReservedProducts());
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ReservedProductDTO> getProductReservationDetail(@PathVariable Long productId) {
        return ResponseEntity.ok(reservationService.getProductReservationDetail(productId));
    }

    @GetMapping("/clients")
    public ResponseEntity<List<ReservedClientDTO>> getClientsWithReservations() {
        return ResponseEntity.ok(reservationService.getClientsWithReservations());
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ReservedClientDTO> getClientReservationDetail(@PathVariable Long clientId) {
        return ResponseEntity.ok(reservationService.getClientReservationDetail(clientId));
    }
}
