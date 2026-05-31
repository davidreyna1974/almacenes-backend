package com.codigo2enter.almacenes.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests @SpringBootTest que verifican las reglas RBAC de SecurityConfig
 * usando tokens JWT reales — sin mocks de JwtUtils.
 *
 * La cadena completa que se verifica aquí pero NO en SecurityFilterTest (Tipo B*):
 *
 *   Login HTTP → JWT firmado por JwtUtils.generateToken()
 *     → JwtAuthenticationFilter.doFilterInternal()
 *     → jwtUtils.extractUsername() real (sin mock)
 *     → jwtUtils.validateToken() real
 *     → jwtUtils.extractRoles() real → SimpleGrantedAuthority cargado
 *     → SecurityConfig.hasRole() evalúa contra los roles reales del token
 *     → 200 o 403
 *
 * Gaps cubiertos:
 *
 *   Gap 2 — SecurityFilterTest mockea JwtUtils: no detecta un bug en extractRoles()
 *            que deserialice incorrectamente los roles del JWT real, ni tampoco
 *            detectaría si JwtAuthenticationFilter no carga las authorities.
 *            Aquí la cadena completa se ejerce con una BD y JWT reales.
 *
 *   Gap 3 — No existía test automatizado del flujo completo de ROLE_MANAGER.
 *            @Order(14) crea categoría, producto, orden de compra y la aprueba,
 *            verificando que el aprobador registrado en BD es el usuario MANAGER.
 *
 * Convención de @Order:
 *   1-2   Setup: crear usuarios y obtener tokens
 *   3-10  Accesos denegados (403 por rol insuficiente)
 *   11-13 Accesos permitidos (no 403)
 *   14-16 Flujos de negocio por rol (Gap 3)
 *   17    Integridad del token: firma manipulada → 403
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RbacIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;

    private String base;

    // Tokens JWT reales generados por el servidor — compartidos entre tests via static
    private static String tokenAdmin;
    private static String tokenManager;
    private static String tokenWarehouseman;
    private static String tokenSales;

    // IDs de usuarios creados en @Order(1)
    private static Long managerId;
    private static Long warehousemanId;
    private static Long salesUserId;

    // IDs de recursos del flujo completo MANAGER (@Order 14-16)
    private static Long supplierId;
    private static Long managerCategoryId;
    private static Long managerProductId;
    private static Long managerPurchaseOrderId;

    // Sufijo único por ejecución para evitar colisiones de nombre/RFC en la BD
    private static final String SUFFIX = "R" + (System.currentTimeMillis() % 100000);

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port + "/api/v1";
        if (tokenAdmin == null) {
            tokenAdmin = login("admin", "Admin123!");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 1 — Setup: crear usuarios con cada rol y obtener sus JWT reales
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Admin crea tres usuarios con roles distintos. Verifica que el endpoint
     * de gestión de usuarios acepta los cuatro roles definidos en la BD.
     */
    @Test
    @Order(1)
    @SuppressWarnings("unchecked")
    void setup_adminCreaUsuariosConCadaRol() {
        // MANAGER
        Map<String, Object> mgrBody = new HashMap<>();
        mgrBody.put("username", "mgr" + SUFFIX);
        mgrBody.put("password", "Manager1!");
        mgrBody.put("email",    "mgr" + SUFFIX + "@rbac.com");
        mgrBody.put("roles",    Set.of("ROLE_MANAGER"));
        ResponseEntity<Map> mgrResp = restTemplate.postForEntity(
            base + "/auth/users", new HttpEntity<>(mgrBody, jsonHeaders(tokenAdmin)), Map.class);
        assertEquals(HttpStatus.CREATED, mgrResp.getStatusCode(),
            "Admin debe poder crear usuario con ROLE_MANAGER");
        managerId = ((Number) mgrResp.getBody().get("id")).longValue();

        // WAREHOUSEMAN
        Map<String, Object> whBody = new HashMap<>();
        whBody.put("username", "wh" + SUFFIX);
        whBody.put("password", "Warehouse1!");
        whBody.put("email",    "wh" + SUFFIX + "@rbac.com");
        whBody.put("roles",    Set.of("ROLE_WAREHOUSEMAN"));
        ResponseEntity<Map> whResp = restTemplate.postForEntity(
            base + "/auth/users", new HttpEntity<>(whBody, jsonHeaders(tokenAdmin)), Map.class);
        assertEquals(HttpStatus.CREATED, whResp.getStatusCode(),
            "Admin debe poder crear usuario con ROLE_WAREHOUSEMAN");
        warehousemanId = ((Number) whResp.getBody().get("id")).longValue();

        // SALES
        Map<String, Object> salesBody = new HashMap<>();
        salesBody.put("username", "sales" + SUFFIX);
        salesBody.put("password", "Sales123!");
        salesBody.put("email",    "sales" + SUFFIX + "@rbac.com");
        salesBody.put("roles",    Set.of("ROLE_SALES"));
        ResponseEntity<Map> salesResp = restTemplate.postForEntity(
            base + "/auth/users", new HttpEntity<>(salesBody, jsonHeaders(tokenAdmin)), Map.class);
        assertEquals(HttpStatus.CREATED, salesResp.getStatusCode(),
            "Admin debe poder crear usuario con ROLE_SALES");
        salesUserId = ((Number) salesResp.getBody().get("id")).longValue();
    }

    /**
     * Obtiene tokens JWT reales para cada rol via login HTTP.
     *
     * Esto es lo que SecurityFilterTest (Tipo B*) NO hace: aquí JwtUtils.generateToken()
     * firma el token con la clave secreta real, y en tests posteriores ese token
     * pasa por JwtAuthenticationFilter sin ningún mock.
     */
    @Test
    @Order(2)
    void setup_loginConCadaRol_generaTokensJwtReales() {
        assertNotNull(managerId, "Requiere @Order(1) — no se crearon los usuarios");

        tokenManager      = login("mgr"   + SUFFIX, "Manager1!");
        tokenWarehouseman = login("wh"    + SUFFIX, "Warehouse1!");
        tokenSales        = login("sales" + SUFFIX, "Sales123!");

        assertNotNull(tokenManager,      "Login de MANAGER debe devolver JWT");
        assertNotNull(tokenWarehouseman, "Login de WAREHOUSEMAN debe devolver JWT");
        assertNotNull(tokenSales,        "Login de SALES debe devolver JWT");

        // Verificar que son JWTs bien formados (3 segmentos separados por '.')
        assertEquals(3, tokenManager.split("\\.").length,
            "JWT de MANAGER debe tener formato header.payload.signature");
        assertEquals(3, tokenWarehouseman.split("\\.").length,
            "JWT de WAREHOUSEMAN debe tener formato header.payload.signature");
        assertEquals(3, tokenSales.split("\\.").length,
            "JWT de SALES debe tener formato header.payload.signature");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 2 — Accesos denegados: 403 por rol insuficiente
    //
    // Para endpoints que requieren un {id} en la URL (como /orders/{id}/approve),
    // se usa el ID ficticio 99999. El 403 ocurre en SecurityConfig ANTES de que
    // el request llegue al controlador, por lo que el ID no importa para la prueba.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void manager_getUsuarios_retorna403() {
        assertNotNull(tokenManager, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/auth/users",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenManager)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "MANAGER no debe poder listar usuarios — /auth/users/** es solo ADMIN");
    }

    @Test
    @Order(4)
    void warehouseman_getUsuarios_retorna403() {
        assertNotNull(tokenWarehouseman, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/auth/users",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenWarehouseman)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "WAREHOUSEMAN no debe poder listar usuarios — /auth/users/** es solo ADMIN");
    }

    @Test
    @Order(5)
    void sales_getUsuarios_retorna403() {
        assertNotNull(tokenSales, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/auth/users",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenSales)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "SALES no debe poder listar usuarios — /auth/users/** es solo ADMIN");
    }

    @Test
    @Order(6)
    void warehouseman_crearCategoria_retorna403() {
        assertNotNull(tokenWarehouseman, "Requiere @Order(2)");
        Map<String, String> body = Map.of("name", "Cat-WH-" + SUFFIX, "description", "test");
        ResponseEntity<String> resp = restTemplate.postForEntity(
            base + "/inventory/categories",
            new HttpEntity<>(body, jsonHeaders(tokenWarehouseman)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "WAREHOUSEMAN no debe poder crear categorías — POST /inventory/** es ADMIN o MANAGER");
    }

    @Test
    @Order(7)
    void sales_crearCategoria_retorna403() {
        assertNotNull(tokenSales, "Requiere @Order(2)");
        Map<String, String> body = Map.of("name", "Cat-SL-" + SUFFIX, "description", "test");
        ResponseEntity<String> resp = restTemplate.postForEntity(
            base + "/inventory/categories",
            new HttpEntity<>(body, jsonHeaders(tokenSales)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "SALES no debe poder crear categorías — POST /inventory/** es ADMIN o MANAGER");
    }

    @Test
    @Order(8)
    void warehouseman_aprobarOrdenCompra_retorna403() {
        assertNotNull(tokenWarehouseman, "Requiere @Order(2)");
        // El 403 viene de SecurityConfig antes del controlador — el ID 99999 no importa
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/purchases/orders/99999/approve",
            HttpMethod.PATCH, new HttpEntity<>(jsonHeaders(tokenWarehouseman)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "WAREHOUSEMAN no debe poder aprobar órdenes de compra — " +
            "PATCH /purchases/** (sin /receive) es solo ADMIN o MANAGER");
    }

    @Test
    @Order(9)
    void sales_aprobarOrdenVenta_retorna403() {
        assertNotNull(tokenSales, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/sales/orders/99999/approve",
            HttpMethod.PATCH, new HttpEntity<>(jsonHeaders(tokenSales)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "SALES no debe poder aprobar órdenes de venta — " +
            "PATCH /sales/orders/*/approve es ADMIN o MANAGER");
    }

    @Test
    @Order(10)
    void sales_eliminarCliente_retorna403() {
        assertNotNull(tokenSales, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/sales/clients/99999",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders(tokenSales)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "SALES no debe poder desactivar clientes — " +
            "DELETE /sales/clients/** es ADMIN o MANAGER");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 3 — Accesos permitidos: verificar que el rol SÍ puede operar
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(11)
    void warehouseman_leerInventario_noRetorna403() {
        assertNotNull(tokenWarehouseman, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/inventory/categories/active",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenWarehouseman)), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "WAREHOUSEMAN debe poder leer el inventario — " +
            "GET /inventory/** permite ADMIN, MANAGER, WAREHOUSEMAN y SALES");
    }

    @Test
    @Order(12)
    void sales_leerInventario_noRetorna403() {
        assertNotNull(tokenSales, "Requiere @Order(2)");
        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/inventory/categories/active",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenSales)), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "SALES debe poder leer el inventario — " +
            "GET /inventory/** permite todos los roles autenticados");
    }

    @Test
    @Order(13)
    void todosLosRoles_perfilPropio_noRetorna403() {
        assertNotNull(tokenManager, "Requiere @Order(2)");

        ResponseEntity<String> mgrResp = restTemplate.exchange(
            base + "/auth/me", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders(tokenManager)), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, mgrResp.getStatusCode(),
            "MANAGER debe poder acceder a /auth/me (cualquier autenticado)");

        ResponseEntity<String> whResp = restTemplate.exchange(
            base + "/auth/me", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders(tokenWarehouseman)), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, whResp.getStatusCode(),
            "WAREHOUSEMAN debe poder acceder a /auth/me");

        ResponseEntity<String> salesResp = restTemplate.exchange(
            base + "/auth/me", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders(tokenSales)), String.class);
        assertNotEquals(HttpStatus.FORBIDDEN, salesResp.getStatusCode(),
            "SALES debe poder acceder a /auth/me");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 4 — Flujos de negocio por rol (Gap 3)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gap 3: flujo completo de ROLE_MANAGER.
     *
     * Verifica que MANAGER puede ejecutar el ciclo completo de abastecimiento:
     * crear categoría → crear producto → crear orden de compra → aprobarla.
     *
     * Adicionalmente verifica que el campo approvedByUsername en BD registra
     * al usuario MANAGER (no al admin) — prueba que la auditoría funciona
     * end-to-end con un usuario diferente al admin del DataInitializer.
     */
    @Test
    @Order(14)
    @SuppressWarnings("unchecked")
    void manager_flujoCompleto_creaCategoria_producto_ordenCompra_aprueba() {
        assertNotNull(tokenManager, "Requiere @Order(2)");

        // 1. MANAGER crea categoría
        Map<String, String> catBody = Map.of(
            "name", "Cat-Mgr-" + SUFFIX, "description", "Categoría de MANAGER"
        );
        ResponseEntity<Map> catResp = restTemplate.postForEntity(
            base + "/inventory/categories",
            new HttpEntity<>(catBody, jsonHeaders(tokenManager)), Map.class);
        assertEquals(HttpStatus.CREATED, catResp.getStatusCode(),
            "MANAGER debe poder crear categorías — POST /inventory/** permite ADMIN y MANAGER");
        managerCategoryId = ((Number) catResp.getBody().get("id")).longValue();

        // 2. Admin crea proveedor (compartido con @Order(15))
        Map<String, String> supBody = new HashMap<>();
        supBody.put("rfc",         "MGB" + SUFFIX);
        supBody.put("companyName", "Proveedor RBAC " + SUFFIX);
        supBody.put("email",       "prov" + SUFFIX + "@rbac.com");
        ResponseEntity<Map> supResp = restTemplate.postForEntity(
            base + "/purchases/suppliers",
            new HttpEntity<>(supBody, jsonHeaders(tokenAdmin)), Map.class);
        assertEquals(HttpStatus.CREATED, supResp.getStatusCode());
        supplierId = ((Number) supResp.getBody().get("id")).longValue();

        // 3. MANAGER crea producto
        Map<String, Object> prodBody = new HashMap<>();
        prodBody.put("sku",          "MGR-" + SUFFIX);
        prodBody.put("name",         "Producto MANAGER " + SUFFIX);
        prodBody.put("price",        299.00);
        prodBody.put("currentStock", 0);
        prodBody.put("minimumStock", 5);
        prodBody.put("status",       "AVAILABLE");
        prodBody.put("categoryId",   managerCategoryId);
        prodBody.put("supplierId",   supplierId);
        prodBody.put("unitCost",     150.00);
        ResponseEntity<Map> prodResp = restTemplate.postForEntity(
            base + "/inventory/products",
            new HttpEntity<>(prodBody, jsonHeaders(tokenManager)), Map.class);
        assertEquals(HttpStatus.CREATED, prodResp.getStatusCode(),
            "MANAGER debe poder crear productos");
        managerProductId = ((Number) prodResp.getBody().get("id")).longValue();

        // 4. MANAGER crea orden de compra
        Map<String, Object> detail = Map.of(
            "productId", managerProductId, "quantity", 10, "unitPrice", 150.00
        );
        Map<String, Object> poBody = Map.of(
            "supplierId", supplierId,
            "notes",      "Orden RBAC creada por MANAGER",
            "details",    List.of(detail)
        );
        ResponseEntity<Map> poResp = restTemplate.postForEntity(
            base + "/purchases/orders",
            new HttpEntity<>(poBody, jsonHeaders(tokenManager)), Map.class);
        assertEquals(HttpStatus.CREATED, poResp.getStatusCode(),
            "MANAGER debe poder crear órdenes de compra");
        managerPurchaseOrderId = ((Number) poResp.getBody().get("id")).longValue();

        // 5. MANAGER aprueba la orden
        ResponseEntity<Map> approveResp = restTemplate.exchange(
            base + "/purchases/orders/" + managerPurchaseOrderId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(jsonHeaders(tokenManager)), Map.class);
        assertEquals(HttpStatus.OK, approveResp.getStatusCode(),
            "MANAGER debe poder aprobar órdenes de compra");
        assertEquals("APPROVED", approveResp.getBody().get("status"));

        // Verificar en BD (GET posterior) que el aprobador fue el usuario MANAGER, no admin
        ResponseEntity<Map> getResp = restTemplate.exchange(
            base + "/purchases/orders/" + managerPurchaseOrderId,
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenAdmin)), Map.class);
        assertNotNull(getResp.getBody().get("approvedById"),
            "approvedById debe persistir en BD cuando el aprobador es MANAGER");
        assertEquals("mgr" + SUFFIX, getResp.getBody().get("approvedByUsername"),
            "El aprobador debe ser el usuario MANAGER (mgr" + SUFFIX + "), no el admin");
    }

    /**
     * WAREHOUSEMAN recibe la orden de compra aprobada por MANAGER en @Order(14).
     * Verifica que /purchases/orders/*\/receive está permitido para WAREHOUSEMAN.
     */
    @Test
    @Order(15)
    @SuppressWarnings("unchecked")
    void warehouseman_recibirOrdenCompraAprobada_retorna200() {
        assertNotNull(tokenWarehouseman, "Requiere @Order(2)");

        Long poId = managerPurchaseOrderId;
        if (poId == null) {
            // Fallback si @Order(14) falló — crear recursos mínimos como admin
            Long catId  = crearCategoriaComoAdmin("Cat-WHR-" + SUFFIX);
            Long supId  = crearProveedorComoAdmin("WHR" + SUFFIX.substring(0, Math.min(SUFFIX.length(), 4)));
            Long prodId = crearProductoComoAdmin("WHR-" + SUFFIX, catId, supId);
            poId = crearOrdenCompraAprobadaComoAdmin(prodId, supId);
        }

        ResponseEntity<Map> resp = restTemplate.exchange(
            base + "/purchases/orders/" + poId + "/receive",
            HttpMethod.PATCH, new HttpEntity<>(jsonHeaders(tokenWarehouseman)), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(),
            "WAREHOUSEMAN debe poder recibir órdenes de compra — " +
            "PATCH /purchases/orders/*/receive permite ADMIN, MANAGER y WAREHOUSEMAN");
        assertEquals("RECEIVED", resp.getBody().get("status"),
            "La orden debe pasar a estado RECEIVED");
    }

    /**
     * SALES crea una orden de venta — flujo positivo del rol más restrictivo.
     * Verifica que POST /sales/** permite a SALES aunque no puede aprobar.
     */
    @Test
    @Order(16)
    @SuppressWarnings("unchecked")
    void sales_crearOrdenVenta_retorna201() {
        assertNotNull(tokenSales, "Requiere @Order(2)");

        // Producto con stock — si @Order(15) recibió la orden, managerProductId ya tiene 10 unidades
        Long prodId = managerProductId;
        if (prodId == null) {
            Long catId = crearCategoriaComoAdmin("Cat-SLO-" + SUFFIX);
            Long supId = crearProveedorComoAdmin("SLO" + SUFFIX.substring(0, Math.min(SUFFIX.length(), 4)));
            prodId = crearProductoComoAdmin("SLO-" + SUFFIX, catId, supId);
        }

        Long clientId = crearClienteComoAdmin("Cliente RBAC " + SUFFIX);

        Map<String, Object> detail = Map.of("productId", prodId, "quantity", 1, "unitPrice", 299.00);
        Map<String, Object> body   = Map.of("clientId", clientId, "details", List.of(detail));

        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/sales/orders",
            new HttpEntity<>(body, jsonHeaders(tokenSales)), Map.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(),
            "SALES debe poder crear órdenes de venta — POST /sales/** permite ADMIN, MANAGER y SALES");
        assertEquals("PENDING", resp.getBody().get("status"),
            "Una orden creada por SALES debe iniciar en estado PENDING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOQUE 5 — Integridad del token
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que JwtAuthenticationFilter rechaza tokens con firma modificada.
     *
     * Esto prueba la cadena real: el filtro llama a jwtUtils.validateToken()
     * que lanza JwtException → el filtro captura la excepción y deja pasar
     * el request sin autenticar → Spring Security devuelve 403.
     *
     * SecurityFilterTest ya cubre este caso con un mock, pero aquí se verifica
     * que JJWT real detecta la manipulación y que el try-catch del filtro
     * funciona correctamente con la excepción real lanzada por JJWT.
     */
    @Test
    @Order(17)
    void tokenConFirmaManipulada_rutaProtegida_retorna403() {
        assertNotNull(tokenAdmin, "Requiere tokenAdmin");

        // Alterar el último carácter de la firma (tercer segmento del JWT)
        String[] partes = tokenAdmin.split("\\.");
        String firmaOriginal = partes[2];
        char ultimoChar = firmaOriginal.charAt(firmaOriginal.length() - 1);
        char charAlterado = (ultimoChar == 'A') ? 'B' : 'A';
        String firmaManipulada = firmaOriginal.substring(0, firmaOriginal.length() - 1) + charAlterado;
        String tokenManipulado = partes[0] + "." + partes[1] + "." + firmaManipulada;

        ResponseEntity<String> resp = restTemplate.exchange(
            base + "/inventory/categories/active",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders(tokenManipulado)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "Token con firma alterada debe retornar 403, no 200 ni 500. " +
            "Si devuelve 500, el filtro no captura la JwtException de JJWT. " +
            "Si devuelve 200, el filtro no valida la firma.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/auth/login", new HttpEntity<>(body, h), Map.class);
        if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) return null;
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Long crearCategoriaComoAdmin(String name) {
        Map<String, String> body = Map.of("name", name, "description", "test RBAC");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/categories",
            new HttpEntity<>(body, jsonHeaders(tokenAdmin)), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearProveedorComoAdmin(String rfc) {
        Map<String, String> body = new HashMap<>();
        body.put("rfc",         rfc);
        body.put("companyName", "Prov " + rfc);
        body.put("email",       rfc.toLowerCase() + "@rbac.com");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/purchases/suppliers",
            new HttpEntity<>(body, jsonHeaders(tokenAdmin)), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearProductoComoAdmin(String sku, Long catId, Long supId) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku",          sku);
        body.put("name",         "Prod " + sku);
        body.put("price",        199.00);
        body.put("currentStock", 20);
        body.put("minimumStock", 2);
        body.put("status",       "AVAILABLE");
        body.put("categoryId",   catId);
        body.put("supplierId",   supId);
        body.put("unitCost",     100.00);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/products",
            new HttpEntity<>(body, jsonHeaders(tokenAdmin)), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearClienteComoAdmin(String name) {
        Map<String, String> body = Map.of(
            "name",  name,
            "email", name.replaceAll("\\s+", "").toLowerCase() + "@rbac.com"
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/sales/clients",
            new HttpEntity<>(body, jsonHeaders(tokenAdmin)), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearOrdenCompraAprobadaComoAdmin(Long prodId, Long supId) {
        Map<String, Object> detail  = Map.of("productId", prodId, "quantity", 5, "unitPrice", 100.00);
        Map<String, Object> poBody  = Map.of(
            "supplierId", supId, "notes", "Fallback RBAC", "details", List.of(detail)
        );
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            base + "/purchases/orders",
            new HttpEntity<>(poBody, jsonHeaders(tokenAdmin)), Map.class);
        Long poId = ((Number) createResp.getBody().get("id")).longValue();
        restTemplate.exchange(
            base + "/purchases/orders/" + poId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(jsonHeaders(tokenAdmin)), Map.class);
        return poId;
    }
}
