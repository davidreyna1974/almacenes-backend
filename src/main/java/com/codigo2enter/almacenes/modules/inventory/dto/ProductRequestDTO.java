package com.codigo2enter.almacenes.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de entrada para crear o actualizar un producto en el inventario.
 *
 * Todas las validaciones de Jakarta se ejecutan antes de invocar al servicio,
 * gracias a la anotación @Valid en el parámetro del controlador. Si alguna
 * falla, Spring retorna automáticamente HTTP 400 Bad Request con el mensaje
 * definido en cada restricción.
 *
 * No incluye 'id', 'active' ni 'createdAt' — esos campos los gestiona
 * el servicio y la base de datos, no el cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    /** Código único de identificación del producto en el almacén.
     *  El servicio valida que no exista otro producto con el mismo SKU. */
    @NotBlank(message = "El SKU es obligatorio")
    @Size(max = 50)
    private String sku;

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 120)
    private String name;

    /** Descripción larga del producto. Sin restricción de tamaño —
     *  la entidad lo mapea como TEXT en la base de datos. */
    private String description;

    /** Precio unitario. BigDecimal garantiza precisión decimal exacta.
     *  El mínimo es 0.01 para rechazar productos gratuitos o con precio negativo. */
    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a cero")
    private BigDecimal price;

    /** Stock inicial al registrar el producto. Puede ser 0 si aún no
     *  ha ingresado mercancía — el primer movimiento IN lo actualizará. */
    @Min(value = 0, message = "El stock inicial no puede ser negativo")
    private int currentStock;

    /** Umbral de alerta de reposición. El sistema genera una alerta cuando
     *  currentStock desciende a este nivel o por debajo. Mínimo 1 para
     *  que la alerta siempre pueda activarse antes de llegar a cero. */
    @Min(value = 1, message = "El stock mínimo debe ser al menos 1")
    private int minimumStock;

    /** Estado operativo del producto.
     *  Valores esperados: "AVAILABLE", "DISCONTINUED", "OUT_OF_STOCK". */
    @NotBlank(message = "El estatus es obligatorio")
    private String status;

    /** ID de la categoría a la que pertenece el producto.
     *  El servicio resuelve la entidad Category desde este ID antes de persistir. */
    @NotNull(message = "La categoría es obligatoria")
    private Long categoryId;

    /** ID del proveedor que suministra este producto.
     *  Se almacena como FK simple hasta que el módulo de compras implemente
     *  la entidad Supplier con su relación completa. */
    @NotNull(message = "El proveedor es obligatorio")
    private Long supplierId;

    /** Costo unitario de compra del producto. Opcional — nullable para captura
     *  progresiva. Un costo de 0.00 es válido (productos sin costo de adquisición).
     *  Este valor se copia automáticamente a SaleOrderDetail.unitCost al crear
     *  un detalle de venta, preservando el costo histórico para análisis futuros. */
    @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
    private BigDecimal unitCost;
}
