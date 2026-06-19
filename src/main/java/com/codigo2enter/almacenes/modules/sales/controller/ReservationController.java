package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "Resumen de reservaciones", description = "Totales globales de stock reservado: órdenes activas, productos y unidades comprometidas")
    @ApiResponse(responseCode = "200", description = "Resumen de reservaciones")
    @GetMapping("/summary")
    public ResponseEntity<ReservationSummaryDTO> getSummary() {
        return ResponseEntity.ok(reservationService.getSummary());
    }

    @Operation(summary = "Productos con reservas activas", description = "Lista de productos con stock reservado en órdenes APPROVED — muestra unidades comprometidas vs disponibles")
    @ApiResponse(responseCode = "200", description = "Lista de productos reservados")
    @GetMapping("/products")
    public ResponseEntity<List<ReservedProductDTO>> getReservedProducts() {
        return ResponseEntity.ok(reservationService.getReservedProducts());
    }

    @Operation(summary = "Detalle de reservas por producto", description = "Detalle del stock reservado de un producto específico con las órdenes que lo comprometen")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Detalle de reservas del producto"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @GetMapping("/products/{productId}")
    public ResponseEntity<ReservedProductDTO> getProductReservationDetail(@PathVariable Long productId) {
        return ResponseEntity.ok(reservationService.getProductReservationDetail(productId));
    }

    @Operation(summary = "Clientes con reservas activas", description = "Lista de clientes con órdenes de venta en APPROVED que tienen stock comprometido")
    @ApiResponse(responseCode = "200", description = "Lista de clientes con reservas")
    @GetMapping("/clients")
    public ResponseEntity<List<ReservedClientDTO>> getClientsWithReservations() {
        return ResponseEntity.ok(reservationService.getClientsWithReservations());
    }

    @Operation(summary = "Detalle de reservas por cliente", description = "Detalle de las órdenes activas de un cliente con el stock reservado en cada una")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Detalle de reservas del cliente"),
                    @ApiResponse(responseCode = "404", description = "Cliente no encontrado") })
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ReservedClientDTO> getClientReservationDetail(@PathVariable Long clientId) {
        return ResponseEntity.ok(reservationService.getClientReservationDetail(clientId));
    }
}
