package com.codigo2enter.almacenes.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración global de OpenAPI 3.0 / Swagger UI.
 *
 * Por qué esta clase existe: springdoc-openapi auto-descubre los controladores
 * y genera la especificación automáticamente, pero necesita que le indiquemos
 * el esquema de autenticación (Bearer JWT) para que el botón "Authorize" del
 * Swagger UI funcione. Sin esta configuración, el usuario tendría que agregar
 * el header manualmente en cada petición del UI — aquí lo centralizamos.
 *
 * El SecurityRequirement.addList("Bearer") indica que TODOS los endpoints
 * requieren el esquema "Bearer" por defecto; los endpoints públicos
 * (/auth/login, /swagger-ui, /v3/api-docs) están exentos en SecurityConfig,
 * no aquí.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Almacenes API")
                        .description("Backend REST API para gestión de almacenes. " +
                                "Autenticarse con POST /api/v1/auth/login para obtener el JWT " +
                                "y luego usar el botón 'Authorize' con 'Bearer <token>'.")
                        .version("1.0.0")
                        .contact(new Contact().name("Código2Enter")))
                // Aplica el esquema "Bearer" como requisito global de seguridad.
                // Esto hace que el candado aparezca en todos los endpoints del UI.
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Bearer")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT obtenido desde POST /api/v1/auth/login")));
    }
}
