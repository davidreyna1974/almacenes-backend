package com.codigo2enter.almacenes.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Componente utilitario para la generación, validación y extracción de datos
 * de tokens JWT (JSON Web Token) utilizando la librería JJWT 0.12.x.
 *
 * Un JWT está compuesto por tres partes separadas por puntos:
 *   1. Header  — algoritmo de firma (HS256)
 *   2. Payload — claims (datos del usuario: username, roles, fechas)
 *   3. Signature — HMAC-SHA256 aplicado con la clave secreta
 *
 * Esta clase vive en 'core/security' y trabaja exclusivamente con tipos
 * primitivos (String, Set<String>) para no acoplarse a las entidades de dominio.
 */
@Component
public class JwtUtils {

    /**
     * Clave secreta de al menos 256 bits (64 caracteres hex) requerida por HMAC-SHA256.
     * En producción este valor debe externalizarse a una variable de entorno
     * o a un gestor de secretos (Vault, AWS Secrets Manager, etc.).
     */
    private static final String SECRET = "4a8f3b2e9c1d7f6a0b5e2c8d4f1a9b3e7c0d6f2a5b8e3c1d9f4a7b0e2c6d8f1";

    /**
     * Tiempo de vida del token: 2 horas expresadas en milisegundos (2 * 60 * 60 * 1000).
     * Pasado este tiempo el token es inválido aunque su firma sea correcta.
     */
    private static final long EXPIRATION_MS = 7_200_000L;

    /**
     * Convierte la clave secreta en texto plano a un objeto SecretKey seguro
     * compatible con HMAC-SHA256. JJWT requiere este tipo para firmar y verificar.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un token JWT firmado con los datos del usuario autenticado.
     *
     * Estructura del payload generado:
     *   - sub   : nombre de usuario (subject estándar de JWT)
     *   - roles : conjunto de nombres de roles (claim personalizado)
     *   - iat   : fecha/hora de emisión (issued at)
     *   - exp   : fecha/hora de expiración (issued at + EXPIRATION_MS)
     *
     * @param username nombre de usuario que se almacena como subject del token
     * @param roles    nombres de los roles asignados (ej. "ROLE_WAREHOUSEMAN")
     * @return token JWT compacto en formato "header.payload.signature"
     */
    public String generateToken(String username, Set<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrae la lista de roles almacenada en el claim 'roles' del token.
     *
     * El claim 'roles' se almacena como una lista de Strings (ej. ["ROLE_ADMIN","ROLE_WAREHOUSEMAN"]).
     * Se usa en JwtAuthenticationFilter para construir las GrantedAuthority del usuario
     * sin necesidad de consultar la BD en cada request.
     *
     * @param token JWT compacto del que extraer los roles
     * @return lista de nombres de roles; lista vacía si el claim no existe o no es una lista
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = parseClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    /**
     * Extrae el nombre de usuario almacenado en el claim 'sub' del token.
     * Utilizado por el filtro de seguridad para identificar al usuario en cada petición.
     *
     * @param token JWT compacto recibido en la cabecera Authorization
     * @return nombre de usuario contenido en el subject del token
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Valida que el token sea auténtico y no haya expirado.
     *
     * La verificación que realiza JJWT internamente en parseClaims():
     *   1. Reconstruye la firma con la clave secreta y la compara con la del token.
     *   2. Comprueba que la fecha 'exp' sea posterior al momento actual.
     * Si cualquiera de los dos pasos falla, lanza una JwtException.
     *
     * @param token JWT compacto a verificar
     * @return true si la firma es válida y el token no ha expirado; false en caso contrario
     */
    public Boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Método privado reutilizable que parsea y verifica el token JWT.
     * Centraliza la lógica de parsing para que generateToken y validateToken
     * no dupliquen la configuración del parser.
     *
     * @param token JWT compacto a parsear
     * @return Claims con todos los datos del payload
     * @throws JwtException si la firma no coincide o el token ha expirado
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
