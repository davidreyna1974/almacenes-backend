package com.codigo2enter.almacenes.modules.purchases.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para actualizar los campos editables de una orden de compra.
 * Solo aplica cuando la orden está en estado PENDING — el servicio rechaza
 * cualquier intento de edición sobre órdenes en otros estados.
 *
 * Campos editables: supplierId y notes.
 * Campos NO editables por el cliente: orderNumber, status, totalAmount,
 * createdBy, createdAt, approvedAt, receivedAt, cancelledAt.
 * Los detalles (líneas de productos) tienen sus propios endpoints:
 *   POST /{id}/details, PUT /{id}/details/{detailId}, DELETE /{id}/details/{detailId}
 *
 * Ejemplo de body esperado desde Angular:
 * {
 *     "supplierId": 2,
 *     "notes":      "Cambio de proveedor por mejor precio"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderUpdateRequestDTO {

    /** Nuevo proveedor asignado a la orden.
     *  El servicio valida que exista y esté activo antes de asignarlo.
     *  Puede ser el mismo proveedor actual si solo se desea actualizar las notas. */
    @NotNull(message = "El proveedor es obligatorio")
    private Long supplierId;

    /** Notas o instrucciones actualizadas. Nullable — enviar null borra las notas. */
    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    private String notes;
}
