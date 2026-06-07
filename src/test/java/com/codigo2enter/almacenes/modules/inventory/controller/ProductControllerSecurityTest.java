package com.codigo2enter.almacenes.modules.inventory.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.core.security.SecurityConfig;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de seguridad RBAC para ProductController con filtros de Spring Security activos.
 *
 * Usa JWT simulado (mismo patrón que SecurityFilterTest) para que JwtAuthenticationFilter
 * establezca el SecurityContext durante la cadena de filtros, compatible con
 * SessionCreationPolicy.STATELESS en Spring Security 6.
 *
 * @Import(SecurityConfig.class) asegura que el SecurityFilterChain personalizado con las
 * reglas RBAC se cargue en el contexto de prueba (mismo motivo que en SecurityFilterTest).
 *
 * Escenarios cubiertos:
 *
 *   Rule A: POST /products        → ADMIN, MANAGER — WAREHOUSEMAN/SALES reciben 403
 *   Rule B: DELETE /products/{id} → ADMIN only     — MANAGER recibe 403 (regla específica
 *                                                     prevalece sobre DELETE /inventory/**)
 *   Rule C: POST /products/movement → ADMIN, MANAGER, WAREHOUSEMAN — SALES recibe 403
 *   Rule D: GET /products           → todos los roles autenticados reciben 200
 */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtUtils jwtUtils;

    private static final String PRODUCT_BODY = """
            {
                "sku": "TOOL-001",
                "name": "Taladro percutor",
                "description": "Taladro de alto rendimiento",
                "price": 99.99,
                "currentStock": 10,
                "minimumStock": 5,
                "status": "AVAILABLE",
                "categoryId": 1,
                "supplierId": 1,
                "unitCost": 50.00
            }
            """;

    private static final String MOVEMENT_BODY = """
            {
                "productId": 1,
                "quantity": 10,
                "type": "IN",
                "reason": "Compra orden #45"
            }
            """;

    private String tokenConRol(String roleWithPrefix) {
        String tok = "token." + roleWithPrefix;
        when(jwtUtils.extractUsername(tok)).thenReturn("usuario_test");
        when(jwtUtils.validateToken(tok)).thenReturn(true);
        when(jwtUtils.extractRoles(tok)).thenReturn(List.of(roleWithPrefix));
        return tok;
    }

    // =========================================================================
    // POST /products — solo ADMIN y MANAGER tienen acceso
    // =========================================================================

    @Test
    @DisplayName("POST /products: WAREHOUSEMAN debe recibir 403 Forbidden")
    void warehousemanCannotCreateProduct() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");

        mockMvc.perform(post("/api/v1/inventory/products")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /products: SALES debe recibir 403 Forbidden")
    void salesCannotCreateProduct() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        mockMvc.perform(post("/api/v1/inventory/products")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE /products/{id} — solo ADMIN tiene acceso (regla específica antes de la general)
    // =========================================================================

    @Test
    @DisplayName("DELETE /products/{id}: MANAGER debe recibir 403 — la regla ADMIN-only prevalece sobre DELETE /inventory/**")
    void managerCannotDeleteProduct() throws Exception {
        String tok = tokenConRol("ROLE_MANAGER");

        mockMvc.perform(delete("/api/v1/inventory/products/1")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /products/{id}: ADMIN debe recibir 204 No Content")
    void adminCanDeleteProduct() throws Exception {
        String tok = tokenConRol("ROLE_ADMIN");
        // deactivateProduct es void — Mockito no hace nada por defecto

        mockMvc.perform(delete("/api/v1/inventory/products/1")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // POST /products/movement — ADMIN, MANAGER y WAREHOUSEMAN tienen acceso
    // =========================================================================

    @Test
    @DisplayName("POST /products/movement: SALES debe recibir 403 Forbidden")
    void salesCannotRegisterMovement() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        mockMvc.perform(post("/api/v1/inventory/products/movement")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVEMENT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /products/movement: WAREHOUSEMAN debe recibir 204 No Content")
    void warehousemanCanRegisterMovement() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        // registerStockMovement es void — sin configuración adicional en Mockito

        mockMvc.perform(post("/api/v1/inventory/products/movement")
                        .header("Authorization", "Bearer " + tok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVEMENT_BODY))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // GET /products — todos los roles autenticados tienen acceso
    // =========================================================================

    @Test
    @DisplayName("GET /products: WAREHOUSEMAN debe recibir 200 OK")
    void warehousemanCanReadProducts() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");

        PageResponseDTO<ProductResponseDTO> emptyPage = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of())
                .currentPage(0).totalPages(0).totalElements(0).size(20)
                .first(true).last(true).build();
        when(productService.searchProducts(isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/inventory/products")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /products: SALES debe recibir 200 OK")
    void salesCanReadProducts() throws Exception {
        String tok = tokenConRol("ROLE_SALES");

        PageResponseDTO<ProductResponseDTO> emptyPage = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of())
                .currentPage(0).totalPages(0).totalElements(0).size(20)
                .first(true).last(true).build();
        when(productService.searchProducts(isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/inventory/products")
                        .header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk());
    }
}
