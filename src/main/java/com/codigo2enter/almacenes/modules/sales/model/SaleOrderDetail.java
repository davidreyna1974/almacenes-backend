package com.codigo2enter.almacenes.modules.sales.model;

import com.codigo2enter.almacenes.modules.inventory.model.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Línea de detalle de una orden de venta.
 *
 * Captura tres valores monetarios al momento de crear/editar el detalle:
 *   - unitPrice : precio de venta pactado con el cliente
 *   - unitCost  : costo de compra copiado desde Product.unitCost — nullable
 *                 si el producto no tiene costo definido
 *   - subtotal  : quantity × unitPrice, calculado por el servicio
 *
 * La captura de unitCost en el detalle (en lugar de leerlo siempre del producto)
 * preserva el dato histórico: si el costo del producto cambia después, las ventas
 * ya registradas conservan el costo original para calcular margen correctamente.
 *
 * unitCost NO tiene updatable=false — el servicio lo re-lee del producto cuando
 * el detalle se actualiza mientras la orden está en PENDING. Una vez que la orden
 * pasa a APPROVED, el servicio bloquea la edición de detalles, congelando
 * efectivamente el costo capturado sin necesidad de restricción a nivel de ORM.
 *
 * No tiene columnas de auditoría propias — hereda trazabilidad del padre (SaleOrder)
 * igual que PurchaseOrderDetail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sale_order_details")
public class SaleOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int quantity;

    /** Precio de venta pactado al momento de crear o editar el detalle. */
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    /**
     * Costo de compra copiado desde Product.unitCost al crear o actualizar
     * el detalle. Null si el producto no tiene costo definido aún.
     * El módulo financiero calculará margen (unitPrice - unitCost) solo
     * donde este campo no sea null.
     */
    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    /** quantity × unitPrice. Calculado y asignado por SaleOrderServiceImpl. */
    @Column(nullable = false)
    private BigDecimal subtotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id", nullable = false)
    private SaleOrder saleOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
