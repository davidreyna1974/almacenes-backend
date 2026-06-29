package com.codigo2enter.almacenes.modules.sales.model;

import com.codigo2enter.almacenes.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de una orden de venta.
 *
 * Sigue la misma estructura que PurchaseOrder con tres diferencias clave:
 *
 *   1. El campo de transición es deliveredBy/deliveredAt (no receivedBy/receivedAt).
 *   2. Los campos approvedBy, deliveredBy, cancelledBy NO tienen updatable=false —
 *      comienzan como null y se asignan en el UPDATE de cada transición. La regla
 *      aprendida en purchases: updatable=false impide que Hibernate incluya el
 *      campo en el UPDATE statement, dejando el valor en null en BD aunque en
 *      memoria esté seteado.
 *   3. updatedBy está presente porque SaleOrder admite edición de notas/cliente
 *      mientras está en PENDING.
 *
 * Relación con detalles: cascade = ALL + orphanRemoval = true para que eliminar
 * un elemento de la lista también elimine el registro en BD (equivalente a
 * ON DELETE CASCADE a nivel de ORM).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sale_orders")
public class SaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleOrderStatus status = SaleOrderStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /**
     * createdBy es inmutable — updatable=false garantiza que Hibernate
     * nunca lo sobreescriba en un UPDATE posterior.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** Sin updatable=false — comienza null, se asigna en el UPDATE de aprobación. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    /** Sin updatable=false — comienza null, se asigna en el UPDATE de entrega. */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by")
    private User deliveredBy;

    /** Sin updatable=false — comienza null, se asigna en el UPDATE de cancelación. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @OneToMany(mappedBy = "saleOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleOrderDetail> details = new ArrayList<>();
}
