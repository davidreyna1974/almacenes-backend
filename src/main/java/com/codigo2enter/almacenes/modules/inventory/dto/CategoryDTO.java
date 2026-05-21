package com.codigo2enter.almacenes.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO unificado para la entidad Category.
 *
 * Se usa tanto como objeto de entrada (request) para crear y actualizar
 * categorías, como objeto de salida (response) para devolverlas al cliente.
 *
 * El campo 'id' es ignorado por el mapper al crear (toEntity), ya que
 * PostgreSQL lo genera automáticamente. Se incluye aquí para que la misma
 * clase sirva como respuesta con el ID asignado tras la persistencia.
 *
 * El campo 'active' tampoco se recibe en el request de creación —
 * el servicio lo inicializa en true. Se incluye para exponerlo en la
 * respuesta y permitir actualizaciones de estado desde el frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    /** Identificador generado por la base de datos. Null en requests de creación. */
    private Long id;

    /** Nombre único de la categoría. El servicio valida que no exista otro
     *  registro con el mismo nombre antes de persistir (regla de unicidad de negocio). */
    @NotBlank(message = "El nombre de categoría es obligatorio")
    @Size(max = 80)
    private String name;

    /** Descripción opcional que amplía el contexto de la categoría. */
    @Size(max = 255)
    private String description;

    /** Estado lógico de la categoría. true = activa, false = dada de baja.
     *  El frontend usa este campo para mostrar u ocultar la categoría
     *  en los selectores de asignación de productos. */
    private boolean active;
}
