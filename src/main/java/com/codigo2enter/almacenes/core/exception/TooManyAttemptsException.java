package com.codigo2enter.almacenes.core.exception;

/**
 * Lanzada cuando un usuario supera el número máximo de intentos de login
 * fallidos y debe esperar antes de volver a intentarlo.
 * El GlobalExceptionHandler la convierte en HTTP 429 Too Many Requests.
 */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException(String message) {
        super(message);
    }
}
