package com.codigo2enter.almacenes.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Punto de entrada de autenticación para peticiones sin JWT, con JWT inválido
 * o con JWT expirado.
 *
 * Sin este componente, Spring Security usa por defecto Http403ForbiddenEntryPoint,
 * que responde 403 Forbidden tanto para "no autenticado" como para "autenticado
 * pero sin el rol requerido" — el cliente no puede distinguir ambos casos.
 *
 * Este handler responde 401 Unauthorized cuando NO hay autenticación válida en
 * el SecurityContext (token ausente, malformado o expirado). El frontend
 * (error.interceptor.ts) detecta el 401, cierra la sesión local y redirige a
 * /login con el mensaje "Tu sesión ha expirado. Inicia sesión nuevamente."
 *
 * El formato del body replica el de GlobalExceptionHandler.buildResponse() para
 * mantener un contrato de error consistente en toda la API.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("message", "Token JWT ausente, inválido o expirado. Inicia sesión nuevamente.");

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
