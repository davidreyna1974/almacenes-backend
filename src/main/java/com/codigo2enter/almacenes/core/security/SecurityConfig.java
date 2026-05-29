package com.codigo2enter.almacenes.core.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración central de Spring Security para la aplicación.
 *
 * @Configuration  — marca la clase como fuente de beans de Spring.
 * @EnableWebSecurity — activa el módulo de seguridad web de Spring Security,
 *                      reemplazando la configuración por defecto automática.
 * @RequiredArgsConstructor — Lombok genera el constructor con los campos 'final',
 *                            permitiendo la inyección de JwtAuthenticationFilter
 *                            sin necesidad de @Autowired.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Filtro personalizado que intercepta cada petición para validar el JWT.
     * Se inyecta aquí para registrarlo en la cadena de filtros de Spring Security.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Registra BCryptPasswordEncoder como bean disponible en todo el contexto.
     *
     * BCrypt aplica un algoritmo de hashing adaptativo con sal aleatoria,
     * lo que lo hace resistente a ataques de fuerza bruta y tablas rainbow.
     * Se usa en UserServiceImpl para cifrar contraseñas antes de persistirlas
     * y para verificarlas durante el login.
     *
     * Se declara con el tipo de interfaz PasswordEncoder (no BCryptPasswordEncoder)
     * para que el código dependiente esté desacoplado de la implementación concreta.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Define las reglas de seguridad HTTP de la aplicación.
     *
     * Spring Security aplica estos filtros en el orden en que se configuran.
     * El objeto HttpSecurity usa una API fluida (builder pattern) donde cada
     * llamada encadenada configura un aspecto distinto de la seguridad.
     *
     * @param http objeto builder de Spring Security para configurar la seguridad HTTP
     * @return la cadena de filtros de seguridad construida y lista para ser aplicada
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 0. CORS — permite peticiones desde Postman Web y futuros clientes Angular.
            //    Se habilita aquí para que Spring Security aplique los headers CORS
            //    antes que cualquier otro filtro de seguridad (incluyendo el 403 por JWT).
            //    Para desarrollo se permite cualquier origen; en producción se restringe
            //    al dominio del frontend.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 1. DESHABILITAR CSRF
            //    CSRF (Cross-Site Request Forgery) es un mecanismo de protección basado
            //    en tokens de sesión. En APIs REST stateless con JWT, este mecanismo
            //    no aplica — el token JWT ya cumple esa función de autenticidad.
            //    Dejarlo activo causaría errores 403 Forbidden en todas las peticiones
            //    POST/PUT/DELETE desde Angular.
            .csrf(AbstractHttpConfigurer::disable)

            // 2. REGLAS DE AUTORIZACIÓN POR RUTA
            //    Se evalúan en orden: la primera regla que coincide con la ruta gana.
            .authorizeHttpRequests(auth -> auth
                // Rutas públicas: registro y login no requieren token JWT.
                // El patrón /api/v1/auth/** cubre /register, /login y cualquier
                // ruta futura bajo ese prefijo.
                .requestMatchers("/api/v1/auth/**").permitAll()

                // Cualquier otra ruta del sistema requiere un JWT válido.
                // Si la petición no tiene el header Authorization o el token
                // es inválido/expirado, Spring retorna HTTP 403 Forbidden.
                .anyRequest().authenticated()
            )

            // 3. POLÍTICA DE SESIONES: SIN ESTADO (STATELESS)
            //    Le indica a Spring Security que NO cree ni use sesiones HTTP
            //    en el servidor (HttpSession). Cada petición debe autenticarse
            //    por sí sola presentando su token JWT.
            //    Esto es fundamental para APIs REST escalables — sin estado en
            //    el servidor significa que cualquier instancia puede atender
            //    cualquier petición sin compartir sesiones.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 4. REGISTRAR EL FILTRO JWT EN LA CADENA DE SEGURIDAD
            //    addFilterBefore coloca JwtAuthenticationFilter ANTES de
            //    UsernamePasswordAuthenticationFilter (el filtro estándar de
            //    Spring que procesa formularios de login con usuario/contraseña).
            //    Esto garantiza que el token JWT sea validado primero, y si es
            //    válido, la autenticación queda establecida en el SecurityContext
            //    antes de que los filtros posteriores la necesiten.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración CORS para desarrollo — permite cualquier origen, método y header.
     * Habilita el flujo OPTIONS (preflight) requerido por los navegadores antes de
     * enviar peticiones cross-origin con headers personalizados como Authorization.
     * En producción se reemplaza la lista de orígenes por el dominio del frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
