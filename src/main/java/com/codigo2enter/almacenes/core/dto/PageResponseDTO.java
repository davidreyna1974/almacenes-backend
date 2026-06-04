package com.codigo2enter.almacenes.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * DTO genérico de respuesta paginada.
 *
 * Justificación: Spring Data devuelve {@code Page<T>} con metadatos útiles
 * (número de páginas, total de elementos, etc.), pero exponer ese tipo
 * directamente en la API acoplaría el frontend a la estructura interna de
 * Spring. Este DTO actúa como capa de traducción, exponiendo solo los campos
 * necesarios en un formato JSON limpio y estable.
 *
 * Flujo de uso en controladores:
 * <pre>
 *   Page&lt;Xxx&gt; page = xxxRepository.findByActiveTrue(pageable);
 *   return ResponseEntity.ok(PageResponseDTO.from(page.map(mapper::toDTO)));
 * </pre>
 *
 * Criterio de éxito: el frontend puede consumir los campos {@code content},
 * {@code currentPage}, {@code totalPages} y {@code totalElements} sin importar
 * la versión de Spring que use el backend.
 *
 * @param <T> tipo de los elementos de la página (normalmente un DTO de respuesta)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {

    /** Registros de la página actual. */
    private List<T> content;

    /** Número de página actual (base 0). */
    private int currentPage;

    /** Total de páginas disponibles. */
    private int totalPages;

    /** Total de registros en la BD (sin paginación). */
    private long totalElements;

    /** Tamaño de página solicitado. */
    private int size;

    /** true si es la primera página. */
    private boolean first;

    /** true si es la última página. */
    private boolean last;

    /**
     * Factory method para construir un PageResponseDTO a partir de un Page de Spring Data.
     *
     * Se usa como método estático en lugar del builder para centralizar la
     * conversión en un solo lugar y evitar repetición en los servicios.
     *
     * @param page página de Spring Data ya mapeada al tipo T
     * @param <T>  tipo del contenido
     * @return PageResponseDTO listo para serializar como JSON
     */
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return PageResponseDTO.<T>builder()
                .content(page.getContent())
                .currentPage(page.getNumber())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .size(page.getSize())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
