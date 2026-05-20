package com.codigo2enter.almacenes.modules.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
