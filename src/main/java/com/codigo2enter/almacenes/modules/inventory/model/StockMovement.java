package com.codigo2enter.almacenes.modules.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que registra cada movimiento de inventario aplicado a un producto.
 *
 * Actúa como bitácora inmutable del historial de stock: cada entrada o salida
 * genera un registro en esta tabla. Nunca se eliminan movimientos — permiten
 * auditoría completa y reconstrucción del stock en cualquier punto del tiempo.
 *
 * El campo 'updatable = false' en createdAt garantiza que la fecha de registro
 * nunca sea modificada accidentalmente por actualizaciones posteriores.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de unidades involucradas en el movimiento. Siempre positivo:
     *  el campo 'type' determina si se suma (IN) o se resta (OUT) al stock. */
    @Column(nullable = false)
    private int quantity;

    /** Motivo o descripción del movimiento.
     *  Ejemplos: "Compra orden #123", "Merma por caducidad", "Ajuste de inventario". */
    private String reason;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Tipo de movimiento: IN (entrada) u OUT (salida).
     *  EnumType.STRING almacena el nombre del enum ("IN"/"OUT") en lugar del
     *  ordinal numérico, haciendo los registros legibles y resistentes a
     *  cambios en el orden de declaración del enum. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    /** Producto al que pertenece este movimiento.
     *  FetchType.LAZY evita cargar el producto completo al consultar el
     *  historial de movimientos de un período, mejorando el rendimiento. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
