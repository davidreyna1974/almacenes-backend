package com.codigo2enter.almacenes.modules.purchases.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de salida que representa una orden de compra completa.
 *
 * Aplana las relaciones @ManyToOne en campos simples para evitar objetos
 * anidados complejos en el JSON de respuesta:
 *   supplier  → supplierId + supplierName
 *   createdBy → createdById + createdByUsername
 *
 * Esto permite a Angular mostrar el nombre del proveedor y el usuario creador
 * directamente (response.supplierName, response.createdByUsername) sin
 * necesidad de peticiones adicionales para resolver esas relaciones.
 *
 * Los detalles se incluyen como lista anidada — en este caso sí conviene
 * anidarlos porque siempre se consultan junto con la orden y su número
 * variable de elementos justifica la lista en lugar de campos planos.
 *
 * No lleva anotaciones de validación — es exclusivamente de salida.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponseDTO {

    private Long id;
    private String orderNumber;

    /** Estado como String ("PENDING", "APPROVED", etc.) — serializado
     *  directamente desde el enum por Jackson, listo para usar en Angular. */
    private String status;

    private String notes;
    private BigDecimal totalAmount;

    /** Relación Supplier aplanada en dos campos. */
    private Long supplierId;
    private String supplierName;

    /** Relación User (createdBy) aplanada en dos campos.
     *  Permite auditar quién generó la orden sin exponer el objeto User completo. */
    private Long createdById;
    private String createdByUsername;

    private LocalDateTime createdAt;

    /** Última modificación — actualizado en cada operación de escritura
     *  sobre la orden o sus detalles. */
    private LocalDateTime updatedAt;

    /** Asignado cuando la orden transiciona a APPROVED. Null si aún no fue aprobada. */
    private LocalDateTime approvedAt;

    /** Asignado cuando la orden transiciona a RECEIVED. Null si aún no fue recibida. */
    private LocalDateTime receivedAt;

    /** Asignado cuando la orden transiciona a CANCELLED. Null si no fue cancelada. */
    private LocalDateTime cancelledAt;

    /** Usuario que aprobó la orden — aplanado para mostrar directamente en el
     *  historial sin petición adicional. Null si la orden aún no fue aprobada. */
    private Long   approvedById;
    private String approvedByUsername;

    /** Usuario que marcó la orden como recibida. Null si aún no fue recibida. */
    private Long   receivedById;
    private String receivedByUsername;

    /** Usuario que canceló la orden. Null si la orden no fue cancelada. */
    private Long   cancelledById;
    private String cancelledByUsername;

    /** Líneas de detalle con productos, cantidades, precios y subtotales. */
    private List<PurchaseOrderDetailResponseDTO> details;
}
