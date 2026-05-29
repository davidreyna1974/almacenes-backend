package com.codigo2enter.almacenes.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de salida que representa un producto tal como lo recibe el cliente.
 *
 * A diferencia de ProductRequestDTO, este objeto incluye todos los campos
 * generados por el sistema (id, active, createdAt) y aplana la relación
 * con Category en dos campos simples (categoryId, categoryName) para evitar
 * objetos anidados complejos en la respuesta JSON.
 *
 * Aplanar la categoría en lugar de anidar un CategoryDTO permite que Angular
 * muestre el nombre en tablas y listas sin necesidad de acceder a
 * response.category.name — simplemente response.categoryName.
 *
 * No lleva anotaciones de validación porque es únicamente de salida —
 * nunca se recibe como @RequestBody.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {

    private Long id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;

    /** Cantidad de unidades actualmente disponibles en el almacén. */
    private int currentStock;

    /** Umbral mínimo configurado. Si currentStock <= minimumStock,
     *  el producto aparece en el reporte de stock bajo. */
    private int minimumStock;

    private String status;

    /** false indica que el producto fue dado de baja lógicamente (soft delete). */
    private boolean active;

    private LocalDateTime createdAt;

    /** ID del usuario que registró el producto. Campo de auditoría. */
    private Long   createdById;

    /** Username del usuario que registró el producto — aplanado para
     *  mostrar directamente sin petición adicional al servidor. */
    private String createdByUsername;

    /** Fecha de la última modificación. Null si el producto nunca fue editado. */
    private LocalDateTime updatedAt;

    /** ID del último usuario que modificó el producto. Null si nunca fue editado. */
    private Long   updatedById;

    /** Username del último editor — aplanado para el historial de cambios. */
    private String updatedByUsername;

    /** ID de la categoría — útil para el frontend cuando necesita pre-seleccionar
     *  la categoría en formularios de edición. */
    private Long categoryId;

    /** Nombre legible de la categoría — listo para mostrar en tablas y etiquetas
     *  sin necesidad de una segunda petición HTTP. */
    private String categoryName;

    private Long supplierId;
}
