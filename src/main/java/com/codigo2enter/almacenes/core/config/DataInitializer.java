package com.codigo2enter.almacenes.core.config;

import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Inicializador de datos para el primer arranque del sistema.
 *
 * Resuelve el problema de bootstrap (chicken-and-egg): sin usuarios no hay ADMIN,
 * y sin ADMIN no se pueden crear usuarios a través de la API.
 *
 * Si la tabla users está vacía al arrancar, crea automáticamente un usuario
 * administrador con credenciales conocidas. El administrador debe cambiar
 * su contraseña en el primer uso productivo mediante PUT /api/v1/auth/me/password.
 *
 * Credenciales por defecto:
 *   username: admin
 *   password: Admin123!
 *   roles: ROLE_ADMIN, ROLE_WAREHOUSEMAN
 *
 * En producción: externalizar estas credenciales a variables de entorno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultAdmin() {
        if (userRepository.count() > 0) {
            return;
        }

        log.warn("=======================================================");
        log.warn("  PRIMER ARRANQUE: creando usuario administrador por defecto");
        log.warn("  Usuario: admin | Password: Admin123!");
        log.warn("  Cambia la contraseña en: PUT /api/v1/auth/me/password");
        log.warn("=======================================================");

        Set<Role> roles = new HashSet<>();
        roleRepository.findByName("ROLE_ADMIN").ifPresent(roles::add);
        roleRepository.findByName("ROLE_WAREHOUSEMAN").ifPresent(roles::add);

        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("Admin123!"))
                .email("admin@almacenes.com")
                .roles(roles)
                .build();

        userRepository.save(admin);
        log.info("Usuario administrador por defecto creado exitosamente.");
    }
}
