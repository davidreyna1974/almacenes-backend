package com.codigo2enter.almacenes.modules.purchases.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de la capa web para PurchaseOrderController.
 *
 * Cubre los 13 endpoints verificando: enrutamiento HTTP, presencia de @Valid,
 * códigos HTTP semánticamente correctos y serialización JSON básica.
 *
 * La lógica de negocio (máquina de estados, integración con inventory,
 * validaciones de servicio) ya está cubierta en PurchaseOrderServiceImplTest
 * con 29 tests — no se duplica aquí.
 *
 * Consideración especial sobre PATCH:
 * Los tres endpoints de transición de estado (approve, receive, cancel) no
 * reciben body — se verifica que Spring no los exige al enviar PATCH sin
 * Content-Type ni body.
 */
@WebMvcTest(PurchaseOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PurchaseOrderService purchaseOrderService;

    @MockBean
    private JwtUtils jwtUtils;

    private PurchaseOrderResponseDTO response;

    @BeforeEach
    void setUp() {
        response = PurchaseOrderResponseDTO.builder()
                .id(1L)
                .orderNumber("OC-2026-0001")
                .status("PENDING")
                .totalAmount(new BigDecimal("899.90"))
                .supplierId(1L)
                .supplierName("Ferretería SA")
                .createdByUsername("operador01")
                .build();
    }

    // =========================================================================
    // POST /api/v1/purchases/orders
    // =========================================================================

    /**
     * Happy Path: body válido → 201 Created con los datos de la orden.
     * Verifica que orderNumber, status y totalAmount se serializan correctamente.
     */
    @Test
    @DisplayName("POST /orders: debe retornar 201 Created con la orden creada")
    void shouldCreateOrderAndReturn201() throws Exception {
        // ARRANGE
        when(purchaseOrderService.createOrder(any(PurchaseOrderRequestDTO.class)))
                .thenReturn(response);

        String body = """
                {
                    "supplierId": 1,
                    "notes": "Pedido Q2",
                    "details": [
                        { "productId": 5, "quantity": 10, "unitPrice": 89.99 }
                    ]
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/purchases/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("OC-2026-0001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(899.90));
    }

    /**
     * Validación: details=[] falla @NotEmpty → 400 Bad Request.
     * Verifica que @Valid propaga la validación a la lista de detalles.
     * Sin @Valid en el controlador, la orden se crearía sin líneas de productos.
     */
    @Test
    @DisplayName("POST /orders: debe retornar 400 cuando la lista de detalles está vacía")
    void shouldReturn400WhenDetailsListIsEmpty() throws Exception {
        String invalidBody = """
                {
                    "supplierId": 1,
                    "details": []
                }
                """;

        mockMvc.perform(post("/api/v1/purchases/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * Validación: supplierId=null falla @NotNull → 400 Bad Request.
     * El campo más crítico del request — sin proveedor no puede existir la orden.
     */
    @Test
    @DisplayName("POST /orders: debe retornar 400 cuando supplierId es nulo")
    void shouldReturn400WhenSupplierIdIsNull() throws Exception {
        String invalidBody = """
                {
                    "notes": "Sin proveedor",
                    "details": [
                        { "productId": 5, "quantity": 10, "unitPrice": 89.99 }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/purchases/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/purchases/orders/{id}
    // =========================================================================

    /**
     * Happy Path: retorna la orden completa por ID.
     * Verifica enrutamiento de /{id} y serialización de campos clave.
     */
    @Test
    @DisplayName("GET /orders/{id}: debe retornar 200 con la orden encontrada")
    void shouldReturnOrderById() throws Exception {
        // ARRANGE
        when(purchaseOrderService.findById(1L)).thenReturn(response);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("OC-2026-0001"))
                .andExpect(jsonPath("$.supplierName").value("Ferretería SA"));
    }

    // =========================================================================
    // GET /api/v1/purchases/orders/status/{status}
    // =========================================================================

    /**
     * Happy Path: retorna lista de órdenes por estado.
     * Verifica que el status como PathVariable String llega al servicio
     * y que la lista se serializa correctamente.
     */
    @Test
    @DisplayName("GET /orders/status/{status}: debe retornar 200 con las órdenes en ese estado")
    void shouldReturnOrdersByStatus() throws Exception {
        // ARRANGE
        when(purchaseOrderService.findByStatus("PENDING")).thenReturn(List.of(response));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/orders/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // =========================================================================
    // GET /api/v1/purchases/orders/supplier/{supplierId}
    // =========================================================================

    /**
     * Happy Path: retorna todas las órdenes de un proveedor.
     * Verifica que @PathVariable Long supplierId extrae el id correctamente.
     */
    @Test
    @DisplayName("GET /orders/supplier/{supplierId}: debe retornar 200 con las órdenes del proveedor")
    void shouldReturnOrdersBySupplierId() throws Exception {
        // ARRANGE
        when(purchaseOrderService.findBySupplierId(1L)).thenReturn(List.of(response));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/orders/supplier/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // =========================================================================
    // GET /api/v1/purchases/orders/supplier/{supplierId}/status/{status}
    // =========================================================================

    /**
     * Happy Path: filtro combinado proveedor + estado.
     * Es el test más crítico de enrutamiento: verifica que la ruta con DOS
     * PathVariables (/supplier/{supplierId}/status/{status}) no colisiona con
     * /supplier/{supplierId} y que Spring extrae correctamente ambos valores.
     * Un error en @GetMapping o en los @PathVariable haría invisible este endpoint.
     */
    @Test
    @DisplayName("GET /orders/supplier/{supplierId}/status/{status}: debe retornar 200 con filtro combinado")
    void shouldReturnOrdersBySupplierIdAndStatus() throws Exception {
        // ARRANGE
        when(purchaseOrderService.findBySupplierIdAndStatus(1L, "APPROVED"))
                .thenReturn(List.of(response));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/orders/supplier/1/status/APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // =========================================================================
    // GET /api/v1/purchases/orders/product/{productId}
    // =========================================================================

    /**
     * Happy Path: retorna órdenes que contienen un producto específico.
     * Verifica que /product/{productId} no colisiona con /{id}/details/{detailId}.
     */
    @Test
    @DisplayName("GET /orders/product/{productId}: debe retornar 200 con las órdenes que contienen el producto")
    void shouldReturnOrdersByProduct() throws Exception {
        // ARRANGE
        when(purchaseOrderService.findOrdersByProduct(5L)).thenReturn(List.of(response));

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/orders/product/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // =========================================================================
    // PUT /api/v1/purchases/orders/{id}
    // =========================================================================

    /**
     * Happy Path: actualiza notas y proveedor de una orden.
     * Verifica que PUT retorna 200 con el cuerpo de la orden actualizada.
     */
    @Test
    @DisplayName("PUT /orders/{id}: debe retornar 200 con la orden actualizada")
    void shouldUpdateOrderAndReturn200() throws Exception {
        // ARRANGE
        when(purchaseOrderService.updateOrder(eq(1L), any(PurchaseOrderUpdateRequestDTO.class)))
                .thenReturn(response);

        String body = """
                {
                    "supplierId": 1,
                    "notes": "Notas actualizadas"
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(put("/api/v1/purchases/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    /**
     * Validación: supplierId=null en PurchaseOrderUpdateRequestDTO falla @NotNull.
     * Verifica que @Valid está activo en el PUT de la orden.
     */
    @Test
    @DisplayName("PUT /orders/{id}: debe retornar 400 cuando supplierId es nulo")
    void shouldReturn400WhenUpdateSupplierIdIsNull() throws Exception {
        String invalidBody = """
                {
                    "notes": "Sin proveedor"
                }
                """;

        mockMvc.perform(put("/api/v1/purchases/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PATCH /api/v1/purchases/orders/{id}/approve
    // =========================================================================

    /**
     * Happy Path: aprueba una orden enviando PATCH sin body.
     * Verifica que PATCH (no PUT) es el método correcto y que la ruta
     * /approve está mapeada. No recibe body — Spring no debe exigirlo.
     */
    @Test
    @DisplayName("PATCH /orders/{id}/approve: debe retornar 200 con la orden aprobada")
    void shouldApproveOrderAndReturn200() throws Exception {
        // ARRANGE
        when(purchaseOrderService.approveOrder(1L)).thenReturn(response);

        // ACT + ASSERT — PATCH sin body ni Content-Type
        mockMvc.perform(patch("/api/v1/purchases/orders/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("OC-2026-0001"));
    }

    // =========================================================================
    // PATCH /api/v1/purchases/orders/{id}/receive
    // =========================================================================

    /**
     * Happy Path: recibe una orden enviando PATCH sin body.
     * Verifica que /receive no colisiona con /approve ni /cancel.
     * En producción dispara los movimientos de stock — verificado en el
     * test de servicio TEST 13 (receiveOrder con argThat).
     */
    @Test
    @DisplayName("PATCH /orders/{id}/receive: debe retornar 200 con la orden recibida")
    void shouldReceiveOrderAndReturn200() throws Exception {
        // ARRANGE
        when(purchaseOrderService.receiveOrder(1L)).thenReturn(response);

        // ACT + ASSERT
        mockMvc.perform(patch("/api/v1/purchases/orders/1/receive"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // PATCH /api/v1/purchases/orders/{id}/cancel
    // =========================================================================

    /**
     * Happy Path: cancela una orden enviando PATCH sin body.
     * Verifica que /cancel está mapeado y no interfiere con las otras rutas PATCH.
     */
    @Test
    @DisplayName("PATCH /orders/{id}/cancel: debe retornar 200 con la orden cancelada")
    void shouldCancelOrderAndReturn200() throws Exception {
        // ARRANGE
        when(purchaseOrderService.cancelOrder(1L)).thenReturn(response);

        // ACT + ASSERT
        mockMvc.perform(patch("/api/v1/purchases/orders/1/cancel"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // POST /api/v1/purchases/orders/{id}/details
    // =========================================================================

    /**
     * Happy Path: agrega un detalle a una orden → 201 Created.
     * Verifica que POST al subrecurso /{id}/details retorna 201 (no 200)
     * y que la ruta no colisiona con POST /.
     */
    @Test
    @DisplayName("POST /orders/{id}/details: debe retornar 201 Created con la orden actualizada")
    void shouldAddDetailAndReturn201() throws Exception {
        // ARRANGE
        when(purchaseOrderService.addDetail(eq(1L), any(PurchaseOrderDetailRequestDTO.class)))
                .thenReturn(response);

        String body = """
                {
                    "productId": 8,
                    "quantity":  5,
                    "unitPrice": 49.99
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/purchases/orders/1/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /**
     * Validación: quantity=0 falla @Min(1) en PurchaseOrderDetailRequestDTO.
     * Verifica que @Valid está activo en el POST de detalles.
     * Sin @Valid, quantity=0 llegaría al servicio y generaría RuntimeException
     * en lugar del 400 más informativo de Jakarta.
     */
    @Test
    @DisplayName("POST /orders/{id}/details: debe retornar 400 cuando la cantidad es cero")
    void shouldReturn400WhenDetailQuantityIsZero() throws Exception {
        String invalidBody = """
                {
                    "productId": 8,
                    "quantity":  0,
                    "unitPrice": 49.99
                }
                """;

        mockMvc.perform(post("/api/v1/purchases/orders/1/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PUT /api/v1/purchases/orders/{id}/details/{detailId}
    // =========================================================================

    /**
     * Happy Path: actualiza quantity y unitPrice de un detalle.
     * Verifica que PUT sobre /{id}/details/{detailId} extrae correctamente
     * AMBOS PathVariables (orderId y detailId) y retorna 200.
     */
    @Test
    @DisplayName("PUT /orders/{id}/details/{detailId}: debe retornar 200 con el detalle actualizado")
    void shouldUpdateDetailAndReturn200() throws Exception {
        // ARRANGE
        when(purchaseOrderService.updateDetail(
                eq(1L), eq(1L), any(PurchaseOrderDetailUpdateRequestDTO.class)))
                .thenReturn(response);

        String body = """
                {
                    "quantity":  15,
                    "unitPrice": 75.00
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(put("/api/v1/purchases/orders/1/details/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    /**
     * Validación: unitPrice=0.00 falla @DecimalMin("0.01") → 400 Bad Request.
     * Verifica que @Valid está activo en el PUT de detalles.
     * Un precio de cero no tiene sentido en una orden de compra.
     */
    @Test
    @DisplayName("PUT /orders/{id}/details/{detailId}: debe retornar 400 cuando el precio unitario es cero")
    void shouldReturn400WhenDetailUnitPriceIsInvalid() throws Exception {
        String invalidBody = """
                {
                    "quantity":  15,
                    "unitPrice": 0.00
                }
                """;

        mockMvc.perform(put("/api/v1/purchases/orders/1/details/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // DELETE /api/v1/purchases/orders/{id}/details/{detailId}
    // =========================================================================

    /**
     * Happy Path: elimina un detalle → 204 No Content.
     * Verifica que DELETE retorna 204 (no 200) y que ambos PathVariables
     * (orderId y detailId) se extraen correctamente del path.
     * removeDetail es void — 204 es el código semánticamente correcto.
     */
    @Test
    @DisplayName("DELETE /orders/{id}/details/{detailId}: debe retornar 204 No Content")
    void shouldRemoveDetailAndReturn204() throws Exception {
        mockMvc.perform(delete("/api/v1/purchases/orders/1/details/1"))
                .andExpect(status().isNoContent());
    }
}
