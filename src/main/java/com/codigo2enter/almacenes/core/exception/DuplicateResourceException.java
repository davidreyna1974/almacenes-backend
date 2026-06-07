package com.codigo2enter.almacenes.core.exception;

/**
 * Lanzada cuando se intenta crear o actualizar un recurso con un valor
 * que viola una restricción de unicidad (SKU, nombre de categoría, etc.).
 * El GlobalExceptionHandler la convierte en HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
