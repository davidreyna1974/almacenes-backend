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
 * DTO de entrada para agregar una línea de detalle a una orden de compra.
 * Solo aplica cuando la orden está en estado PENDING.
 *
 * El campo 'unitPrice' es enviado por el cliente (Angular), quien típicamente
 * lo pre-rellena con el precio de catálogo del producto (product.price) para
 * mejorar la UX. Sin embargo, el usuario puede modificarlo antes de enviar
 * para reflejar el precio negociado con el proveedor.
 *
 * El campo 'subtotal' NO se incluye — el servicio lo calcula automáticamente
 * como quantity × unitPrice antes de persistir el detalle.
 *
 * Ejemplo de body esperado desde Angular:
 * {
 *     "productId":  5,
 *     "quantity":   10,
 *     "unitPrice":  89.99
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDetailRequestDTO {

    /** ID del producto a incluir en la orden.
     *  El servicio valida que el producto exista y que no esté ya en la misma orden. */
    @NotNull(message = "El producto es obligatorio")
    private Long productId;

    /** Número de unidades a comprar. Mínimo 1 — consistente con el CHECK > 0 de la BD. */
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private int quantity;

    /** Precio unitario pactado con el proveedor.
     *  Se persiste en el detalle para preservar el precio histórico independientemente
     *  de cambios futuros en Product.price. */
    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a cero")
    private BigDecimal unitPrice;
}
