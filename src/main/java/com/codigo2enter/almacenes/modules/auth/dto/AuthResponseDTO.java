package com.codigo2enter.almacenes.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de salida para el endpoint de autenticación POST /api/v1/auth/login.
 *
 * Contiene el JWT generado tras una autenticación exitosa. El cliente
 * (Angular) almacena este token (normalmente en localStorage o sessionStorage)
 * y lo adjunta en la cabecera de cada petición subsiguiente:
 *
 *   Authorization: Bearer <token>
 *
 * El token es autocontenido — incluye username, roles y fecha de expiración
 * en su payload, por lo que el servidor no necesita sesiones ni base de datos
 * para validar peticiones posteriores.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {

    // JWT firmado con HMAC-SHA256, válido por 2 horas desde su emisión.
    // Formato: "header.payload.signature" (tres segmentos en Base64 separados por puntos).
    private String token;
}
