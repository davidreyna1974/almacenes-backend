package com.codigo2enter.almacenes.modules.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para registrar un movimiento de stock sobre un producto.
 *
 * El campo 'type' se recibe como String ("IN" o "OUT") en lugar del enum
 * MovementType para simplificar el contrato con el cliente (Angular no necesita
 * conocer el enum Java). El servicio es responsable de convertirlo al enum
 * antes de ejecutar la lógica de negocio, validando que el valor sea uno
 * de los dos permitidos.
 *
 * El campo 'productId' se incluye en el body (no como @PathVariable) para
 * que el request sea autocontenido y el endpoint POST /stock pueda ser
 * consumido sin necesidad de construir una URL dinámica desde el frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementRequestDTO {

    /** ID del producto al que se aplicará el movimiento de stock.
     *  El servicio valida que el producto exista antes de procesar el movimiento. */
    @NotNull(message = "El ID del producto es obligatorio")
    private Long productId;

    /** Número de unidades del movimiento. Siempre positivo — el campo 'type'
     *  determina si se suma (IN) o se resta (OUT) al stock actual del producto. */
    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private int quantity;

    /** Motivo o descripción del movimiento para el historial del Kardex.
     *  Ejemplos: "Compra orden #45", "Merma por caducidad", "Ajuste de inventario". */
    @NotBlank(message = "El motivo es obligatorio")
    @Size(max = 255)
    private String reason;

    /** Tipo de movimiento como texto: "IN" para entrada, "OUT" para salida.
     *  El servicio convierte este String al enum MovementType usando
     *  MovementType.valueOf(type), lo que rechaza cualquier valor distinto
     *  de "IN" u "OUT" con una excepción de negocio clara. */
    @NotBlank(message = "El tipo de movimiento es obligatorio")
    private String type;
}
