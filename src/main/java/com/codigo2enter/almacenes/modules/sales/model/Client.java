package com.codigo2enter.almacenes.modules.sales.model;

import com.codigo2enter.almacenes.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad que representa a un cliente comprador.
 *
 * Análogo a Supplier en el módulo purchases — gestiona el catálogo de clientes
 * a los que se les puede emitir una orden de venta. El RFC es opcional porque
 * no todos los clientes son personas morales con RFC registrado; el email es
 * único cuando se proporciona.
 *
 * Soft delete con active=false — nunca se elimina un registro físicamente para
 * preservar la trazabilidad de órdenes históricas asociadas al cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** RFC opcional — no todos los clientes son personas morales. */
    @Column(length = 13, unique = true)
    private String rfc;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 20)
    private String phone;

    @Column(unique = true, length = 100)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * updatable = false — el creador del registro es inmutable.
     * Contrasta con updatedBy que sí puede cambiar en cada edición.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
