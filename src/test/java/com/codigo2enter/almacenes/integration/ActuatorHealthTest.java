package com.codigo2enter.almacenes.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el endpoint de Spring Boot Actuator habilitado para BACK-I8.
 *
 * Contexto: la guía de puesta en producción (§9 05-verify.sh, §11 smoke test)
 * valida la salud del backend con:
 *     wget -qO- http://localhost:8080/actuator/health  →  {"status":"UP"}
 *
 * Estos tests ejercen exactamente ese contrato sobre un servidor real
 * (@SpringBootTest RANDOM_PORT + TestRestTemplate), sin autenticación, para
 * confirmar dos cosas a la vez:
 *   1) la dependencia spring-boot-starter-actuator está presente y el endpoint
 *      /actuator/health responde 200 con {"status":"UP"};
 *   2) SecurityConfig abre /actuator/health sin requerir JWT (permitAll).
 *
 * Antes de BACK-I8 la dependencia no existía y el endpoint devolvía 404, lo que
 * hacía fallar el smoke test del go-live sin que ningún test lo detectara.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;

    @Test
    void health_sinAutenticacion_retorna200YStatusUp() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "/actuator/health debe responder 200 sin autenticación (smoke test del go-live)");
        assertNotNull(resp.getBody(), "El cuerpo de /actuator/health no debe ser null");
        assertTrue(resp.getBody().contains("\"status\":\"UP\""),
                "El estado de salud debe ser UP; recibido: " + resp.getBody());
    }

    @Test
    void health_noExponeDetallesSensibles() {
        // Con management.endpoint.health.show-details: never (prod) el cuerpo no
        // debe filtrar detalles de componentes (db, diskSpace, etc.). En el perfil
        // por defecto Spring Boot también oculta detalles salvo configuración
        // explícita; este test documenta y protege ese comportamiento.
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertFalse(resp.getBody().contains("\"components\""),
                "El health no debe exponer el detalle de 'components' sin autorización");
    }
}
