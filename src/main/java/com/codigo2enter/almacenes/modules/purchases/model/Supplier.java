package com.codigo2enter.almacenes.modules.purchases.model;

import com.codigo2enter.almacenes.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un proveedor en el sistema de compras.
 *
 * La desactivación es lógica (soft delete con campo 'active') para preservar
 * la integridad referencial — las órdenes de compra históricas conservan
 * la referencia al proveedor aunque este haya sido dado de baja.
 *
 * El RFC es el identificador fiscal único del proveedor ante el SAT (México).
 * Se valida unicidad a nivel de base de datos (UNIQUE constraint) y a nivel
 * de negocio en SupplierServiceImpl antes de crear o actualizar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RFC fiscal mexicano — identificador único del proveedor ante el SAT.
     *  Máximo 13 caracteres. La unicidad se garantiza por constraint en BD
     *  y por validación de negocio en el servicio. */
    @Column(nullable = false, unique = true, length = 13)
    private String rfc;

    /** Razón social del proveedor.
     *  Mapeado a 'company_name' en la tabla. La unicidad se valida en el
     *  servicio (no en BD) para permitir comparaciones case-insensitive
     *  o con normalización de espacios si fuera necesario. */
    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    /** Nombre de la persona de contacto en el proveedor. */
    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 20)
    private String phone;

    /** Email de contacto. Unicidad garantizada por constraint en BD. */
    @Column(unique = true, length = 100)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    /** Soft delete — false indica que el proveedor fue dado de baja lógicamente.
     *  El servicio valida que no existan órdenes PENDING o APPROVED antes de
     *  desactivar para evitar órdenes huérfanas sin proveedor activo. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Usuario que registró al proveedor. updatable=false — el creador es inmutable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    /** Fecha de la última modificación de los datos del proveedor. Null si nunca fue editado. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Usuario que realizó la última modificación. Null si el proveedor nunca fue editado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
