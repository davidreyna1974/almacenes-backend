package com.codigo2enter.almacenes.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador de acceso denegado para peticiones con JWT válido pero cuyo
 * usuario NO tiene el rol requerido por la ruta (data.roles en
 * SecurityConfig.authorizeHttpRequests()).
 *
 * Responde 403 Forbidden — distinto del 401 de JwtAuthenticationEntryPoint,
 * que cubre "no autenticado / token expirado". El frontend (error.interceptor.ts)
 * muestra "No tienes permiso para realizar esta acción." para 403 sin cerrar sesión.
 *
 * El formato del body replica el de GlobalExceptionHandler.buildResponse() para
 * mantener un contrato de error consistente en toda la API.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        body.put("message", "No tienes permiso para realizar esta acción.");

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
