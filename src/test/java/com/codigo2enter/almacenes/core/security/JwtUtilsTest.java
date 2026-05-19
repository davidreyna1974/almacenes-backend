package com.codigo2enter.almacenes.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias para JwtUtils.
 *
 * Son pruebas UNITARIAS puras: no levantan el contexto de Spring (@SpringBootTest),
 * no conectan a la base de datos y no usan mocks — JwtUtils es una clase sin
 * dependencias externas que solo necesita la librería JJWT, por lo que se puede
 * instanciar directamente con 'new'.
 *
 * Esto hace que las pruebas sean muy rápidas (< 100 ms cada una) y completamente
 * aisladas del resto del sistema.
 */
class JwtUtilsTest {

    // Instancia real de la clase bajo prueba (no un mock).
    private JwtUtils jwtUtils;

    // Datos de prueba reutilizados en los tres tests.
    private static final String TEST_USERNAME = "bodeguero01";
    private static final Set<String> TEST_ROLES = Set.of("ROLE_WAREHOUSEMAN");

    /**
     * @BeforeEach se ejecuta antes de cada método @Test.
     * Crea una instancia fresca de JwtUtils para garantizar que los tests
     * son independientes entre sí — ninguno hereda estado del anterior.
     */
    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
    }

    /**
     * Verifica que generateToken produzca un token no nulo y que
     * ese mismo token sea reconocido como válido por validateToken.
     *
     * Este test cubre el flujo principal (happy path) del ciclo de vida
     * de un JWT: generación → validación.
     */
    @Test
    @DisplayName("Debe generar un token no nulo y válido para un usuario con roles")
    void shouldGenerateValidToken() {
        // ARRANGE — preparar los datos de entrada (ya definidos como constantes)

        // ACT — ejecutar la acción bajo prueba
        String token = jwtUtils.generateToken(TEST_USERNAME, TEST_ROLES);

        // ASSERT — verificar el resultado esperado
        // El token no debe ser nulo ni estar vacío
        assertNotNull(token, "El token generado no debe ser nulo");

        // validateToken debe confirmar que la firma es auténtica y el token no expiró.
        // Si la clave secreta o el algoritmo cambian, esta aserción fallará,
        // lo que nos alertaría de un cambio que rompe la compatibilidad.
        assertTrue(jwtUtils.validateToken(token),
                "El token recién generado debe pasar la validación de firma y expiración");
    }

    /**
     * Verifica que extractUsername recupere exactamente el mismo username
     * que fue usado al generar el token.
     *
     * Este test garantiza que el claim 'sub' (subject) se escribe y se lee
     * correctamente, sin transformaciones ni pérdida de datos.
     */
    @Test
    @DisplayName("Debe extraer el username correcto del payload del token")
    void shouldExtractCorrectUsername() {
        // ARRANGE — generamos el token con el username de prueba
        String token = jwtUtils.generateToken(TEST_USERNAME, TEST_ROLES);

        // ACT — extraemos el username del token generado
        String extractedUsername = jwtUtils.extractUsername(token);

        // ASSERT — el username extraído debe ser idéntico al original.
        // assertEquals compara valor a valor (no referencia), lo que es
        // el comportamiento correcto para Strings.
        assertEquals(TEST_USERNAME, extractedUsername,
                "El username extraído del token debe coincidir exactamente con el original");
    }

    /**
     * Verifica que un token manipulado (con un carácter alterado) sea rechazado.
     *
     * Esta prueba simula un ataque en el que un actor malicioso intenta modificar
     * el payload del JWT para cambiar su identidad o sus roles. JJWT detecta
     * la manipulación porque la firma HMAC-SHA256 ya no coincide con el contenido.
     *
     * La alteración se aplica en el último carácter del token para asegurarse
     * de que el cambio afecte la firma (tercera sección del JWT), aunque cualquier
     * modificación en cualquier parte del token causaría el mismo rechazo.
     */
    @Test
    @DisplayName("Debe retornar false para un token manipulado o con firma inválida")
    void shouldReturnFalseForInvalidOrManipulatedToken() {
        // ARRANGE — generamos un token legítimo y lo manipulamos
        String validToken = jwtUtils.generateToken(TEST_USERNAME, TEST_ROLES);

        // Alteramos el último carácter del token para corromper la firma.
        // El JWT tiene el formato "header.payload.signature"; cambiar cualquier
        // carácter invalida la verificación HMAC sin importar en qué sección caiga.
        char lastChar = validToken.charAt(validToken.length() - 1);
        char alteredChar = (lastChar == 'A') ? 'B' : 'A';
        String manipulatedToken = validToken.substring(0, validToken.length() - 1) + alteredChar;

        // ACT — validamos el token manipulado
        Boolean result = jwtUtils.validateToken(manipulatedToken);

        // ASSERT — debe retornar false porque la firma ya no coincide con el contenido.
        // Si esta prueba fallara (result == true), significaría que el sistema
        // acepta tokens falsificados — una vulnerabilidad crítica de seguridad.
        assertFalse(result,
                "Un token con la firma manipulada debe ser rechazado por validateToken");
    }
}
