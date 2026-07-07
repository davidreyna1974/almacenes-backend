package com.codigo2enter.almacenes.core.config;

import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Siembra los roles de referencia OBLIGATORIOS del sistema (RBAC).
 *
 * Los 4 roles son datos de referencia estáticos e imprescindibles: sin ellos el
 * control de acceso por rol no funciona y {@link DataInitializer} no puede asignar
 * roles al administrador por defecto. Antes, estos roles se insertaban a mano en la
 * BD (paso manual documentado en la propuesta de refactorización de auth) o los
 * creaban los propios tests bajo demanda — un anti-patrón: un dato imprescindible
 * dependía de intervención humana o de la ejecución de pruebas.
 *
 * Este componente los garantiza de forma AUTOMÁTICA e IDEMPOTENTE en cada arranque
 * de la aplicación, en TODOS los entornos (dev, test, CI, prod), sin ninguna
 * inserción manual. Es idempotente: solo crea los roles que falten.
 *
 * Se ejecuta ANTES que {@link DataInitializer} ({@code @Order(1)} vs {@code @Order(2)})
 * para que, al crear el admin por defecto, ROLE_ADMIN ya exista.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInitializer {

    /** Roles de referencia del sistema (enum cerrado del dominio RBAC). */
    public static final List<String> REQUIRED_ROLES =
            List.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAREHOUSEMAN", "ROLE_SALES");

    private final RoleRepository roleRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void ensureRoles() {
        int created = 0;
        for (String name : REQUIRED_ROLES) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(Role.builder().name(name).build());
                created++;
            }
        }
        if (created > 0) {
            log.info("RoleInitializer: {} rol(es) de referencia creado(s). Roles requeridos: {}.",
                    created, REQUIRED_ROLES);
        }
    }
}
