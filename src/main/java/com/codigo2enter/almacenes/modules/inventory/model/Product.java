package com.codigo2enter.almacenes.modules.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un producto almacenado en el inventario.
 *
 * Centraliza toda la información del artículo: identificación (SKU), precios,
 * niveles de stock actuales y mínimos, estado operativo y relación con su
 * categoría y proveedor. El ciclo de vida del stock se gestiona a través
 * de StockMovement — nunca se modifica currentStock directamente sin registrar
 * el movimiento correspondiente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stock Keeping Unit: código único que identifica físicamente al producto.
     *  Ejemplo: "TOOL-TALADRO-001". Se valida unicidad antes de persistir. */
    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    /** Descripción larga del producto. Se mapea como TEXT para soportar
     *  contenido extenso sin límite de caracteres de VARCHAR. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Precio unitario del producto. BigDecimal garantiza precisión decimal
     *  exacta, evitando errores de redondeo propios de float/double. */
    @Column(nullable = false)
    private BigDecimal price;

    /** Cantidad de unidades actualmente disponibles en el almacén.
     *  Solo debe modificarse a través de applyStockMovement en el servicio. */
    @Column(name = "current_stock")
    private int currentStock;

    /** Umbral mínimo de stock. Cuando currentStock <= minimumStock el producto
     *  aparece en el reporte de stock bajo y debe reponerse. */
    @Column(name = "minimum_stock")
    private int minimumStock;

    /** Estado operativo del producto en el sistema.
     *  Valores esperados: "AVAILABLE", "DISCONTINUED", "OUT_OF_STOCK", etc.
     *  Se almacena como String para permitir extensión sin migración de enum. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Soft delete: false indica que el producto fue dado de baja lógicamente. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Categoría a la que pertenece el producto.
     *  FetchType.LAZY evita cargar la categoría completa al listar productos,
     *  previniendo queries N+1 en colecciones grandes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Proveedor que suministra este producto.
     *  FetchType.LAZY — el proveedor no se carga automáticamente al listar
     *  productos, evitando queries N+1 en colecciones grandes.
     *  La columna 'supplier_id' ya existe en la tabla con FK hacia suppliers. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Usuario que registró el producto en el catálogo.
     *  updatable=false garantiza que Hibernate nunca modifique este campo
     *  después de la inserción — el creador original es inmutable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    /** Fecha de la última modificación del producto. Null si nunca fue editado.
     *  Actualizado en updateProduct y deactivateProduct. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Usuario que realizó la última modificación. Null si el producto nunca fue editado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** Unidades comprometidas con órdenes de venta en estado APPROVED no entregadas.
     *  Se incrementa en approveOrder() y se decrementa en deliverOrder() o en
     *  cancelOrder() cuando la orden estaba APPROVED al cancelarse.
     *
     *  Invariante que el código garantiza en todo momento:
     *    0 <= reservedStock <= currentStock
     *    availableStock = currentStock - reservedStock >= 0
     *
     *  @Builder.Default = 0 para que los tests que construyen Product con el
     *  builder no necesiten especificar este campo explícitamente. */
    @Builder.Default
    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock = 0;

    /** Campo de control de Optimistic Locking de Hibernate.
     *  Hibernate lo incrementa automáticamente en cada UPDATE.
     *  Si dos transacciones concurrentes intentan modificar el mismo producto
     *  (e.g., dos approveOrder() simultáneos), la segunda recibe
     *  OptimisticLockingFailureException → HTTP 409 con mensaje claro.
     *
     *  Sin @Builder.Default — null antes del primer persist es el comportamiento
     *  correcto que Hibernate espera para el campo de versión. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** Costo unitario de compra del producto. Obligatorio — NOT NULL en BD.
     *  Se copia a SaleOrderDetail.unitCost al crear un detalle de venta para
     *  preservar el costo histórico: si el costo cambia después, las ventas
     *  registradas conservan el costo original para calcular margen. */
    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;
}
