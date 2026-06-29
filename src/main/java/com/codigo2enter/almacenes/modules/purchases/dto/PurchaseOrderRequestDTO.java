package com.codigo2enter.almacenes.modules.purchases.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de entrada para crear una nueva orden de compra.
 *
 * El campo 'createdBy' NO se incluye aquí — el servicio lo resuelve
 * automáticamente desde el JWT de la petición a través de SecurityContextHolder.
 * El cliente nunca envía quién creó la orden.
 *
 * El campo 'orderNumber' tampoco se incluye — el servicio lo genera con
 * el formato OC-YYYY-NNNN usando un contador anual.
 *
 * El campo 'totalAmount' no se incluye — el servicio lo calcula como
 * suma de los subtotales de cada detalle.
 *
 * Ejemplo de body esperado desde Angular:
 * {
 *     "supplierId": 1,
 *     "notes":      "Pedido urgente Q2",
 *     "details": [
 *         { "productId": 5,  "quantity": 10, "unitPrice": 99.99 },
 *         { "productId": 12, "quantity": 5,  "unitPrice": 249.50 }
 *     ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderRequestDTO {

    /** ID del proveedor al que se realiza la compra.
     *  El servicio valida que el proveedor exista y esté activo. */
    @NotNull(message = "El proveedor es obligatorio")
    private Long supplierId;

    /** Notas o instrucciones adicionales para la orden. Opcional. */
    @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
    private String notes;

    /** Líneas de detalle de la orden — al menos una es requerida.
     *  @Valid propaga las validaciones de PurchaseOrderDetailRequestDTO
     *  a cada elemento de la lista. */
    @NotEmpty(message = "La orden debe contener al menos una línea de detalle")
    @Valid
    private List<PurchaseOrderDetailRequestDTO> details;
}
