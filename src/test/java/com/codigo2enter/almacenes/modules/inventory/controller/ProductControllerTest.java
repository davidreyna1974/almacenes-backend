package com.codigo2enter.almacenes.modules.inventory.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
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
 * Pruebas de integración de la capa web para ProductController.
 *
 * Cubre los 8 endpoints del controlador verificando: enrutamiento correcto,
 * activación de validaciones Jakarta (@Valid), delegación al servicio y
 * serialización del body de respuesta.
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtUtils jwtUtils;

    private ProductResponseDTO      response;
    private StockMovementResponseDTO movResponse;

    @BeforeEach
    void setUp() {
        response = ProductResponseDTO.builder()
                .id(1L)
                .sku("TOOL-001")
                .name("Taladro percutor")
                .categoryId(1L)
                .categoryName("Herramientas")
                .active(true)
                .build();

        movResponse = StockMovementResponseDTO.builder()
                .id(1L)
                .quantity(10)
                .type("IN")
                .reason("Compra orden #45")
                .build();
    }

    // =========================================================================
    // GET /api/v1/inventory/products  (búsqueda con filtros opcionales)
    // =========================================================================

    /**
     * Sin parámetros → retorna todos los productos activos paginados.
     * Verifica que el endpoint GET base está mapeado y delega al servicio.
     */
    @Test
    @DisplayName("GET /products: sin parámetros debe retornar 200 con todos los productos activos")
    void shouldReturnAllActiveProductsWhenNoParams() throws Exception {
        // ARRANGE
        PageResponseDTO<ProductResponseDTO> page = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of(response))
                .currentPage(0).totalPages(1).totalElements(1).size(20)
                .first(true).last(true).build();
        when(productService.searchProducts(null, null, null, null, 0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("TOOL-001"));
    }

    /**
     * Con search=taladro → delega la búsqueda al servicio con ese término.
     * Verifica que el parámetro ?search se mapea correctamente.
     */
    @Test
    @DisplayName("GET /products?search=taladro: debe retornar 200 con los productos que coincidan")
    void shouldReturnProductsMatchingSearch() throws Exception {
        // ARRANGE
        PageResponseDTO<ProductResponseDTO> page = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of(response))
                .currentPage(0).totalPages(1).totalElements(1).size(20)
                .first(true).last(true).build();
        when(productService.searchProducts("taladro", null, null, null, 0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products").param("search", "taladro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Taladro percutor"));
    }

    /**
     * Con categoryId=1, status=AVAILABLE, supplierId=1 → todos los filtros activos.
     * Verifica que los tres parámetros de filtro se pasan correctamente al servicio.
     */
    @Test
    @DisplayName("GET /products: con filtros categoryId + status + supplierId debe retornar 200")
    void shouldReturnProductsWithAllFilters() throws Exception {
        // ARRANGE
        PageResponseDTO<ProductResponseDTO> page = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of(response))
                .currentPage(0).totalPages(1).totalElements(1).size(20)
                .first(true).last(true).build();
        when(productService.searchProducts(null, 1L, "AVAILABLE", 1L, 0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products")
                        .param("categoryId", "1")
                        .param("status", "AVAILABLE")
                        .param("supplierId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // =========================================================================
    // POST /api/v1/inventory/products
    // =========================================================================

    /**
     * Happy path: body JSON válido → 201 Created con sku y categoryName resueltos.
     */
    @Test
    @DisplayName("POST /products: debe retornar 201 Created con el producto creado")
    void shouldCreateProductAndReturn201() throws Exception {
        // ARRANGE
        when(productService.createProduct(any(ProductRequestDTO.class)))
                .thenReturn(response);

        String body = """
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

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/inventory/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("TOOL-001"))
                .andExpect(jsonPath("$.categoryName").value("Herramientas"));
    }

    /**
     * Validación: sku="" falla @NotBlank → 400 Bad Request.
     * Verifica que @Valid está activo en el endpoint POST.
     */
    @Test
    @DisplayName("POST /products: debe retornar 400 cuando el SKU está en blanco")
    void shouldReturn400WhenSkuIsBlank() throws Exception {
        // ARRANGE — sku vacío falla @NotBlank
        String invalidBody = """
                {
                    "sku": "",
                    "name": "Taladro percutor",
                    "price": 99.99,
                    "currentStock": 10,
                    "minimumStock": 5,
                    "status": "AVAILABLE",
                    "categoryId": 1,
                    "supplierId": 1
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/inventory/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PUT /api/v1/inventory/products/{id}
    // =========================================================================

    /**
     * Happy path: body válido con id=1 → 200 OK con el DTO actualizado.
     */
    @Test
    @DisplayName("PUT /products/{id}: debe retornar 200 con el producto actualizado")
    void shouldUpdateProductAndReturn200() throws Exception {
        // ARRANGE
        when(productService.updateProduct(eq(1L), any(ProductRequestDTO.class)))
                .thenReturn(response);

        String body = """
                {
                    "sku": "TOOL-001",
                    "name": "Taladro percutor",
                    "description": "Taladro actualizado",
                    "price": 109.99,
                    "currentStock": 10,
                    "minimumStock": 5,
                    "status": "AVAILABLE",
                    "categoryId": 1,
                    "supplierId": 1,
                    "unitCost": 50.00
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(put("/api/v1/inventory/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("TOOL-001"));
    }

    // =========================================================================
    // DELETE /api/v1/inventory/products/{id}
    // =========================================================================

    /**
     * Happy path: el servicio ejecuta sin error → 204 No Content.
     * deactivateProduct es void — Mockito no hace nada por defecto, que es
     * el comportamiento correcto para una operación de soft delete exitosa.
     */
    @Test
    @DisplayName("DELETE /products/{id}: debe retornar 204 No Content")
    void shouldDeactivateProductAndReturn204() throws Exception {
        // ACT + ASSERT
        mockMvc.perform(delete("/api/v1/inventory/products/1"))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // GET /api/v1/inventory/products/sku/{sku}
    // =========================================================================

    /**
     * Happy path: el servicio encuentra el producto con el SKU indicado.
     * Verifica que la ruta /sku/{sku} está correctamente mapeada.
     */
    @Test
    @DisplayName("GET /products/sku/{sku}: debe retornar 200 con el producto encontrado")
    void shouldReturnProductBySku() throws Exception {
        // ARRANGE
        when(productService.getBySku("TOOL-001")).thenReturn(response);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products/sku/TOOL-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TOOL-001"))
                .andExpect(jsonPath("$.categoryName").value("Herramientas"));
    }

    // =========================================================================
    // GET /api/v1/inventory/products/category/{categoryId}
    // =========================================================================

    /**
     * Happy path: el servicio retorna 2 productos de la categoría indicada.
     */
    @Test
    @DisplayName("GET /products/category/{categoryId}: debe retornar 200 con los productos de la categoría")
    void shouldReturnProductsByCategoryId() throws Exception {
        // ARRANGE
        ProductResponseDTO response2 = ProductResponseDTO.builder()
                .id(2L).sku("TOOL-002").categoryName("Herramientas").build();

        // El endpoint ahora retorna PageResponseDTO — mockear la versión paginada.
        PageResponseDTO<ProductResponseDTO> page = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of(response, response2))
                .currentPage(0).totalPages(1).totalElements(2).size(20)
                .first(true).last(true).build();
        when(productService.getByCategoryId(1L, 0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products/category/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].sku").value("TOOL-001"))
                .andExpect(jsonPath("$.content[1].sku").value("TOOL-002"));
    }

    // =========================================================================
    // GET /api/v1/inventory/products/low-stock
    // =========================================================================

    /**
     * Happy path: el servicio retorna 2 productos con stock crítico.
     */
    @Test
    @DisplayName("GET /products/low-stock: debe retornar 200 con productos de stock bajo")
    void shouldReturnLowStockProducts() throws Exception {
        // ARRANGE
        ProductResponseDTO response2 = ProductResponseDTO.builder()
                .id(2L).sku("TOOL-002").build();

        // El endpoint ahora retorna PageResponseDTO — mockear la versión paginada.
        PageResponseDTO<ProductResponseDTO> page = PageResponseDTO.<ProductResponseDTO>builder()
                .content(List.of(response, response2))
                .currentPage(0).totalPages(1).totalElements(2).size(20)
                .first(true).last(true).build();
        when(productService.getLowStockProducts(0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    // =========================================================================
    // POST /api/v1/inventory/products/movement
    // =========================================================================

    /**
     * Happy path: body válido → 204 No Content.
     * registerStockMovement retorna void — la respuesta no tiene cuerpo.
     */
    @Test
    @DisplayName("POST /products/movement: debe retornar 204 No Content al registrar el movimiento")
    void shouldRegisterStockMovementAndReturn204() throws Exception {
        // ARRANGE — registerStockMovement es void; Mockito no requiere configuración
        String body = """
                {
                    "productId": 1,
                    "quantity": 10,
                    "type": "IN",
                    "reason": "Compra orden #45"
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/inventory/products/movement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    /**
     * Validación: quantity=0 falla @Min(1) → 400 Bad Request.
     * Verifica que @Valid está activo en el endpoint de movimientos.
     * Sin @Valid, el servicio recibiría quantity=0 y lanzaría RuntimeException
     * en lugar de devolver HTTP 400 al cliente con un mensaje de validación.
     */
    @Test
    @DisplayName("POST /products/movement: debe retornar 400 cuando la cantidad es cero")
    void shouldReturn400WhenMovementQuantityIsZero() throws Exception {
        // ARRANGE — quantity=0 falla @Min(value=1)
        String invalidBody = """
                {
                    "productId": 1,
                    "quantity": 0,
                    "type": "IN",
                    "reason": "Compra"
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/inventory/products/movement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/inventory/products/{id}/movements
    // =========================================================================

    /**
     * Happy path: el servicio retorna historial de 3 movimientos del producto.
     * Verifica la ruta /{id}/movements y que la lista se serializa correctamente.
     */
    @Test
    @DisplayName("GET /products/{id}/movements: debe retornar 200 con el historial de movimientos")
    void shouldReturnStockMovementsByProduct() throws Exception {
        // ARRANGE
        StockMovementResponseDTO mov2 = StockMovementResponseDTO.builder()
                .id(2L).quantity(5).type("OUT").build();
        StockMovementResponseDTO mov3 = StockMovementResponseDTO.builder()
                .id(3L).quantity(20).type("IN").build();

        // El endpoint ahora retorna PageResponseDTO — mockear la versión paginada.
        PageResponseDTO<StockMovementResponseDTO> page = PageResponseDTO.<StockMovementResponseDTO>builder()
                .content(List.of(movResponse, mov2, mov3))
                .currentPage(0).totalPages(1).totalElements(3).size(20)
                .first(true).last(true).build();
        when(productService.getStockMovementsByProduct(1L, 0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/inventory/products/1/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].type").value("IN"))
                .andExpect(jsonPath("$.content[1].type").value("OUT"));
    }
}
