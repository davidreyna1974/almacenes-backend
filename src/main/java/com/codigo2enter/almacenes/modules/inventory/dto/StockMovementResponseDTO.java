package com.codigo2enter.almacenes.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de salida que representa un movimiento de stock tal como lo recibe el cliente.
 *
 * Los movimientos son registros inmutables — solo existen DTOs de respuesta,
 * no de request, porque la creación de un movimiento se realiza a través de
 * ProductService.applyStockMovement() que recibe directamente los parámetros
 * necesarios (productId, type, quantity, reason).
 *
 * El campo 'type' se expone como String en lugar del enum MovementType para
 * que Jackson lo serialice como "IN" u "OUT" directamente, sin configuración
 * adicional en el frontend.
 *
 * Los campos 'productId' y 'productName' aplanan la relación con Product,
 * evitando un objeto anidado en el JSON del historial de Kardex.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponseDTO {

    private Long id;

    /** Número de unidades involucradas en el movimiento. Siempre positivo. */
    private int quantity;

    /** Motivo del movimiento registrado por el operador. Puede ser null. */
    private String reason;

    /** Fecha y hora exacta del movimiento. El repositorio ordena por este
     *  campo de forma descendente para el historial del Kardex. */
    private LocalDateTime createdAt;

    /** "IN" o "OUT" — serializado directamente desde el enum MovementType.
     *  El frontend puede usarlo para aplicar estilos distintos a cada tipo. */
    private String type;

    /** ID del producto al que pertenece este movimiento. */
    private Long productId;

    /** Nombre del producto — evita que Angular tenga que hacer una segunda
     *  petición para resolver el nombre a partir del productId. */
    private String productName;
}
