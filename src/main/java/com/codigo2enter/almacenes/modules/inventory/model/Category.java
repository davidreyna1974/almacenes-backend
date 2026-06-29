package com.codigo2enter.almacenes.modules.inventory.model;

import com.codigo2enter.almacenes.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa una categoría de productos en el almacén.
 *
 * Permite agrupar productos bajo una clasificación común (ej. "Electrónica",
 * "Herramientas", "Consumibles"). La eliminación es lógica (soft delete)
 * mediante el campo 'active', preservando la integridad referencial con
 * los productos asociados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre único de la categoría. Se valida unicidad a nivel de base de datos
     *  y a nivel de negocio en CategoryServiceImpl antes de persistir. */
    @Column(nullable = false, unique = true, length = 80)
    private String name;

    /** Descripción opcional que amplía el significado de la categoría. */
    @Column(length = 255)
    private String description;

    /** Indica si la categoría está activa en el sistema.
     *  false = eliminada lógicamente; los productos asociados quedan huérfanos
     *  de categoría activa y deben reasignarse antes de la desactivación. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Fecha de creación. @Builder.Default garantiza que Hibernate nunca la deje null.
     *  updatable=false: la fecha de alta es un dato histórico inmutable. */
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Usuario que creó la categoría. updatable=false — el creador es inmutable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    /** Fecha de la última modificación. Null si la categoría nunca fue editada. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Usuario que realizó la última modificación. Null si nunca fue editada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
