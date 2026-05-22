package com.codigo2enter.almacenes.modules.inventory.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de la capa web para CategoryController.
 *
 * @WebMvcTest levanta un contexto reducido con solo los componentes web:
 * controlador, Jackson, validaciones Jakarta y filtros de seguridad.
 * No levanta repositorios, servicios reales ni conecta a PostgreSQL.
 *
 * @AutoConfigureMockMvc(addFilters = false) desactiva JwtAuthenticationFilter
 * para que las peticiones de prueba no sean rechazadas con HTTP 401.
 *
 * @MockBean JwtUtils satisface la dependencia que SecurityConfig necesita
 * para construir JwtAuthenticationFilter en el contexto reducido.
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    /**
     * @MockBean JwtUtils es necesario aunque los filtros estén desactivados.
     * @WebMvcTest carga SecurityConfig que intenta construir JwtAuthenticationFilter,
     * el cual depende de JwtUtils. Sin este mock, el contexto de prueba no arranca.
     */
    @MockBean
    private JwtUtils jwtUtils;

    private CategoryDTO validDTO;
    private CategoryDTO response;

    @BeforeEach
    void setUp() {
        validDTO = CategoryDTO.builder()
                .name("Herramientas")
                .description("Herramientas de trabajo")
                .active(true)
                .build();

        response = CategoryDTO.builder()
                .id(1L)
                .name("Herramientas")
                .description("Herramientas de trabajo")
                .active(true)
                .build();
    }

    // =========================================================================
    // POST /api/v1/inventory/categories
    // =========================================================================

    /**
     * Happy path: body JSON válido → 201 Created con el DTO retornado por el servicio.
     * Verifica que el controlador serializa correctamente el objeto recibido del servicio.
     */
    @Test
    @DisplayName("POST /categories: debe retornar 201 Created con la categoría creada")
    void shouldCreateCategoryAndReturn201() throws Exception {
        // ARRANGE
        when(categoryService.createCategory(any(CategoryDTO.class))).thenReturn(response);

        String body = """
                {
                    "name": "Herramientas",
                    "description": "Herramientas de trabajo",
                    "active": true
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/inventory/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Herramientas"));
    }

    /**
     * Validación: name="" falla @NotBlank → 400 Bad Request.
     * Verifica que @Valid está correctamente anotado en el parámetro del
     * método del controlador. Sin @Valid, Spring no ejecutaría la validación
     * y el servicio recibiría datos inválidos.
     */
    @Test
    @DisplayName("POST /categories: debe retornar 400 cuando el nombre está en blanco")
    void shouldReturn400WhenNameIsBlank() throws Exception {
        // ARRANGE — body con name vacío que falla @NotBlank
        String invalidBody = """
                {
                    "name": "",
                    "description": "Herramientas de trabajo",
                    "active": true
                }
                """;

        // ACT + ASSERT — Spring rechaza antes de invocar al servicio
        mockMvc.perform(post("/api/v1/inventory/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/inventory/categories/active
    // =========================================================================

    /**
     * Happy path: el servicio retorna 2 categorías activas.
     * Verifica la ruta /active y que la lista se serializa correctamente.
     */
    @Test
    @DisplayName("GET /categories/active: debe retornar 200 con la lista de categorías activas")
    void shouldReturnActiveCategories() throws Exception {
        // ARRANGE
        CategoryDTO response2 = CategoryDTO.builder()
                .id(2L).name("Electrónica").active(true).build();

        when(categoryService.getAllActiveCategories())
                .thenReturn(List.of(response, response2));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/categories/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Herramientas"))
                .andExpect(jsonPath("$[1].name").value("Electrónica"));
    }

    // =========================================================================
    // PUT /api/v1/inventory/categories/{id}
    // =========================================================================

    /**
     * Happy path: body válido con id=1 → 200 OK con el DTO actualizado.
     */
    @Test
    @DisplayName("PUT /categories/{id}: debe retornar 200 con la categoría actualizada")
    void shouldUpdateCategoryAndReturn200() throws Exception {
        // ARRANGE
        when(categoryService.updateCategory(eq(1L), any(CategoryDTO.class)))
                .thenReturn(response);

        String body = """
                {
                    "name": "Herramientas",
                    "description": "Herramientas de trabajo",
                    "active": true
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(put("/api/v1/inventory/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Herramientas"));
    }

    /**
     * Validación: name="   " (solo espacios) falla @NotBlank → 400 Bad Request.
     * Confirma que la validación aplica también en el endpoint de actualización.
     */
    @Test
    @DisplayName("PUT /categories/{id}: debe retornar 400 cuando el nombre es solo espacios")
    void shouldReturn400WhenUpdateBodyIsInvalid() throws Exception {
        // ARRANGE — name con solo espacios, falla @NotBlank
        String invalidBody = """
                {
                    "name": "   ",
                    "description": "Herramientas de trabajo",
                    "active": true
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(put("/api/v1/inventory/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // DELETE /api/v1/inventory/categories/{id}
    // =========================================================================

    /**
     * Happy path: el servicio ejecuta sin error → 204 No Content sin cuerpo.
     * deactivateCategory es void — no se configura when/thenReturn;
     * Mockito no hace nada por defecto para métodos void, que es el comportamiento correcto.
     */
    @Test
    @DisplayName("DELETE /categories/{id}: debe retornar 204 No Content")
    void shouldDeactivateCategoryAndReturn204() throws Exception {
        // ACT + ASSERT
        mockMvc.perform(delete("/api/v1/inventory/categories/1"))
                .andExpect(status().isNoContent());
    }
}
