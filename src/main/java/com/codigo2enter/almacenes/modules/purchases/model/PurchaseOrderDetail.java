package com.codigo2enter.almacenes.modules.purchases.model;

import com.codigo2enter.almacenes.modules.inventory.model.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entidad JPA que representa una línea de detalle dentro de una orden de compra.
 *
 * Cada detalle vincula un producto específico con su cantidad y precio pactado
 * al momento de crear o editar la orden. El campo 'unitPrice' se persiste en
 * el detalle (no se toma del producto) para garantizar la inmutabilidad del
 * historial contable — si el precio del producto cambia después, la orden
 * conserva el precio original negociado.
 *
 * Los detalles solo pueden crearse, editarse o eliminarse cuando la orden
 * padre está en estado PENDING. Esta restricción se aplica en el servicio.
 *
 * La relación con PurchaseOrder usa ON DELETE CASCADE en la BD y
 * orphanRemoval=true en el modelo — ambos alineados para garantizar que
 * al eliminar una orden sus detalles se eliminen físicamente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_order_details")
public class PurchaseOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de unidades del producto en esta línea de detalle.
     *  Siempre positivo — validado en el DTO con @Min(1) y en la BD con CHECK > 0. */
    @Column(nullable = false)
    private int quantity;

    /** Precio unitario pactado al momento de crear o editar el detalle.
     *  Inmutable una vez que la orden sale de PENDING — preserva el historial
     *  contable independientemente de cambios futuros en Product.price. */
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    /** Resultado de quantity × unitPrice, calculado por el servicio.
     *  Nunca lo envía el cliente. Se recalcula automáticamente en
     *  addDetail y updateDetail. Su suma determina totalAmount de la orden. */
    @Column(nullable = false)
    private BigDecimal subtotal;

    /** Orden de compra a la que pertenece este detalle.
     *  FetchType.LAZY — la orden no se carga automáticamente al consultar
     *  detalles individuales, evitando queries innecesarias. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    /** Producto referenciado por este detalle.
     *  ON DELETE RESTRICT en la BD protege el historial — no se puede eliminar
     *  un producto que tenga detalles de compra registrados.
     *  FetchType.LAZY evita cargar el producto completo al procesar la orden. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
