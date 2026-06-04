package com.codigo2enter.almacenes.core.security;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.auth.controller.UserController;
import com.codigo2enter.almacenes.modules.auth.dto.AuthResponseDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;
import com.codigo2enter.almacenes.modules.auth.service.UserService;
import com.codigo2enter.almacenes.modules.inventory.controller.CategoryController;
import com.codigo2enter.almacenes.modules.inventory.controller.ProductController;
import com.codigo2enter.almacenes.modules.inventory.service.CategoryService;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
import com.codigo2enter.almacenes.modules.purchases.controller.PurchaseOrderController;
import com.codigo2enter.almacenes.modules.purchases.controller.SupplierController;
import com.codigo2enter.almacenes.modules.purchases.service.PurchaseOrderService;
import com.codigo2enter.almacenes.modules.purchases.service.SupplierService;
import com.codigo2enter.almacenes.modules.sales.controller.ClientController;
import com.codigo2enter.almacenes.modules.sales.controller.ReservationController;
import com.codigo2enter.almacenes.modules.sales.controller.SaleOrderController;
import com.codigo2enter.almacenes.modules.sales.service.ClientService;
import com.codigo2enter.almacenes.modules.sales.service.ReservationService;
import com.codigo2enter.almacenes.modules.sales.service.SaleOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que Spring Security aplica correctamente las reglas de autorización.
 *
 * A diferencia de los tests de controlador existentes (addFilters=false),
 * aquí los filtros están ACTIVOS — el JwtAuthenticationFilter y las reglas
 * de SecurityConfig se evalúan en cada request.
 *
 * Sin esta clase, un cambio en SecurityConfig (p.ej. eliminar permitAll en
 * /auth/**) haría que login retorne 403 en producción sin que ningún test
 * lo detecte.
 *
 * Estrategia: JwtUtils está mockeado. Para requests autenticados configuramos
 * extractUsername() y validateToken() para que reconozcan "valid.test.token".
 * Para requests sin token, el filtro pasa sin autenticar y Spring Security
 * decide según las reglas: 403 para rutas protegidas, permitAll para /auth/**.
 */
@WebMvcTest({
    UserController.class,
    CategoryController.class,
    ProductController.class,
    SupplierController.class,
    PurchaseOrderController.class,
    ClientController.class,
    SaleOrderController.class,
    ReservationController.class
})
@Import(SecurityConfig.class)
class SecurityFilterTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService userService;
    @MockBean CategoryService categoryService;
    @MockBean ProductService productService;
    @MockBean SupplierService supplierService;
    @MockBean PurchaseOrderService purchaseOrderService;
    @MockBean ClientService clientService;
    @MockBean SaleOrderService saleOrderService;
    @MockBean ReservationService reservationService;
    @MockBean JwtUtils jwtUtils;

    private static final String TOKEN  = "valid.test.token";
    private static final String BEARER = "Bearer " + TOKEN;

    private void autenticar() {
        autenticarConRol("ROLE_ADMIN");
    }

    private void autenticarConRol(String... roles) {
        when(jwtUtils.extractUsername(TOKEN)).thenReturn("tester01");
        when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtils.extractRoles(TOKEN)).thenReturn(List.of(roles));
    }

    private String tokenConRol(String... roles) {
        String tok = "token." + String.join(".", roles);
        when(jwtUtils.extractUsername(tok)).thenReturn("usuario_test");
        when(jwtUtils.validateToken(tok)).thenReturn(true);
        when(jwtUtils.extractRoles(tok)).thenReturn(List.of(roles));
        return tok;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 1 — Rutas públicas (/auth/**): accesibles sin JWT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void login_createUser_admin_sinToken_retorna403() throws Exception {
        // POST /auth/users requiere ROLE_ADMIN — sin token debe retornar 403
        mockMvc.perform(post("/api/v1/auth/users")
                .contentType("application/json")
                .content("{\"username\":\"nuevo\",\"password\":\"P1234567!\",\"email\":\"n@t.com\",\"roles\":[\"ROLE_WAREHOUSEMAN\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_sinToken_esAccesible_noRetorna403() throws Exception {
        when(userService.login(any()))
                .thenReturn(AuthResponseDTO.builder().token("tok").build());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"u\",\"password\":\"P1!\"}"))
                .andExpect(result -> assertNotEquals(
                    HttpStatus.FORBIDDEN.value(),
                    result.getResponse().getStatus(),
                    "Spring Security no debe bloquear POST /auth/login (ruta pública)"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 2 — Rutas protegidas sin JWT: rechazadas con 403
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void categorias_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/categories/active"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearCategoria_sinToken_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/categories")
                .contentType("application/json")
                .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void productos_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/products/low-stock"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearProducto_sinToken_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/products")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void proveedores_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/purchases/suppliers/active"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordenes_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/purchases/orders/status/PENDING"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientes_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/sales/clients/active"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordenesVenta_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/sales/orders/status/PENDING"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservaciones_sinToken_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/sales/reservations/summary"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 3 — Rutas protegidas con JWT válido: Spring Security permite el paso
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void categorias_conTokenValido_noRetorna403() throws Exception {
        autenticar();
        when(categoryService.getAllActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inventory/categories/active")
                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void productos_conTokenValido_noRetorna403() throws Exception {
        autenticar();
        when(productService.getLowStockProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inventory/products/low-stock")
                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void proveedores_conTokenValido_noRetorna403() throws Exception {
        autenticar();
        when(supplierService.getAllActiveSuppliers()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/purchases/suppliers/active")
                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void ordenes_conTokenValido_noRetorna403() throws Exception {
        autenticar();
        when(purchaseOrderService.findByStatus("PENDING")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/purchases/orders/status/PENDING")
                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void clientes_conTokenValido_noRetorna403() throws Exception {
        autenticar();
        when(clientService.getAllActiveClients()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sales/clients/active")
                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void reservaciones_conTokenValido_noRetorna403() throws Exception {
        autenticar();
        when(reservationService.getSummary()).thenReturn(
            com.codigo2enter.almacenes.modules.sales.dto.ReservationSummaryDTO.builder()
                .totalApprovedOrders(0).totalReservedUnits(0).build());

        mockMvc.perform(get("/api/v1/sales/reservations/summary")
                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 4 — Token inválido: rechazado con 403
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void recursos_conTokenManipulado_retorna403() throws Exception {
        // Firma manipulada → extractUsername lanza excepción
        // El filtro la captura silenciosamente y no establece autenticación
        // Spring Security ve request sin autenticar en ruta protegida → 403
        when(jwtUtils.extractUsername("token.manipulado"))
                .thenThrow(new RuntimeException("JWT signature does not match"));

        mockMvc.perform(get("/api/v1/inventory/products/low-stock")
                .header("Authorization", "Bearer token.manipulado"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recursos_conTokenNoValidado_retorna403() throws Exception {
        // Token técnicamente parseado pero inválido (expirado, revocado, etc.)
        when(jwtUtils.extractUsername(TOKEN)).thenReturn("tester01");
        when(jwtUtils.validateToken(TOKEN)).thenReturn(false);

        mockMvc.perform(get("/api/v1/inventory/categories/active")
                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 5 — Autorización por rol (RBAC)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void gestionUsuarios_conTokenAdmin_noRetorna403() throws Exception {
        // El endpoint ahora llama a getAllUsers(int page, int size).
        when(userService.getAllUsers(0, 20)).thenReturn(
                PageResponseDTO.<UserResponseDTO>builder()
                        .content(List.of()).currentPage(0).totalPages(0)
                        .totalElements(0).size(20).first(true).last(true).build());
        String tok = tokenConRol("ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/auth/users").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk());
    }

    @Test
    void gestionUsuarios_conTokenManager_retorna403() throws Exception {
        String tok = tokenConRol("ROLE_MANAGER");
        mockMvc.perform(get("/api/v1/auth/users").header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestionUsuarios_conTokenWarehouseman_retorna403() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        mockMvc.perform(get("/api/v1/auth/users").header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearProducto_conTokenWarehouseman_retorna403() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        mockMvc.perform(post("/api/v1/inventory/products")
                .header("Authorization", "Bearer " + tok)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearProducto_conTokenManager_noRetorna403() throws Exception {
        when(productService.createProduct(any())).thenReturn(null);
        String tok = tokenConRol("ROLE_MANAGER");
        mockMvc.perform(post("/api/v1/inventory/products")
                .header("Authorization", "Bearer " + tok)
                .contentType("application/json").content("{}"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "MANAGER puede crear productos"));
    }

    @Test
    void registrarMovimientoStock_conTokenWarehouseman_noRetorna403() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        mockMvc.perform(post("/api/v1/inventory/products/movement")
                .header("Authorization", "Bearer " + tok)
                .contentType("application/json").content("{}"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "WAREHOUSEMAN puede registrar movimientos de stock"));
    }

    @Test
    void aprobarOrdenVenta_conTokenSales_retorna403() throws Exception {
        String tok = tokenConRol("ROLE_SALES");
        mockMvc.perform(patch("/api/v1/sales/orders/1/approve")
                .header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelarOrdenVenta_conTokenSales_noRetorna403() throws Exception {
        when(saleOrderService.cancelOrder(1L)).thenReturn(null);
        String tok = tokenConRol("ROLE_SALES");
        mockMvc.perform(patch("/api/v1/sales/orders/1/cancel")
                .header("Authorization", "Bearer " + tok))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "SALES puede cancelar órdenes de venta"));
    }

    @Test
    void entregarOrdenVenta_conTokenWarehouseman_noRetorna403() throws Exception {
        when(saleOrderService.deliverOrder(1L)).thenReturn(null);
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        mockMvc.perform(patch("/api/v1/sales/orders/1/deliver")
                .header("Authorization", "Bearer " + tok))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "WAREHOUSEMAN puede entregar órdenes de venta"));
    }

    @Test
    void crearOrdenVenta_conTokenSales_noRetorna403() throws Exception {
        when(saleOrderService.createOrder(any())).thenReturn(null);
        String tok = tokenConRol("ROLE_SALES");
        mockMvc.perform(post("/api/v1/sales/orders")
                .header("Authorization", "Bearer " + tok)
                .contentType("application/json").content("{}"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "SALES puede crear órdenes de venta"));
    }

    @Test
    void aprobarOrdenCompra_conTokenWarehouseman_retorna403() throws Exception {
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        mockMvc.perform(patch("/api/v1/purchases/orders/1/approve")
                .header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    void recibirOrdenCompra_conTokenWarehouseman_noRetorna403() throws Exception {
        when(purchaseOrderService.receiveOrder(1L)).thenReturn(null);
        String tok = tokenConRol("ROLE_WAREHOUSEMAN");
        mockMvc.perform(patch("/api/v1/purchases/orders/1/receive")
                .header("Authorization", "Bearer " + tok))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "WAREHOUSEMAN puede recibir órdenes de compra"));
    }

    @Test
    void eliminarCliente_conTokenSales_retorna403() throws Exception {
        String tok = tokenConRol("ROLE_SALES");
        mockMvc.perform(delete("/api/v1/sales/clients/1")
                .header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminarCliente_conTokenManager_noRetorna403() throws Exception {
        String tok = tokenConRol("ROLE_MANAGER");
        mockMvc.perform(delete("/api/v1/sales/clients/1")
                .header("Authorization", "Bearer " + tok))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "MANAGER puede eliminar clientes"));
    }
}
