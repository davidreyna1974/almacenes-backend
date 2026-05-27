package com.codigo2enter.almacenes.modules.purchases.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de entrada para actualizar los campos editables de un detalle de orden.
 * Solo aplica cuando la orden padre está en estado PENDING.
 *
 * Campos editables: quantity y unitPrice.
 * El campo 'productId' NO se incluye deliberadamente — cambiar el producto
 * de un detalle existente es semánticamente un reemplazo, no una edición.
 * La operación correcta para cambiar el producto es:
 *   DELETE /{id}/details/{detailId}  →  POST /{id}/details con el nuevo producto
 *
 * El campo 'subtotal' tampoco se incluye — el servicio lo recalcula
 * automáticamente como quantity × unitPrice y también actualiza
 * totalAmount en la orden padre.
 *
 * Ejemplo de body esperado desde Angular:
 * {
 *     "quantity":  15,
 *     "unitPrice": 89.99
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDetailUpdateRequestDTO {

    /** Nueva cantidad de unidades para este detalle.
     *  Mínimo 1 — consistente con el CHECK > 0 de la BD y @Min(1) del request de creación. */
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private int quantity;

    /** Nuevo precio unitario pactado.
     *  Al actualizar, el servicio recalcula subtotal = quantity × unitPrice
     *  y también recalcula totalAmount en la orden padre. */
    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a cero")
    private BigDecimal unitPrice;
}
