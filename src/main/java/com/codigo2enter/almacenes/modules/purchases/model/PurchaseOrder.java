package com.codigo2enter.almacenes.modules.purchases.model;

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
 * Entidad JPA que representa la cabecera de una orden de compra.
 *
 * Sigue una máquina de estados estricta: PENDING → APPROVED → RECEIVED (terminal)
 * con cancelación posible desde PENDING o APPROVED. El servicio valida cada
 * transición antes de ejecutarla.
 *
 * El campo 'createdBy' registra qué usuario del sistema generó la orden.
 * Se resuelve automáticamente desde SecurityContextHolder en el servicio —
 * el cliente nunca lo envía en el request body.
 *
 * 'totalAmount' es calculado por el servicio como suma de los subtotales
 * de todos los detalles. El cliente nunca lo envía directamente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número único de orden en formato OC-YYYY-NNNN.
     *  Generado por el servicio con un contador anual reiniciable.
     *  El cliente nunca lo envía — evita duplicados y fraudes. */
    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    /** Estado actual de la orden en su ciclo de vida.
     *  EnumType.STRING almacena "PENDING", "APPROVED", etc. en lugar del
     *  índice numérico, haciendo los registros legibles y resistentes a
     *  cambios en el orden de declaración del enum. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.PENDING;

    /** Notas o instrucciones adicionales para la orden.
     *  Editable únicamente cuando status == PENDING. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Suma de todos los subtotales de los detalles.
     *  Recalculado por el servicio en cada addDetail, updateDetail y removeDetail.
     *  Inicializado en ZERO — se actualiza tras agregar los primeros detalles. */
    @Builder.Default
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Proveedor al que se realiza la compra.
     *  FetchType.LAZY — solo se carga cuando se accede explícitamente,
     *  evitando queries innecesarias al listar órdenes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Usuario que creó la orden.
     *  updatable=false garantiza que Hibernate nunca actualice esta columna
     *  después de la inserción inicial — campo de auditoría inmutable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    /** Líneas de detalle de la orden (productos y cantidades).
     *  cascade=ALL propaga las operaciones de persistencia a los detalles.
     *  orphanRemoval=true elimina físicamente los detalles huérfanos cuando
     *  se remueven de la lista — alineado con ON DELETE CASCADE en la BD. */
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL,
               orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderDetail> details = new ArrayList<>();

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Fecha de última modificación. Actualizado por el servicio en cada
     *  operación de escritura: updateOrder, approveOrder, receiveOrder,
     *  cancelOrder, addDetail, updateDetail, removeDetail. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Asignado cuando la orden transiciona a APPROVED. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Asignado cuando la orden transiciona a RECEIVED.
     *  En este momento el servicio dispara los movimientos de inventario. */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** Asignado cuando la orden transiciona a CANCELLED. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Usuario que aprobó la orden. Null hasta que la orden pasa a APPROVED.
     *  Sin updatable=false porque el campo comienza null y se establece por primera
     *  vez en el UPDATE de aprobación. La inmutabilidad se garantiza a nivel de
     *  negocio en PurchaseOrderServiceImpl (no se puede re-aprobar una orden). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    /** Usuario que marcó la orden como recibida físicamente.
     *  Null hasta que la orden pasa a RECEIVED. Mismo razonamiento que approvedBy. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    /** Usuario que canceló la orden. Null en órdenes no canceladas.
     *  Mismo razonamiento que approvedBy. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;
}
