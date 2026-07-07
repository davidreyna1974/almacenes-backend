package com.codigo2enter.almacenes.integration;

import com.codigo2enter.almacenes.core.config.RoleInitializer;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración: verifica que, tras el arranque REAL de la aplicación, los 4
 * roles de referencia obligatorios existen en la base de datos — sembrados de forma
 * automática por {@link RoleInitializer} en el evento ApplicationReadyEvent, sin
 * ninguna inserción manual.
 *
 * Candado anti-regresión: complementa a {@code RoleInitializerTest} (unit, con mocks).
 * Aquí se valida el comportamiento REAL en un contexto Spring completo contra
 * PostgreSQL, es decir, que la app aprovisiona sus propios datos de referencia al
 * arrancar (dev, test, CI y prod), sin depender de un paso manual ni de otros tests.
 *
 * Usa {@code webEnvironment = RANDOM_PORT} para reutilizar el mismo contexto cacheado
 * que los demás @SpringBootTest de integración (sin arrancar un contexto adicional).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleSeedingIntegrationTest {

    @Autowired
    RoleRepository roleRepository;

    @Test
    void trasElArranque_los4RolesDeReferenciaExisten() {
        for (String name : RoleInitializer.REQUIRED_ROLES) {
            assertTrue(
                    roleRepository.findByName(name).isPresent(),
                    "El rol de referencia '" + name + "' debe existir tras el arranque "
                            + "(lo siembra RoleInitializer, sin inserción manual)");
        }
    }
}
