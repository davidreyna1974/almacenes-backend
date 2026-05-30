package com.codigo2enter.almacenes.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para todos los controladores REST.
 *
 * Sin este componente, las excepciones no capturadas hacen que Spring Boot
 * redirija internamente a /error. Dado que /error no está en permitAll() de
 * SecurityConfig, Spring Security lo intercepta y devuelve 403 Forbidden en
 * lugar del 500 esperado — comportamiento confuso para el cliente.
 *
 * @RestControllerAdvice intercepta las excepciones ANTES de que lleguen al
 * mecanismo de redirección de Spring Boot, devolviendo la respuesta directamente
 * desde el handler con el código HTTP correcto y un cuerpo JSON legible.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura todas las RuntimeException lanzadas desde los servicios.
     *
     * Los servicios del sistema usan RuntimeException para señalar violaciones
     * de reglas de negocio (SKU duplicado, stock insuficiente, estado inválido,
     * entidad no encontrada). Se mapean a 500 para mantener compatibilidad con
     * el comportamiento documentado en el archivo de pruebas E2E.
     *
     * Mejora futura: distinguir por tipo de excepción (NotFoundException → 404,
     * DuplicateException → 409, ValidationException → 400) para comunicar mejor
     * la naturaleza del error al cliente.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    /**
     * Defensa en profundidad para colisiones de Optimistic Locking.
     *
     * SaleOrderServiceImpl.approveOrder() usa saveAndFlush() para que esta excepción
     * se lance dentro del try-catch del servicio y sea convertida a un RuntimeException
     * con mensaje de negocio claro ("concurrentemente"). Este handler cubre el caso
     * en que el flush ocurra fuera del try-catch (p.ej. si otro código llama save()
     * sin flush explícito) y la excepción llegue sin convertir al controlador.
     *
     * Se retorna 409 Conflict en lugar de 500 porque la operación falló por contención
     * externa, no por un error interno del servidor — el cliente puede reintentar.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT,
            "Stock modificado concurrentemente. Intente nuevamente.");
    }

    /**
     * Captura errores de validación Jakarta (@Valid en @RequestBody).
     *
     * Cuando un campo del DTO no supera una restricción (@NotBlank, @Min, etc.),
     * Spring lanza MethodArgumentNotValidException antes de llegar al servicio.
     * Se mapea a 400 Bad Request con la lista de campos que fallaron.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, "Validación fallida: " + errors);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
