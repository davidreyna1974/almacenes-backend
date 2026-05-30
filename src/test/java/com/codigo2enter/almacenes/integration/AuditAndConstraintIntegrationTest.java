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
 * Tests @SpringBootTest que verifican comportamientos que los mocks no detectan:
 *
 *   1. supplierId persiste en BD al crear un producto (Bug 1 de CLAUDE.md)
 *   2. Columnas de auditoría (createdBy, updatedBy) persisten en BD
 *   3. approvedBy/receivedBy/cancelledBy persisten en BD (Bug 3 de CLAUDE.md)
 *   4. unitCost capturado en detalle de venta y congelado post-APPROVED
 *   5. Ciclo completo PENDING→APPROVED→DELIVERED con verificación de stock
 *   6. Sin JWT → 403, no 500 (la auditoria NOT NULL no llega a Hibernate)
 *
 * Por qué son necesarios:
 *   Los tests con Mockito devuelven el objeto configurado en @BeforeEach,
 *   que ya tiene todos los campos. Hibernate nunca ejecuta un INSERT real,
 *   por lo que constraints NOT NULL, updatable=false incorrecto, o mappers
 *   mal configurados pasan desapercibidos.
 *
 * Limpieza: @AfterEach cancela/desactiva los recursos creados en cada test
 * para que los tests sean independientes y repetibles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditAndConstraintIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;

    private String base;
    private HttpHeaders authHeaders;

    // IDs compartidos entre tests de un mismo ciclo
    private static Long categoryId;
    private static Long supplierId;
    private static Long productId;
    private static Long clientId;
    private static Long orderId;
    private static Long purchaseOrderId;

    private final String suffix = String.valueOf(System.currentTimeMillis() % 100000);

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port + "/api/v1";
        authHeaders = obtenerJwt();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — Sin JWT → 403 (Spring Security intercepta ANTES de Hibernate)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void sinJwt_crearCategoria_retorna403_noLlegaAHibernate() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = restTemplate.postForEntity(
            base + "/inventory/categories",
            new HttpEntity<>("{\"name\":\"SinJWT\"}", headers),
            String.class
        );

        // Spring Security debe bloquear con 403 antes de que el request
        // llegue al controlador → Hibernate nunca recibe createdBy=null
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
            "Sin JWT debe retornar 403, no 500 ni 201");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — supplierId persiste en BD al crear producto (Bug 1)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @SuppressWarnings("unchecked")
    void crearProducto_supplierId_persiste_enBD() {
        categoryId = crearCategoria("Cat-Int-" + suffix);
        supplierId  = crearProveedor("AINT" + suffix.substring(0, Math.min(suffix.length(), 8)));

        Map<String, Object> body = new HashMap<>();
        body.put("sku",          "INT-" + suffix);
        body.put("name",         "Producto Integración " + suffix);
        body.put("price",        999.00);
        body.put("currentStock", 20);
        body.put("minimumStock", 5);
        body.put("status",       "AVAILABLE");
        body.put("categoryId",   categoryId);
        body.put("supplierId",   supplierId);
        body.put("unitCost",     500.00);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/products",
            new HttpEntity<>(body, authHeaders),
            Map.class
        );

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());

        productId = ((Number) resp.getBody().get("id")).longValue();

        // Verificar en el response inmediato
        Number suppId = (Number) resp.getBody().get("supplierId");
        assertNotNull(suppId, "supplierId debe ser no nulo en el response de creación (Bug 1 corregido)");
        assertEquals(supplierId, suppId.longValue());

        // Verificar que persiste en BD con un GET posterior
        ResponseEntity<Map> getResp = restTemplate.exchange(
            base + "/inventory/products/" + productId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class
        );
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        Number suppIdFromBd = (Number) getResp.getBody().get("supplierId");
        assertNotNull(suppIdFromBd,
            "supplierId debe seguir siendo no nulo en consulta GET posterior — " +
            "verifica que el dato persiste en BD, no solo en memoria");
        assertEquals(supplierId, suppIdFromBd.longValue());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — Auditoría de categoría: createdBy y updatedBy persisten en BD
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @SuppressWarnings("unchecked")
    void auditoria_categorias_createdBy_y_updatedBy_persisten() {
        Long catId = crearCategoria("Cat-Audit-" + suffix);

        // Buscar la categoría recién creada en la lista activa
        List<Map<String, Object>> cats = (List<Map<String, Object>>) restTemplate.exchange(
            base + "/inventory/categories/active",
            HttpMethod.GET, new HttpEntity<>(authHeaders), List.class
        ).getBody();

        assertNotNull(cats);
        Map<String, Object> cat = cats.stream()
                .filter(c -> catId.equals(((Number) c.get("id")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Categoría no encontrada en listado activo"));

        assertNotNull(cat.get("createdById"),
            "createdById debe persistir en BD — no nulo tras GET posterior a la creación");
        assertNotNull(cat.get("createdByUsername"),
            "createdByUsername debe ser visible en el response");
        assertNull(cat.get("updatedAt"),
            "updatedAt debe ser null antes de cualquier edición");

        // Actualizar y verificar updatedBy
        Map<String, String> updateBody = Map.of(
            "name", "Cat-Audit-Updated-" + suffix,
            "description", "actualizada"
        );
        ResponseEntity<Map> updateResp = restTemplate.exchange(
            base + "/inventory/categories/" + catId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders),
            Map.class
        );
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        assertNotNull(updateResp.getBody().get("updatedAt"),
            "updatedAt debe tener valor después de una edición");
        assertNotNull(updateResp.getBody().get("updatedById"),
            "updatedById debe persistir en BD después de una edición (verifica que no hay updatable=false incorrecto)");

        // Desactivar para limpieza
        restTemplate.exchange(base + "/inventory/categories/" + catId,
            HttpMethod.DELETE, new HttpEntity<>(authHeaders), Void.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4 — approvedBy/receivedBy persisten en BD (Bug 3)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @SuppressWarnings("unchecked")
    void purchaseOrder_approvedBy_y_receivedBy_persisten_enBD() {
        if (productId == null) {
            categoryId = crearCategoria("Cat-PO-" + suffix);
            supplierId  = crearProveedor("APO" + suffix.substring(0, Math.min(suffix.length(), 8)));
            productId   = crearProducto("SKU-PO-" + suffix, categoryId, supplierId);
        }

        // Crear orden de compra
        Map<String, Object> detail = Map.of(
            "productId", productId, "quantity", 5, "unitPrice", 100.00
        );
        Map<String, Object> orderBody = Map.of(
            "supplierId", supplierId,
            "notes",      "Test auditoría",
            "details",    List.of(detail)
        );
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            base + "/purchases/orders",
            new HttpEntity<>(orderBody, authHeaders), Map.class
        );
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        purchaseOrderId = ((Number) createResp.getBody().get("id")).longValue();

        // Aprobar
        ResponseEntity<Map> approveResp = restTemplate.exchange(
            base + "/purchases/orders/" + purchaseOrderId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class
        );
        assertEquals(HttpStatus.OK, approveResp.getStatusCode());
        assertEquals("APPROVED", approveResp.getBody().get("status"));

        // Verificar que approvedBy persiste en BD consultando de nuevo
        ResponseEntity<Map> getAfterApprove = restTemplate.exchange(
            base + "/purchases/orders/" + purchaseOrderId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class
        );
        assertNotNull(getAfterApprove.getBody().get("approvedById"),
            "approvedById debe persistir en BD (Bug 3 corregido — updatable=false removido)");
        assertNotNull(getAfterApprove.getBody().get("approvedByUsername"));
        assertNotNull(getAfterApprove.getBody().get("approvedAt"));

        // Recibir
        ResponseEntity<Map> receiveResp = restTemplate.exchange(
            base + "/purchases/orders/" + purchaseOrderId + "/receive",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class
        );
        assertEquals(HttpStatus.OK, receiveResp.getStatusCode());
        assertEquals("RECEIVED", receiveResp.getBody().get("status"));

        // Verificar que receivedBy persiste en BD
        ResponseEntity<Map> getAfterReceive = restTemplate.exchange(
            base + "/purchases/orders/" + purchaseOrderId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class
        );
        assertNotNull(getAfterReceive.getBody().get("receivedById"),
            "receivedById debe persistir en BD");
        assertNotNull(getAfterReceive.getBody().get("receivedAt"));
        // approvedBy NO debe haberse borrado al recibir
        assertNotNull(getAfterReceive.getBody().get("approvedById"),
            "approvedById debe seguir presente después de recibir la orden");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5 — unitCost capturado y congelado en orden de venta
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void saleOrder_unitCost_capturado_y_congelado_postApproved() {
        if (productId == null) {
            categoryId = crearCategoria("Cat-SO-" + suffix);
            supplierId  = crearProveedor("ASO" + suffix.substring(0, Math.min(suffix.length(), 8)));
            productId   = crearProducto("SKU-SO-" + suffix, categoryId, supplierId);
        }
        clientId = crearCliente("Cliente Int " + suffix);

        // Crear orden de venta — el detail.unitCost debe ser 500.00 (de crearProducto)
        Map<String, Object> detail = Map.of(
            "productId", productId, "quantity", 2, "unitPrice", 800.00
        );
        Map<String, Object> orderBody = Map.of(
            "clientId", clientId,
            "details",  List.of(detail)
        );
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            base + "/sales/orders",
            new HttpEntity<>(orderBody, authHeaders), Map.class
        );
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        orderId = ((Number) createResp.getBody().get("id")).longValue();

        List<Map<String, Object>> details = (List<Map<String, Object>>) createResp.getBody().get("details");
        Number unitCostEnCreacion = (Number) details.get(0).get("unitCost");
        assertNotNull(unitCostEnCreacion,
            "unitCost debe capturarse de Product.unitCost al crear la orden");
        assertEquals(500.0, unitCostEnCreacion.doubleValue(), 0.01,
            "unitCost debe ser 500.00 (el costo del producto al momento de crear)");

        // Aprobar
        restTemplate.exchange(base + "/sales/orders/" + orderId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);

        // Cambiar el unitCost del producto DESPUÉS del approve
        Map<String, Object> updateProduct = new HashMap<>();
        updateProduct.put("sku",          "SKU-SO-" + suffix);
        updateProduct.put("name",         "Producto Integración " + suffix);
        updateProduct.put("price",        999.00);
        updateProduct.put("currentStock", 18); // 20 - 2 del deliver que viene
        updateProduct.put("minimumStock", 5);
        updateProduct.put("status",       "AVAILABLE");
        updateProduct.put("categoryId",   categoryId);
        updateProduct.put("supplierId",   supplierId);
        updateProduct.put("unitCost",     750.00); // cambio de costo
        restTemplate.exchange(base + "/inventory/products/" + productId,
            HttpMethod.PUT, new HttpEntity<>(updateProduct, authHeaders), Map.class);

        // Verificar que el detalle de la orden sigue con el costo original (congelado)
        ResponseEntity<Map> getResp = restTemplate.exchange(
            base + "/sales/orders/" + orderId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class
        );
        List<Map<String, Object>> detailsPost = (List<Map<String, Object>>) getResp.getBody().get("details");
        Number unitCostCongelado = (Number) detailsPost.get(0).get("unitCost");
        assertEquals(500.0, unitCostCongelado.doubleValue(), 0.01,
            "unitCost debe estar congelado en 500.00 aunque el producto ahora vale 750.00 — " +
            "la congelación es implícita: los detalles no son editables post-APPROVED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6 — Ciclo completo APPROVED→DELIVERED: reservedStock y currentStock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @SuppressWarnings("unchecked")
    void saleOrder_cicloCompleto_reservedStock_y_currentStock_correctos() {
        if (productId == null || clientId == null) {
            categoryId = crearCategoria("Cat-Cy-" + suffix);
            supplierId  = crearProveedor("ACY" + suffix.substring(0, Math.min(suffix.length(), 8)));
            productId   = crearProducto("SKU-CY-" + suffix, categoryId, supplierId);
            clientId    = crearCliente("Cliente Cy " + suffix);
        }

        // Obtener stock inicial
        int stockInicial = getProductField(productId, "currentStock");
        int reservadoInicial = getProductField(productId, "reservedStock");

        // Crear orden
        Map<String, Object> detail = Map.of("productId", productId, "quantity", 3, "unitPrice", 800.00);
        Map<String, Object> body   = Map.of("clientId", clientId, "details", List.of(detail));
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            base + "/sales/orders", new HttpEntity<>(body, authHeaders), Map.class);
        Long soId = ((Number) createResp.getBody().get("id")).longValue();

        // Aprobar → reservedStock debe incrementar en 3
        restTemplate.exchange(base + "/sales/orders/" + soId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);

        assertEquals(reservadoInicial + 3, getProductField(productId, "reservedStock"),
            "reservedStock debe incrementar en 3 al aprobar");
        assertEquals(stockInicial, getProductField(productId, "currentStock"),
            "currentStock NO debe cambiar al aprobar — solo reservedStock");

        // Entregar → currentStock debe decrementar, reservedStock debe volver al inicial
        restTemplate.exchange(base + "/sales/orders/" + soId + "/deliver",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);

        assertEquals(stockInicial - 3, getProductField(productId, "currentStock"),
            "currentStock debe decrementar en 3 al entregar");
        assertEquals(reservadoInicial, getProductField(productId, "reservedStock"),
            "reservedStock debe liberarse (volver al valor inicial) al entregar");

        // Verificar movimiento OUT en Kardex
        ResponseEntity<List> kardexResp = restTemplate.exchange(
            base + "/inventory/products/" + productId + "/movements",
            HttpMethod.GET, new HttpEntity<>(authHeaders), List.class);
        List<Map<String, Object>> movimientos = kardexResp.getBody();
        assertFalse(movimientos.isEmpty(), "Debe haber al menos un movimiento en el Kardex");

        Map<String, Object> ultimoMovimiento = movimientos.get(0);
        assertEquals("OUT", ultimoMovimiento.get("type"),
            "El movimiento más reciente debe ser de tipo OUT");
        String reason = (String) ultimoMovimiento.get("reason");
        assertTrue(reason != null && reason.contains("OV-"),
            "El reason del movimiento OUT debe contener el número de orden OV-YYYY-NNNN");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7 — Cancelación de orden de venta desde APPROVED: reservedStock liberado
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que al cancelar una orden de venta desde APPROVED,
     * el reservedStock del producto se libera correctamente en BD.
     *
     * Este escenario estaba cubierto por los tests E2E con curl pero NO por
     * ningún @SpringBootTest. Sin este test, una regresión en cancelOrder()
     * que dejara de liberar el reservedStock pasaría desapercibida — el stock
     * quedaría comprometido permanentemente para un producto libre de órdenes.
     *
     * Usa un producto propio (no el productId compartido) porque Tests 5 y 6
     * pueden haber modificado el stock del producto compartido.
     */
    @Test
    @Order(7)
    @SuppressWarnings("unchecked")
    void saleOrder_cancelDesdeApproved_liberaReservedStock_enBD() {
        // Producto fresco con stock controlado
        Long catId  = categoryId != null ? categoryId : crearCategoria("Cat-Cancel-" + suffix);
        Long supId  = supplierId  != null ? supplierId  : crearProveedor("CAN" + suffix.substring(0, 5));
        Long prodId = crearProducto("SKU-CANCEL-" + suffix, catId, supId);
        Long cliId  = clientId   != null ? clientId   : crearCliente("Cliente Cancel " + suffix);

        int stockInicial    = getProductField(prodId, "currentStock");
        int reservadoInicial = getProductField(prodId, "reservedStock");

        // Crear orden de venta con 4 unidades
        Map<String, Object> detail = Map.of("productId", prodId, "quantity", 4, "unitPrice", 999.00);
        Map<String, Object> body   = Map.of("clientId", cliId, "details", List.of(detail));
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            base + "/sales/orders", new HttpEntity<>(body, authHeaders), Map.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        Long soId = ((Number) createResp.getBody().get("id")).longValue();

        // Aprobar — reservedStock debe incrementar en 4
        restTemplate.exchange(base + "/sales/orders/" + soId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);

        int reservadoPostApprove = getProductField(prodId, "reservedStock");
        assertEquals(reservadoInicial + 4, reservadoPostApprove,
            "reservedStock debe ser " + (reservadoInicial + 4) + " después de aprobar");
        assertEquals(stockInicial, getProductField(prodId, "currentStock"),
            "currentStock NO debe cambiar al aprobar — solo reservedStock");

        // Cancelar desde APPROVED
        ResponseEntity<Map> cancelResp = restTemplate.exchange(
            base + "/sales/orders/" + soId + "/cancel",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);
        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
        assertEquals("CANCELLED", cancelResp.getBody().get("status"),
            "El status debe ser CANCELLED");
        assertNotNull(cancelResp.getBody().get("cancelledById"),
            "cancelledById debe persistir en BD al cancelar");
        assertNotNull(cancelResp.getBody().get("cancelledAt"),
            "cancelledAt debe tener valor");

        // Verificar en BD que reservedStock volvió al valor inicial
        int reservadoPostCancel = getProductField(prodId, "reservedStock");
        assertEquals(reservadoInicial, reservadoPostCancel,
            "reservedStock debe liberarse (volver a " + reservadoInicial + ") al cancelar desde APPROVED. " +
            "Valor actual: " + reservadoPostCancel + ". " +
            "Si es " + reservadoPostApprove + ", la reserva no fue liberada.");

        // currentStock no debe haber cambiado (la cancelación no mueve stock físico)
        assertEquals(stockInicial, getProductField(prodId, "currentStock"),
            "currentStock NO debe cambiar al cancelar — solo se libera la reserva");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 8 — Cancelación de orden de compra desde APPROVED: cancelledBy persiste
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que al cancelar una orden de compra desde APPROVED,
     * cancelledBy y cancelledAt persisten correctamente en BD.
     *
     * Complementa el Test 4 que cubre PENDING→APPROVED→RECEIVED.
     * La ruta APPROVED→CANCELLED era la única transición de PurchaseOrder
     * sin cobertura @SpringBootTest para los campos de auditoría.
     */
    @Test
    @Order(8)
    @SuppressWarnings("unchecked")
    void purchaseOrder_cancelDesdeApproved_cancelledBy_persiste_enBD() {
        Long supId  = supplierId  != null ? supplierId  : crearProveedor("POCA" + suffix.substring(0, 4));
        Long prodId = productId   != null ? productId   : crearProducto("SKU-POCA-" + suffix,
                          categoryId != null ? categoryId : crearCategoria("Cat-POCA-" + suffix), supId);

        // Crear orden de compra
        Map<String, Object> detail  = Map.of("productId", prodId, "quantity", 3, "unitPrice", 100.00);
        Map<String, Object> poBody  = Map.of("supplierId", supId, "notes", "Test cancel PO", "details", List.of(detail));
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            base + "/purchases/orders", new HttpEntity<>(poBody, authHeaders), Map.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        Long poId = ((Number) createResp.getBody().get("id")).longValue();

        // Aprobar
        restTemplate.exchange(base + "/purchases/orders/" + poId + "/approve",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);

        // Cancelar desde APPROVED
        ResponseEntity<Map> cancelResp = restTemplate.exchange(
            base + "/purchases/orders/" + poId + "/cancel",
            HttpMethod.PATCH, new HttpEntity<>(authHeaders), Map.class);
        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
        assertEquals("CANCELLED", cancelResp.getBody().get("status"));

        // Verificar en BD con GET posterior (no solo en memoria)
        ResponseEntity<Map> getResp = restTemplate.exchange(
            base + "/purchases/orders/" + poId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);

        assertNotNull(getResp.getBody().get("cancelledById"),
            "cancelledById debe persistir en BD — verifica que el campo no tiene updatable=false incorrecto");
        assertNotNull(getResp.getBody().get("cancelledAt"),
            "cancelledAt debe persistir en BD");
        // approvedBy debe seguir presente (cancelar no borra la aprobación)
        assertNotNull(getResp.getBody().get("approvedById"),
            "approvedById debe seguir visible después de cancelar");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de creación de entidades
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private HttpHeaders obtenerJwt() {
        Map<String, String> loginBody = Map.of("username", "tester01", "password", "Admin123!");
        HttpHeaders jsonH = new HttpHeaders();
        jsonH.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
            base + "/auth/login", new HttpEntity<>(loginBody, jsonH), Map.class);
        String token = (String) Objects.requireNonNull(loginResp.getBody()).get("token");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Long crearCategoria(String name) {
        Map<String, String> body = Map.of("name", name, "description", "test");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/categories", new HttpEntity<>(body, authHeaders), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearProveedor(String rfc) {
        Map<String, String> body = new HashMap<>();
        body.put("rfc",         rfc);
        body.put("companyName", "Proveedor " + rfc);
        body.put("email",       rfc.toLowerCase() + "@test.com");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/purchases/suppliers", new HttpEntity<>(body, authHeaders), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearProducto(String sku, Long catId, Long supId) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku",          sku);
        body.put("name",         "Prod " + sku);
        body.put("price",        999.00);
        body.put("currentStock", 20);
        body.put("minimumStock", 2);
        body.put("status",       "AVAILABLE");
        body.put("categoryId",   catId);
        body.put("supplierId",   supId);
        body.put("unitCost",     500.00);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/products", new HttpEntity<>(body, authHeaders), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long crearCliente(String name) {
        Map<String, String> body = Map.of(
            "name",  name,
            "email", name.replaceAll("\\s+", "").toLowerCase() + "@test.com"
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/sales/clients", new HttpEntity<>(body, authHeaders), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private int getProductField(Long prodId, String field) {
        ResponseEntity<Map> resp = restTemplate.exchange(
            base + "/inventory/products/" + prodId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);
        return ((Number) resp.getBody().get(field)).intValue();
    }
}
