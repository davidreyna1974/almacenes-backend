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
     * Entidad no encontrada → HTTP 404 Not Found.
     * Lanzada desde los servicios cuando findById / findBySku / findByName
     * no encuentran el registro solicitado.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Restricción de unicidad violada → HTTP 409 Conflict.
     * Lanzada cuando se intenta crear o actualizar con un SKU o nombre ya existente.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Regla de negocio violada → HTTP 422 Unprocessable Entity.
     * Lanzada por stock insuficiente, tipo de movimiento inválido,
     * categoría con productos activos, etc.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /**
     * Captura las RuntimeException no tipadas que queden en el sistema.
     * Solo deben llegar aquí errores genuinos de infraestructura
     * (usuario autenticado no encontrado, fallo de BD, etc.) — cualquier
     * error de negocio debe lanzar una excepción específica.
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
