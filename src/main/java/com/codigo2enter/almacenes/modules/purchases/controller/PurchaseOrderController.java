package com.codigo2enter.almacenes.modules.purchases.controller;

import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para la gestión de órdenes de compra.
 *
 * Ruta base: /api/v1/purchases/orders
 * Todas las rutas están protegidas por JWT — requieren cabecera:
 *   Authorization: Bearer <token>
 *
 * Expone 13 endpoints que cubren el ciclo de vida completo:
 * creación, consulta, edición, transiciones de estado y gestión de detalles.
 *
 * Nota sobre PATCH vs PUT:
 *   PATCH → transición de estado (modifica solo el campo 'status')
 *   PUT   → actualización de datos (notes, supplierId, campos de detalle)
 */
@RestController
@RequestMapping("/api/v1/purchases/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /**
     * POST /api/v1/purchases/orders
     *
     * Crea una nueva orden de compra en estado PENDING.
     * El campo 'createdBy' se resuelve automáticamente desde el JWT — no se envía.
     * El campo 'orderNumber' (OC-YYYY-NNNN) lo genera el servicio.
     * El campo 'totalAmount' lo calcula el servicio como suma de subtotales.
     * @NotEmpty en details garantiza al menos una línea de producto.
     * @Valid propaga validaciones a cada PurchaseOrderDetailRequestDTO de la lista.
     *
     * @param dto datos de la orden y sus detalles iniciales
     * @return 201 Created con la PurchaseOrderResponseDTO completa
     */
    @PostMapping
    public ResponseEntity<PurchaseOrderResponseDTO> createOrder(
            @Valid @RequestBody PurchaseOrderRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.createOrder(dto));
    }

    /**
     * GET /api/v1/purchases/orders/{id}
     *
     * Retorna la orden completa con todos sus detalles.
     * Los timestamps null (approvedAt, receivedAt, cancelledAt) indican que
     * la orden aún no alcanzó ese estado.
     *
     * @param id identificador de la orden
     * @return 200 OK con la PurchaseOrderResponseDTO incluyendo detalles
     */
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.findById(id));
    }

    /**
     * GET /api/v1/purchases/orders/status/{status}
     *
     * Retorna todas las órdenes en el estado indicado.
     * El valor de {status} debe ser exactamente uno de:
     *   PENDING, APPROVED, RECEIVED, CANCELLED  (case-sensitive)
     * El servicio convierte el String al enum con valueOf() — valor inválido
     * produce RuntimeException con los valores válidos en el mensaje.
     *
     * @param status nombre del estado como String
     * @return 200 OK con la lista de órdenes (puede ser vacía)
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PurchaseOrderResponseDTO>> findByStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(purchaseOrderService.findByStatus(status));
    }

    /**
     * GET /api/v1/purchases/orders/supplier/{supplierId}
     *
     * Retorna todas las órdenes de un proveedor en cualquier estado.
     * El servicio valida que el supplierId exista antes de consultar,
     * distinguiendo "proveedor sin órdenes" (lista vacía) de "inexistente" (error).
     *
     * @param supplierId identificador del proveedor
     * @return 200 OK con la lista de órdenes del proveedor
     */
    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<List<PurchaseOrderResponseDTO>> findBySupplierId(
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(purchaseOrderService.findBySupplierId(supplierId));
    }

    /**
     * GET /api/v1/purchases/orders/supplier/{supplierId}/status/{status}
     *
     * Filtro combinado: retorna las órdenes de un proveedor específico
     * que se encuentran en el estado indicado. Más eficiente que pedir
     * todas las órdenes del proveedor y filtrar en el frontend.
     *
     * Casos de uso:
     *   /supplier/1/status/PENDING   → órdenes pendientes de aprobación del proveedor 1
     *   /supplier/1/status/APPROVED  → mercancía en tránsito del proveedor 1
     *   /supplier/1/status/RECEIVED  → historial de recepciones del proveedor 1
     *
     * @param supplierId identificador del proveedor
     * @param status     nombre del estado (case-sensitive)
     * @return 200 OK con la lista filtrada (puede ser vacía)
     */
    @GetMapping("/supplier/{supplierId}/status/{status}")
    public ResponseEntity<List<PurchaseOrderResponseDTO>> findBySupplierIdAndStatus(
            @PathVariable Long supplierId,
            @PathVariable String status) {
        return ResponseEntity.ok(
                purchaseOrderService.findBySupplierIdAndStatus(supplierId, status));
    }

    /**
     * GET /api/v1/purchases/orders/product/{productId}
     *
     * Retorna todas las órdenes que incluyen un producto específico en sus detalles.
     * Útil para consultar el historial de compras de un producto: a qué proveedores
     * se ha comprado y a qué precios históricamente.
     *
     * @param productId identificador del producto
     * @return 200 OK con la lista de órdenes que contienen ese producto
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<PurchaseOrderResponseDTO>> findOrdersByProduct(
            @PathVariable Long productId) {
        return ResponseEntity.ok(purchaseOrderService.findOrdersByProduct(productId));
    }

    /**
     * PUT /api/v1/purchases/orders/{id}
     *
     * Actualiza los campos editables de una orden: supplierId y notes.
     * Solo aplica cuando status == PENDING — el servicio rechaza cualquier
     * intento de edición sobre órdenes en otros estados.
     * Los detalles tienen sus propios endpoints de edición.
     * updatedAt se asigna automáticamente en el servicio.
     *
     * @param id  identificador de la orden
     * @param dto nuevos valores de supplierId y notes
     * @return 200 OK con la PurchaseOrderResponseDTO actualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponseDTO> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderUpdateRequestDTO dto) {
        return ResponseEntity.ok(purchaseOrderService.updateOrder(id, dto));
    }

    /**
     * PATCH /api/v1/purchases/orders/{id}/approve
     *
     * Transiciona la orden de PENDING a APPROVED.
     * PATCH porque solo modifica el campo 'status' — modificación parcial.
     * El servicio valida que la orden tenga al menos un detalle antes de aprobar.
     * Tras la aprobación los detalles quedan bloqueados.
     * No recibe body — la transición no requiere datos adicionales del cliente.
     *
     * @param id identificador de la orden a aprobar
     * @return 200 OK con la orden en estado APPROVED y approvedAt asignado
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<PurchaseOrderResponseDTO> approveOrder(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.approveOrder(id));
    }

    /**
     * PATCH /api/v1/purchases/orders/{id}/receive
     *
     * Transiciona la orden de APPROVED a RECEIVED.
     * Es el endpoint de mayor impacto del sistema: dispara registerStockMovement(IN)
     * por cada detalle de la orden dentro de la misma transacción, incrementando
     * el stock automáticamente. Si cualquier movimiento falla, Hibernate hace
     * rollback completo — inventario y orden quedan en el estado previo.
     * Estado terminal: RECEIVED no admite más transiciones.
     *
     * @param id identificador de la orden a recibir
     * @return 200 OK con la orden en estado RECEIVED y receivedAt asignado
     */
    @PatchMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderResponseDTO> receiveOrder(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.receiveOrder(id));
    }

    /**
     * PATCH /api/v1/purchases/orders/{id}/cancel
     *
     * Transiciona la orden de PENDING o APPROVED a CANCELLED.
     * No impacta el inventario en ningún caso.
     * Los detalles se conservan como historial de intención de compra.
     * Estado terminal: CANCELLED no admite más transiciones.
     *
     * @param id identificador de la orden a cancelar
     * @return 200 OK con la orden en estado CANCELLED y cancelledAt asignado
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrderResponseDTO> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.cancelOrder(id));
    }

    /**
     * POST /api/v1/purchases/orders/{id}/details
     *
     * Agrega una línea de detalle a una orden en estado PENDING.
     * El servicio valida que el producto no esté ya en la orden.
     * subtotal (quantity × unitPrice) y totalAmount se calculan en el servicio.
     * La respuesta retorna la orden completa para que Angular vea el
     * totalAmount actualizado sin petición adicional.
     *
     * @param id  identificador de la orden
     * @param dto datos del detalle (productId, quantity, unitPrice)
     * @return 201 Created con la orden completa incluyendo el nuevo detalle
     */
    @PostMapping("/{id}/details")
    public ResponseEntity<PurchaseOrderResponseDTO> addDetail(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderDetailRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.addDetail(id, dto));
    }

    /**
     * PUT /api/v1/purchases/orders/{id}/details/{detailId}
     *
     * Actualiza quantity y unitPrice de un detalle en una orden PENDING.
     * productId NO es editable — cambiar el producto requiere delete + add.
     * subtotal y totalAmount se recalculan automáticamente en el servicio.
     * findByIdAndPurchaseOrderId valida que el detalle pertenece a la orden
     * del path — protección contra accesos cruzados entre órdenes.
     * La respuesta retorna la orden completa con los totales actualizados.
     *
     * @param id       identificador de la orden
     * @param detailId identificador del detalle a actualizar
     * @param dto      nuevos valores de quantity y unitPrice
     * @return 200 OK con la orden completa y totalAmount recalculado
     */
    @PutMapping("/{id}/details/{detailId}")
    public ResponseEntity<PurchaseOrderResponseDTO> updateDetail(
            @PathVariable Long id,
            @PathVariable Long detailId,
            @Valid @RequestBody PurchaseOrderDetailUpdateRequestDTO dto) {
        return ResponseEntity.ok(purchaseOrderService.updateDetail(id, detailId, dto));
    }

    /**
     * DELETE /api/v1/purchases/orders/{id}/details/{detailId}
     *
     * Elimina FÍSICAMENTE un detalle de una orden en estado PENDING.
     * A diferencia del soft delete de proveedores y productos, aquí la
     * eliminación es física — orphanRemoval=true en PurchaseOrder hace que
     * Hibernate ejecute DELETE FROM purchase_order_details al remover el
     * elemento de la lista y guardar la orden.
     * totalAmount de la orden se recalcula automáticamente.
     * Si se elimina el último detalle, totalAmount queda en 0.
     *
     * @param id       identificador de la orden
     * @param detailId identificador del detalle a eliminar
     * @return 204 No Content — eliminación exitosa sin cuerpo de respuesta
     */
    @DeleteMapping("/{id}/details/{detailId}")
    public ResponseEntity<Void> removeDetail(
            @PathVariable Long id,
            @PathVariable Long detailId) {
        purchaseOrderService.removeDetail(id, detailId);
        return ResponseEntity.noContent().build();
    }
}
