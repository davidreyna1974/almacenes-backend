package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.service.SaleOrderService;
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

    @PostMapping
    public ResponseEntity<SaleOrderResponseDTO> createOrder(@Valid @RequestBody SaleOrderRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleOrderService.createOrder(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleOrderResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.findById(id));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PageResponseDTO<SaleOrderResponseDTO>> findByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(saleOrderService.findByStatus(status, page, size));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByClientId(@PathVariable Long clientId) {
        return ResponseEntity.ok(saleOrderService.findByClientId(clientId));
    }

    @GetMapping("/client/{clientId}/status/{status}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByClientIdAndStatus(
            @PathVariable Long clientId, @PathVariable String status) {
        return ResponseEntity.ok(saleOrderService.findByClientIdAndStatus(clientId, status));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(saleOrderService.findByProductId(productId));
    }

    @GetMapping("/product/{productId}/status/{status}")
    public ResponseEntity<List<SaleOrderResponseDTO>> findByProductIdAndStatus(
            @PathVariable Long productId, @PathVariable String status) {
        return ResponseEntity.ok(saleOrderService.findByProductIdAndStatus(productId, status));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SaleOrderResponseDTO> updateOrder(@PathVariable Long id,
                                                             @Valid @RequestBody SaleOrderUpdateRequestDTO dto) {
        return ResponseEntity.ok(saleOrderService.updateOrder(id, dto));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<SaleOrderResponseDTO> approveOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.approveOrder(id));
    }

    @PatchMapping("/{id}/deliver")
    public ResponseEntity<SaleOrderResponseDTO> deliverOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.deliverOrder(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<SaleOrderResponseDTO> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.cancelOrder(id));
    }

    @PostMapping("/{id}/details")
    public ResponseEntity<SaleOrderResponseDTO> addDetail(@PathVariable Long id,
                                                           @Valid @RequestBody SaleOrderDetailRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleOrderService.addDetail(id, dto));
    }

    @PutMapping("/{id}/details/{detailId}")
    public ResponseEntity<SaleOrderResponseDTO> updateDetail(@PathVariable Long id,
                                                              @PathVariable Long detailId,
                                                              @Valid @RequestBody SaleOrderDetailUpdateRequestDTO dto) {
        return ResponseEntity.ok(saleOrderService.updateDetail(id, detailId, dto));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    public ResponseEntity<Void> removeDetail(@PathVariable Long id, @PathVariable Long detailId) {
        saleOrderService.removeDetail(id, detailId);
        return ResponseEntity.noContent().build();
    }
}
