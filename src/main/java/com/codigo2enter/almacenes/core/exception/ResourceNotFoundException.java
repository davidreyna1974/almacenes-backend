package com.codigo2enter.almacenes.core.exception;

/**
 * Lanzada cuando una entidad solicitada no existe en la base de datos.
 * El GlobalExceptionHandler la convierte en HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
