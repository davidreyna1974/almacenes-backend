package com.codigo2enter.almacenes.core.exception;

/**
 * Lanzada cuando una operación viola una regla de negocio (stock insuficiente,
 * entidad con dependencias activas, tipo de movimiento inválido, etc.).
 * El GlobalExceptionHandler la convierte en HTTP 422 Unprocessable Entity.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
