package com.codigo2enter.almacenes.modules.sales.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de concurrencia para el mecanismo de reservas del módulo Sales.
 *
 * Estrategia: @SpringBootTest con WebEnvironment.RANDOM_PORT lanza el contexto
 * completo de Spring (incluyendo Hibernate y la conexión real a PostgreSQL).
 * Los tests envían requests HTTP desde múltiples threads simultáneos usando
 * CountDownLatch para sincronizar el inicio y garantizar solapamiento real.
 *
 * Por qué @SpringBootTest y no Mockito:
 *   Los tests de servicio con @Mock del repositorio nunca llegan a Hibernate.
 *   El @Version de Optimistic Locking solo se activa cuando Hibernate ejecuta
 *   el UPDATE real contra la BD y detecta que la versión cambió en disco.
 *   Sin Hibernate real, ObjectOptimisticLockingFailureException nunca se lanza
 *   y la protección de concurrencia queda sin verificar.
 *
 * Escenarios cubiertos:
 *   1. Stock insuficiente para dos aprobaciones simultáneas:
 *      ambas compiten por las mismas unidades → exactamente una gana.
 *   2. Stock suficiente para dos aprobaciones simultáneas:
 *      ambas pueden reservar → las dos deben ganar (sin falsos negativos).
 *   3. Reserva bajo contención alta (5 threads):
 *      solo las aprobaciones cuya suma cabe en el stock disponible tienen éxito.
 *
 * Criterio de corrección transversal:
 *   product.reservedStock al final NUNCA supera product.currentStock,
 *   independientemente de cuántas aprobaciones concurrentes lleguen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaleOrderConcurrencyTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String base;
    private HttpHeaders authHeaders;

    // IDs creados en @BeforeEach para cleanup en @AfterEach
    private final List<Long> orderIds   = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private Long categoryId;
    private Long supplierId;
    private Long clientId;

    // Sufijo único por ejecución para evitar colisiones de nombre con datos de dev
    private final String suffix = String.valueOf(System.currentTimeMillis());

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port + "/api/v1";
        authHeaders = buildAuthHeaders();
        categoryId = createCategory();
        supplierId = createSupplier();
        clientId   = createClient();
    }

    @AfterEach
    void tearDown() {
        // Cancelar órdenes activas antes de limpiar productos y clientes
        for (Long id : orderIds) {
            try {
                patch("/sales/orders/" + id + "/cancel");
            } catch (Exception ignored) {}
        }
        // Desactivar productos
        for (Long id : productIds) {
            try {
                delete("/inventory/products/" + id);
            } catch (Exception ignored) {}
        }
        // Desactivar cliente
        try { delete("/sales/clients/" + clientId); } catch (Exception ignored) {}
        // Desactivar categoría (puede fallar si tiene productos activos — ignorar)
        try { delete("/inventory/categories/" + categoryId); } catch (Exception ignored) {}
        // Desactivar proveedor
        try { delete("/purchases/suppliers/" + supplierId); } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: stock insuficiente — una aprobación gana, la otra es bloqueada
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escenario: currentStock=8, dos órdenes de 8 unidades cada una.
     * El stock solo alcanza para una. Las dos aprobaciones llegan simultáneamente.
     *
     * Resultado esperado:
     *   - Exactamente 1 aprobación con HTTP 200 (status APPROVED)
     *   - Exactamente 1 aprobación rechazada (HTTP 409 — colisión de @Version detectada
     *     por Hibernate, o HTTP 422 — stock disponible insuficiente)
     *   - product.reservedStock == 8 al final (nunca 16)
     *
     * La protección funciona por dos mecanismos complementarios:
     *   a) Si las transacciones se solapan en la fase de escritura:
     *      @Version detecta la colisión → ObjectOptimisticLockingFailureException → 409
     *   b) Si una transacción termina antes de que la otra lea el producto:
     *      la segunda ve reservedStock=8, available=0 < 8, falla en validación → 422
     *   (H1 — desde la migración a excepciones tipadas, ya no se devuelve 500 para
     *   estos casos de negocio.)
     */
    @Test
    void concurrentApprove_stockInsuficienteParaAmbos_soloUnoAprobado() throws Exception {
        Long productId = createProduct("CONC-A-" + suffix, 8);

        Long order1 = createOrder(productId, 8, 100.00);
        Long order2 = createOrder(productId, 8, 100.00);

        List<ApprovalResult> results = launchConcurrentApprovals(List.of(order1, order2));

        long approved = results.stream().filter(r -> r.status() == 200).count();
        long rejected = results.stream().filter(r -> r.status() == 409 || r.status() == 422).count();

        assertEquals(1, approved,
            "Exactamente 1 aprobación debe tener éxito cuando el stock solo alcanza para una. " +
            "Resultados: " + results);
        assertEquals(1, rejected,
            "Exactamente 1 aprobación debe ser rechazada con 409 (colisión @Version) " +
            "o 422 (stock insuficiente). Resultados: " + results);

        // Verificar que el body del rechazo contiene un mensaje de negocio claro.
        //
        // Dos mensajes son válidos según el momento en que se produce la colisión:
        //   a) "concurrentemente" — si @Version detectó la colisión en la fase de escritura
        //      (ObjectOptimisticLockingFailureException en el UPDATE de Hibernate) → 409
        //   b) "insuficiente"    — si la transacción ganadora ya commitó cuando la perdedora
        //      leyó el producto, por lo que la validación previa detectó available < qty → 422
        //
        // Ambos mensajes son correctos: indican que la reserva fue rechazada por razones
        // de integridad. Lo que este test garantiza es que NUNCA se acepta silenciosamente
        // (200 OK) una reserva que excede el stock disponible.
        String rejectedBody = results.stream()
                .filter(r -> r.status() == 409 || r.status() == 422)
                .map(ApprovalResult::body)
                .findFirst()
                .orElse("");

        boolean mensajeEsperado = rejectedBody.contains("concurrentemente")
                               || rejectedBody.contains("insuficiente");
        assertTrue(mensajeEsperado,
            "El body del rechazo debe contener 'concurrentemente' (colisión @Version) " +
            "o 'insuficiente' (validación detectó stock agotado). " +
            "Body recibido: " + rejectedBody);

        int reservedStock = getReservedStock(productId);
        assertEquals(8, reservedStock,
            "reservedStock debe ser 8 (no 16). Valor final: " + reservedStock);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: stock suficiente — ambas aprobaciones deben ganar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escenario: currentStock=20, dos órdenes de 8 unidades cada una.
     * El stock alcanza para ambas (8+8=16 <= 20).
     *
     * Resultado esperado:
     *   - Las 2 aprobaciones con HTTP 200 (ambas tienen cabida)
     *   - product.reservedStock == 16 al final
     *
     * Este test verifica que el mecanismo de Optimistic Locking no genera
     * falsos negativos cuando hay suficiente stock para todos los competidores.
     * Si Optimistic Locking fuera demasiado agresivo, rechazaría la segunda
     * aprobación aunque hubiera stock suficiente — esto sería incorrecto.
     *
     * Nota: este test puede necesitar reintentos en un sistema real porque
     * la colisión de versión puede ocurrir incluso con stock suficiente.
     * En producción se recomendaría un mecanismo de retry automático para
     * el caso en que la excepción es por contención (no por stock insuficiente).
     */
    @Test
    void concurrentApprove_stockSuficienteParaAmbos_ambosAprobados() throws Exception {
        Long productId = createProduct("CONC-B-" + suffix, 20);

        Long order1 = createOrder(productId, 8, 100.00);
        Long order2 = createOrder(productId, 8, 100.00);

        // Con stock suficiente, ambas deben poder aprobarse. En caso de colisión
        // de versión, reintentamos la que falló (comportamiento real de producción).
        List<ApprovalResult> results = launchConcurrentApprovals(List.of(order1, order2));

        long approved = results.stream().filter(r -> r.status() == 200).count();

        // Si hubo colisión de versión, la perdedora sigue en PENDING — reintentamos
        if (approved < 2) {
            for (Long id : List.of(order1, order2)) {
                ResponseEntity<String> resp = restTemplate.exchange(
                    base + "/sales/orders/" + id + "/approve",
                    HttpMethod.PATCH, new HttpEntity<>(authHeaders), String.class
                );
                if (resp.getStatusCode().value() == 200) approved++;
            }
        }

        assertEquals(2, approved,
            "Ambas aprobaciones deben tener éxito cuando el stock es suficiente. " +
            "Resultados iniciales: " + results);

        int reservedStock = getReservedStock(productId);
        assertEquals(16, reservedStock,
            "reservedStock debe ser 16 (8+8). Valor final: " + reservedStock);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: contención alta — 5 threads compiten por stock limitado
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escenario: currentStock=15, cinco órdenes de 5 unidades cada una.
     * Solo 3 pueden ser aprobadas (3×5=15). Las otras 2 deben ser rechazadas.
     *
     * Resultado esperado:
     *   - Entre 1 y 3 aprobaciones exitosas (al menos 1, a lo sumo 3)
     *   - product.reservedStock <= 15 al final (nunca supera el stock físico)
     *
     * El rango 1-3 (no exactamente 3) se debe a que con Optimistic Locking
     * algunos threads válidos pueden perder por colisión de versión aunque
     * hubiera stock para ellos. En un sistema productivo, estos se reintentarían.
     * Lo que esta prueba garantiza es el invariante crítico: nunca se reserva
     * más de lo que físicamente existe.
     */
    @Test
    void concurrentApprove_altoNivelDeContencion_reservedStockNuncaSuperaCurrentStock()
            throws Exception {
        Long productId = createProduct("CONC-C-" + suffix, 15);

        List<Long> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orders.add(createOrder(productId, 5, 100.00));
        }

        List<ApprovalResult> results = launchConcurrentApprovals(orders);

        long approved = results.stream().filter(r -> r.status() == 200).count();

        int reservedStock = getReservedStock(productId);
        int currentStock  = getCurrentStock(productId);

        // Invariante fundamental: reservedStock nunca supera currentStock
        assertTrue(reservedStock <= currentStock,
            "INVARIANTE VIOLADA: reservedStock (" + reservedStock +
            ") > currentStock (" + currentStock + "). " +
            "El sistema reservó más unidades de las que físicamente existen.");

        // Todos los rechazos deben incluir un mensaje de negocio reconocible,
        // con 409 (colisión @Version) o 422 (stock insuficiente) — nunca 500 (H1).
        results.stream()
               .filter(r -> r.status() == 409 || r.status() == 422)
               .forEach(r -> assertTrue(
                   r.body().contains("concurrentemente") || r.body().contains("insuficiente"),
                   "Cada rechazo debe explicar la causa. Body recibido: " + r.body()));

        // Al menos 1 debe haberse aprobado (el sistema no es tan conservador)
        assertTrue(approved >= 1,
            "Al menos 1 aprobación debe tener éxito. Resultados: " + results);

        // A lo sumo 3 (15 / 5 = 3)
        assertTrue(approved <= 3,
            "No pueden aprobarse más de 3 órdenes de 5 unidades con stock=15. " +
            "Aprobadas: " + approved + ". Resultados: " + results);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Infraestructura del test
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Par inmutable que agrupa el código HTTP y el body de una respuesta de aprobación.
     * Permite verificar tanto el status como el mensaje de error en los tests de concurrencia.
     */
    record ApprovalResult(int status, String body) {}

    /**
     * Lanza N aprobaciones simultáneas usando CountDownLatch para sincronizar
     * el inicio de todos los threads en el mismo instante.
     *
     * CountDownLatch startGun: todos los threads esperan en await() hasta que
     * el test principal llame countDown() — garantiza solapamiento real.
     *
     * CountDownLatch done: el test principal espera a que todos terminen.
     *
     * Retorna ApprovalResult (status + body) para permitir verificar tanto
     * el código HTTP como el mensaje específico del rechazo.
     */
    private List<ApprovalResult> launchConcurrentApprovals(List<Long> orderIds) throws Exception {
        int n = orderIds.size();
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(n);
        List<ApprovalResult> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors    = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(n);

        for (Long orderId : orderIds) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    ResponseEntity<String> resp = restTemplate.exchange(
                        base + "/sales/orders/" + orderId + "/approve",
                        HttpMethod.PATCH,
                        new HttpEntity<>(authHeaders),
                        String.class
                    );
                    results.add(new ApprovalResult(resp.getStatusCode().value(),
                                                   resp.getBody() != null ? resp.getBody() : ""));
                } catch (Exception e) {
                    errors.incrementAndGet();
                    results.add(new ApprovalResult(-1, e.getMessage()));
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown(); // dispara todos simultáneamente
        boolean completed = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(completed, "Timeout: no todos los threads terminaron en 30s");
        assertEquals(0, errors.get(), "Hubo errores inesperados en los threads");
        return results;
    }

    // ─── Helpers HTTP ─────────────────────────────────────────────────────────

    private HttpHeaders buildAuthHeaders() {
        Map<String, String> loginBody = Map.of(
            "username", "admin",
            "password", "Admin123!"
        );
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
            base + "/auth/login",
            new HttpEntity<>(loginBody, jsonHeaders()),
            Map.class
        );
        String token = (String) Objects.requireNonNull(loginResp.getBody()).get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private int patch(String path) {
        ResponseEntity<String> resp = restTemplate.exchange(
            base + path, HttpMethod.PATCH,
            new HttpEntity<>(authHeaders), String.class
        );
        return resp.getStatusCode().value();
    }

    private void delete(String path) {
        restTemplate.exchange(
            base + path, HttpMethod.DELETE,
            new HttpEntity<>(authHeaders), String.class
        );
    }

    @SuppressWarnings("unchecked")
    private Long createCategory() {
        Map<String, String> body = Map.of(
            "name", "Cat-Conc-" + suffix,
            "description", "Categoría para tests de concurrencia"
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/categories",
            new HttpEntity<>(body, authHeaders), Map.class
        );
        return ((Number) Objects.requireNonNull(resp.getBody()).get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long createSupplier() {
        Map<String, String> body = new HashMap<>();
        body.put("rfc",         "CONC" + suffix.substring(suffix.length() - 9));
        body.put("companyName", "Proveedor Concurrencia " + suffix);
        body.put("email",       "conc" + suffix + "@test.com");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/purchases/suppliers",
            new HttpEntity<>(body, authHeaders), Map.class
        );
        return ((Number) Objects.requireNonNull(resp.getBody()).get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long createClient() {
        Map<String, String> body = Map.of(
            "name",  "Cliente Concurrencia " + suffix,
            "email", "cliente.conc." + suffix + "@test.com"
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/sales/clients",
            new HttpEntity<>(body, authHeaders), Map.class
        );
        return ((Number) Objects.requireNonNull(resp.getBody()).get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long createProduct(String sku, int stock) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku",          sku);
        body.put("name",         "Producto " + sku);
        body.put("price",        500.00);
        body.put("currentStock", stock);
        body.put("minimumStock", 1);
        body.put("status",       "AVAILABLE");
        body.put("categoryId",   categoryId);
        body.put("supplierId",   supplierId);
        body.put("unitCost",     300.00);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/inventory/products",
            new HttpEntity<>(body, authHeaders), Map.class
        );
        Long id = ((Number) Objects.requireNonNull(resp.getBody()).get("id")).longValue();
        productIds.add(id);
        return id;
    }

    @SuppressWarnings("unchecked")
    private Long createOrder(Long productId, int quantity, double unitPrice) {
        Map<String, Object> detail = Map.of(
            "productId", productId,
            "quantity",  quantity,
            "unitPrice", unitPrice
        );
        Map<String, Object> body = Map.of(
            "clientId", clientId,
            "details",  List.of(detail)
        );
        ResponseEntity<Map> resp = restTemplate.postForEntity(
            base + "/sales/orders",
            new HttpEntity<>(body, authHeaders), Map.class
        );
        Long id = ((Number) Objects.requireNonNull(resp.getBody()).get("id")).longValue();
        orderIds.add(id);
        return id;
    }

    @SuppressWarnings("unchecked")
    private int getReservedStock(Long productId) {
        ResponseEntity<Map> resp = restTemplate.exchange(
            base + "/inventory/products/" + productId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class
        );
        return ((Number) Objects.requireNonNull(resp.getBody()).get("reservedStock")).intValue();
    }

    @SuppressWarnings("unchecked")
    private int getCurrentStock(Long productId) {
        ResponseEntity<Map> resp = restTemplate.exchange(
            base + "/inventory/products/" + productId,
            HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class
        );
        return ((Number) Objects.requireNonNull(resp.getBody()).get("currentStock")).intValue();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
