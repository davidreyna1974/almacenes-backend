package com.codigo2enter.almacenes.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filtro de seguridad que intercepta cada petición HTTP para validar el JWT.
 *
 * Hereda de OncePerRequestFilter, lo que garantiza que la lógica de validación
 * se ejecuta exactamente una vez por petición, sin importar cuántos servlets
 * internos estén encadenados en la misma solicitud.
 *
 * Posición en la cadena de filtros de Spring Security:
 *   petición HTTP → JwtAuthenticationFilter → resto de filtros → controlador
 *
 * Si el token es válido, inyecta la autenticación en el SecurityContextHolder
 * para que los filtros posteriores y los controladores reconozcan al usuario.
 * Si no hay token o es inválido, la petición continúa sin autenticación y
 * Spring Security decidirá si permite o rechaza el acceso según las reglas
 * definidas en SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // JwtUtils provee los métodos para extraer y validar datos del token.
    private final JwtUtils jwtUtils;

    /**
     * Lógica principal del filtro. Se ejecuta en cada petición HTTP entrante.
     *
     * @param request     petición HTTP recibida
     * @param response    respuesta HTTP que se construirá
     * @param filterChain cadena de filtros — se debe invocar al final para
     *                    continuar con el siguiente filtro o el controlador
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // --- 1. Extraer la cabecera Authorization ---
        // El estándar JWT establece que el token viaja en esta cabecera
        // con el prefijo "Bearer " (con espacio al final).
        // Ejemplo: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
        String authHeader = request.getHeader("Authorization");

        // --- 2. Validar que la cabecera existe y tiene el formato correcto ---
        // Si no hay cabecera o no comienza con "Bearer ", la petición no
        // intenta autenticarse con JWT. Se deja pasar al siguiente filtro
        // sin registrar ninguna autenticación en el contexto.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // --- 3. Extraer el token eliminando el prefijo "Bearer " (7 caracteres) ---
        String token = authHeader.substring(7);

        // --- 4. Recuperar el username del payload del token ---
        // Si el token está malformado o su firma es inválida, extractUsername lanza
        // una excepción. La capturamos y pasamos la petición sin autenticar —
        // Spring Security la rechazará con 403 al llegar a la ruta protegida.
        String username;
        try {
            username = jwtUtils.extractUsername(token);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // --- 5. Validar el token y registrar la autenticación ---
        // La doble condición evita sobreescribir una autenticación que ya
        // fue establecida anteriormente en la misma cadena de filtros.
        // getContext().getAuthentication() == null significa que esta petición
        // todavía no tiene un usuario autenticado registrado.
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // validateToken verifica la firma HMAC-SHA256 y que el token no haya expirado.
            if (jwtUtils.validateToken(token)) {

                // UsernamePasswordAuthenticationToken es el objeto estándar de Spring Security
                // para representar a un usuario autenticado dentro del contexto.
                //
                // Constructor de 3 parámetros (token ya autenticado):
                //   - principal   : identidad del usuario (username)
                //   - credentials : null — no se necesitan las credenciales después de validar el JWT
                //   - authorities : lista de roles/permisos — vacía por ahora; en la siguiente
                //                   fase se extraerán los roles del claim del token.
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.emptyList()
                );

                // Enriquece el token de autenticación con detalles de la petición HTTP
                // (IP del cliente, session ID). Útil para auditoría y logging.
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Registra la autenticación en el contexto de la petición actual.
                // A partir de este punto, cualquier componente (filtros posteriores,
                // controladores, servicios) puede consultar SecurityContextHolder
                // para saber quién es el usuario autenticado.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // --- 6. Continuar con la cadena de filtros ---
        // Obligatorio al final de todo filtro: pasa el control al siguiente
        // eslabón de la cadena (otros filtros o el DispatcherServlet).
        filterChain.doFilter(request, response);
    }
}
