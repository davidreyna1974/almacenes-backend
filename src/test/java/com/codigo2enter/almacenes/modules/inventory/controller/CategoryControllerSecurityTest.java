package com.codigo2enter.almacenes.modules.inventory.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.core.security.SecurityConfig;
import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de seguridad RBAC para CategoryController con filtros de Spring Security activos.
 *
 * A diferencia de CategoryControllerTest (addFilters=false), aquí JwtAuthenticationFilter
 * se ejecuta en cada petición. La estrategia: JwtUtils está mockeado y se le instruye
 * para que reconozca tokens de prueba específicos con roles concretos. El filtro
 * extrae username, valida el token y aplica los roles — Spring Security evalúa entonces
 * las reglas definidas en SecurityConfig.
 *
 * @Import(SecurityConfig.class) es necesario para que el SecurityFilterChain personalizado
 * (con las reglas RBAC) se registre en el contexto de prueba. Sin él, @WebMvcTest
 * aplica la seguridad auto-configurada de Spring Boot (que usa HTTP Basic) en lugar
 * de la configuración JWT/RBAC de la aplicación.
 *
 * Escenarios cubiertos:
 *   - WAREHOUSEMAN y SALES no pueden crear, actualizar ni desactivar categorías → 403
 *   - WAREHOUSEMAN y SALES pueden leer categorías activas → 200
 */
@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtUtils jwtUtils;

    private static final String VALID_BODY = """
            {
                "name": "Herramientas",
                "description": "Herramientas de trabajo",
                "active": true
            }
            """;

    /**
     * Crea un token de prueba para el rol indicado y configura JwtUtils para
     * que lo reconozca como válido con las autoridades correspondientes.
     *
     * La convención ROLE_ es necesaria: hasRole("WAREHOUSEMAN") comprueba ROLE_WAREHOUSEMAN.
     */
    private String tokenConRol(String roleWithPrefix) {
        String tok = "token." + roleWithPrefix;
        when(jwtUtils.extractUsername(tok)).thenReturn("usuario_test");
        when(jwtUtils.validateToken(tok)).thenReturn(true);
        when(jwtUtils.extractRoles(tok)).thenReturn(List.of(roleWithPrefix));
        return tok;
    }

    // =========================================================================
    // POST /categories — WAREHOUSEMAN y SALES deben recibir 403
    // =========================================================================

    @Test
    @DisplayName("POST /categories: WAREHOUSEMAN debe recibir 403 Forbidden")
    void warehousemanCannotCreateCategory() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");

        mockMvc.perform(post("/api/v1/inventory/categories")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /categories: SALES debe recibir 403 Forbidden")
    void salesCannotCreateCategory() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        mockMvc.perform(post("/api/v1/inventory/categories")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PUT /categories/{id} — WAREHOUSEMAN y SALES deben recibir 403
    // =========================================================================

    @Test
    @DisplayName("PUT /categories/{id}: WAREHOUSEMAN debe recibir 403 Forbidden")
    void warehousemanCannotUpdateCategory() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");

        mockMvc.perform(put("/api/v1/inventory/categories/1")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /categories/{id}: SALES debe recibir 403 Forbidden")
    void salesCannotUpdateCategory() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        mockMvc.perform(put("/api/v1/inventory/categories/1")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE /categories/{id} — WAREHOUSEMAN y SALES deben recibir 403
    // =========================================================================

    @Test
    @DisplayName("DELETE /categories/{id}: WAREHOUSEMAN debe recibir 403 Forbidden")
    void warehousemanCannotDeactivateCategory() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");

        mockMvc.perform(delete("/api/v1/inventory/categories/1")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /categories/{id}: SALES debe recibir 403 Forbidden")
    void salesCannotDeactivateCategory() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        mockMvc.perform(delete("/api/v1/inventory/categories/1")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /categories/active — todos los roles pueden leer
    // =========================================================================

    @Test
    @DisplayName("GET /categories/active: WAREHOUSEMAN debe recibir 200 OK")
    void warehousemanCanReadCategories() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");

        PageResponseDTO<CategoryDTO> emptyPage = PageResponseDTO.<CategoryDTO>builder()
                .content(List.of())
                .currentPage(0).totalPages(0).totalElements(0).size(20)
                .first(true).last(true).build();
        when(categoryService.getAllActiveCategories(0, 20)).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/inventory/categories/active")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /categories/active: SALES debe recibir 200 OK")
    void salesCanReadCategories() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        PageResponseDTO<CategoryDTO> emptyPage = PageResponseDTO.<CategoryDTO>builder()
                .content(List.of())
                .currentPage(0).totalPages(0).totalElements(0).size(20)
                .first(true).last(true).build();
        when(categoryService.getAllActiveCategories(0, 20)).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/inventory/categories/active")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk());
    }
}
