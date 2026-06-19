package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.service.SaleOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Órdenes de Venta", description = "Ciclo de vida de órdenes de venta")
@RestController
@RequestMapping("/api/v1/sales/orders")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService saleOrderService;

    @Operation(summary = "Crear orden de venta", description = "Crea una OV en estado PENDING — orderNumber (OV-YYYY-NNNN) y totalAmount los genera el servicio; reserva stock al aprobar")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Orden creada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos o lista de detalles vacía") })
    @PostMapping
    public ResponseEntity<SaleOrderResponseDTO> createOrder(@Valid @RequestBody SaleOrderRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleOrderService.createOrder(dto));
    }

    @Operation(summary = "Obtener orden de venta por ID", description = "Devuelve la orden completa con todos sus detalles y timestamps de transición de estado")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Orden encontrada"),
                    @ApiResponse(responseCode = "404", description = "Orden no encontrada") })
    @GetMapping("/{id}")
    public ResponseEntity<SaleOrderResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.findById(id));
    }

    @Operation(summary = "Órdenes de venta por estado", description = "Retorna órdenes paginadas por estado — valores válidos: PENDING, APPROVED, DELIVERED, CANCELLED")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Página de órdenes"),
                    @ApiResponse(responseCode = "400", description = "Estado inválido") })
    @GetMapping("/status/{status}")
    public ResponseEntity<PageResponseDTO<SaleOrderResponseDTO>> findByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(saleOrderService.findByStatus(status, page, size));
    }

    @Operation(summary = "Órdenes de venta por cliente", description = "Historial completo de órdenes de un cliente en cualquier estado")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Lista de órdenes del cliente"),
                    @ApiResponse(responseCode = "404", description = "Cliente no encontrado") })
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByClientId(@PathVariable Long clientId) {
        return ResponseEntity.ok(saleOrderService.findByClientId(clientId));
    }

    @Operation(summary = "Órdenes por cliente y estado", description = "Filtro combinado cliente + estado — más eficiente que filtrar en el frontend")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Lista filtrada de órdenes"),
                    @ApiResponse(responseCode = "404", description = "Cliente no encontrado") })
    @GetMapping("/client/{clientId}/status/{status}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByClientIdAndStatus(
            @PathVariable Long clientId, @PathVariable String status) {
        return ResponseEntity.ok(saleOrderService.findByClientIdAndStatus(clientId, status));
    }

    @Operation(summary = "Órdenes de venta por producto", description = "Historial de ventas de un producto — clientes y precios históricos")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Lista de órdenes con ese producto"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(saleOrderService.findByProductId(productId));
    }

    @Operation(summary = "Órdenes por producto y estado", description = "Filtro combinado producto + estado — útil para ver compromisos activos de un artículo")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Lista filtrada de órdenes"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @GetMapping("/product/{productId}/status/{status}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByProductIdAndStatus(
            @PathVariable Long productId, @PathVariable String status) {
        return ResponseEntity.ok(saleOrderService.findByProductIdAndStatus(productId, status));
    }

    @Operation(summary = "Actualizar orden de venta", description = "Edita clientId y notes — solo permitido en estado PENDING; los detalles tienen endpoints propios")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Orden actualizada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
                    @ApiResponse(responseCode = "422", description = "La orden no está en estado PENDING") })
    @PutMapping("/{id}")
    public ResponseEntity<SaleOrderResponseDTO> updateOrder(@PathVariable Long id,
                                                             @Valid @RequestBody SaleOrderUpdateRequestDTO dto) {
        return ResponseEntity.ok(saleOrderService.updateOrder(id, dto));
    }

    @Operation(summary = "Aprobar orden de venta", description = "Transición PENDING → APPROVED — reserva stock (reservedStock++) por cada detalle; valida availableStock")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Orden aprobada — stock reservado"),
                    @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
                    @ApiResponse(responseCode = "422", description = "Stock insuficiente o la orden no está en PENDING") })
    @PatchMapping("/{id}/approve")
    public ResponseEntity<SaleOrderResponseDTO> approveOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.approveOrder(id));
    }

    @Operation(summary = "Entregar orden de venta", description = "Transición APPROVED → DELIVERED — descuenta stock (OUT) y libera reserva; estado terminal")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Orden entregada — stock descontado"),
                    @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
                    @ApiResponse(responseCode = "422", description = "La orden no está en APPROVED") })
    @PatchMapping("/{id}/deliver")
    public ResponseEntity<SaleOrderResponseDTO> deliverOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.deliverOrder(id));
    }

    @Operation(summary = "Cancelar orden de venta", description = "Transición PENDING/APPROVED → CANCELLED — si estaba APPROVED libera la reserva de stock; estado terminal")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Orden cancelada"),
                    @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
                    @ApiResponse(responseCode = "422", description = "La orden ya está en estado terminal (DELIVERED o CANCELLED)") })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<SaleOrderResponseDTO> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.cancelOrder(id));
    }

    @Operation(summary = "Agregar detalle a OV", description = "Añade una línea de producto a una OV en PENDING — recalcula totalAmount; un producto no puede repetirse")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Detalle agregado — retorna orden completa"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "404", description = "Orden o producto no encontrado"),
                    @ApiResponse(responseCode = "409", description = "El producto ya está en la orden"),
                    @ApiResponse(responseCode = "422", description = "La orden no está en PENDING") })
    @PostMapping("/{id}/details")
    public ResponseEntity<SaleOrderResponseDTO> addDetail(@PathVariable Long id,
                                                           @Valid @RequestBody SaleOrderDetailRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleOrderService.addDetail(id, dto));
    }

    @Operation(summary = "Actualizar detalle de OV", description = "Edita quantity/unitPrice de un detalle en PENDING — productId no es editable; recalcula subtotal y totalAmount")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Detalle actualizado — retorna orden completa"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "404", description = "Orden o detalle no encontrado"),
                    @ApiResponse(responseCode = "422", description = "La orden no está en PENDING") })
    @PutMapping("/{id}/details/{detailId}")
    public ResponseEntity<SaleOrderResponseDTO> updateDetail(@PathVariable Long id,
                                                              @PathVariable Long detailId,
                                                              @Valid @RequestBody SaleOrderDetailUpdateRequestDTO dto) {
        return ResponseEntity.ok(saleOrderService.updateDetail(id, detailId, dto));
    }

    @Operation(summary = "Eliminar detalle de OV", description = "Eliminación física del detalle (orphanRemoval) en OV PENDING — recalcula totalAmount")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Detalle eliminado"),
                    @ApiResponse(responseCode = "404", description = "Orden o detalle no encontrado"),
                    @ApiResponse(responseCode = "422", description = "La orden no está en PENDING") })
    @DeleteMapping("/{id}/details/{detailId}")
    public ResponseEntity<Void> removeDetail(@PathVariable Long id, @PathVariable Long detailId) {
        saleOrderService.removeDetail(id, detailId);
        return ResponseEntity.noContent().build();
    }
}
