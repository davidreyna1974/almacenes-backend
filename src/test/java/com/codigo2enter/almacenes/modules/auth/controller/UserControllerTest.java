package com.codigo2enter.almacenes.modules.auth.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;
import com.codigo2enter.almacenes.modules.auth.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de la capa web para UserController.
 *
 * @WebMvcTest(UserController.class)
 *   Levanta un contexto de Spring reducido que solo contiene los componentes
 *   web necesarios: el controlador indicado, los filtros, los conversores de
 *   mensajes (Jackson) y el motor de validación (Jakarta). NO levanta la capa
 *   de servicio, repositorios ni conecta a la base de datos. Esto hace que
 *   la prueba sea mucho más rápida que un @SpringBootTest completo.
 *
 * @AutoConfigureMockMvc(addFilters = false)
 *   Desactiva todos los filtros de la cadena de Spring Security, incluyendo
 *   JwtAuthenticationFilter. Sin esto, cada petición de prueba sería rechazada
 *   con HTTP 401/403 por no llevar token JWT, lo que haría imposible testear
 *   los endpoints en esta fase del desarrollo.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    /**
     * MockMvc simula un servidor HTTP sin levantar un servidor real (Tomcat).
     * Permite construir peticiones HTTP programáticamente y verificar la respuesta
     * (status code, headers, body JSON) de forma fluida y legible.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * @MockBean reemplaza el bean real de UserService en el contexto de prueba
     * con un mock de Mockito. Esto significa que ningún código real del servicio
     * se ejecuta — solo el comportamiento que configuremos con when(...).thenReturn(...).
     *
     * Objetivo: aislar el controlador de su dependencia para probar únicamente
     * la lógica HTTP (rutas, validaciones, códigos de respuesta, serialización JSON).
     */
    @MockBean
    private UserService userService;

    /**
     * @WebMvcTest carga SecurityConfig, que a su vez necesita construir
     * JwtAuthenticationFilter, que depende de JwtUtils. Como JwtUtils no es
     * un bean de la capa web, no está disponible en el contexto reducido.
     * Este @MockBean satisface esa dependencia con un stub vacío, permitiendo
     * que el contexto arranque sin ejecutar ninguna lógica real de JWT.
     */
    @MockBean
    private JwtUtils jwtUtils;

    /**
     * Verifica que el endpoint POST /api/v1/auth/register:
     *   1. Acepte un JSON válido en el body.
     *   2. Delegue al servicio (simulado) correctamente.
     *   3. Retorne HTTP 201 Created.
     *   4. Incluya en el JSON de respuesta el username esperado.
     *
     * Patrón AAA (Arrange - Act - Assert):
     *   - Arrange : configuramos el mock del servicio y preparamos el body JSON.
     *   - Act     : ejecutamos la petición HTTP con mockMvc.perform().
     *   - Assert  : verificamos el status y el contenido de la respuesta.
     */
    @Test
    @DisplayName("POST /register debe retornar 201 Created con el usuario registrado")
    void shouldRegisterUserSuccessfully() throws Exception {

        // --- ARRANGE ---

        // Construimos el DTO de respuesta simulado que el mock del servicio devolverá.
        // Representa al usuario que hipotéticamente quedó guardado en la base de datos.
        UserResponseDTO mockResponse = UserResponseDTO.builder()
                .id(1L)
                .username("carlos_operador")
                .email("carlos@almacen.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .roles(Set.of("ROLE_WAREHOUSEMAN"))
                .build();

        // Configuramos el mock: "cuando registerUser reciba CUALQUIER UserRequestDTO,
        // devuelve el mockResponse sin ejecutar lógica real".
        // any(UserRequestDTO.class) evita que el test sea frágil ante cambios menores
        // en los datos del request — solo nos importa el comportamiento del controlador.
        when(userService.registerUser(any(UserRequestDTO.class))).thenReturn(mockResponse);

        // Body JSON que simula la petición enviada desde Angular.
        // Cumple todas las validaciones de UserRequestDTO (@NotBlank, @Email, @Size).
        String requestBody = """
                {
                    "username": "carlos_operador",
                    "email": "carlos@almacen.com",
                    "password": "segura123"
                }
                """;

        // --- ACT + ASSERT ---
        // mockMvc.perform() ejecuta la petición y .andExpect() encadena las validaciones.
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                // Indica a Spring que el body es JSON, necesario para
                                // que Jackson deserialice el body a UserRequestDTO.
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                // Verifica que el controlador retornó HTTP 201 Created, no 200 ni 400.
                .andExpect(status().isCreated())

                // jsonPath evalúa expresiones sobre el JSON de respuesta.
                // "$.username" apunta al campo 'username' en la raíz del objeto JSON.
                // El valor debe coincidir exactamente con el del mockResponse.
                .andExpect(jsonPath("$.username").value("carlos_operador"))

                // Verificaciones adicionales para mayor cobertura de la respuesta.
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.active").value(true));
    }
}
