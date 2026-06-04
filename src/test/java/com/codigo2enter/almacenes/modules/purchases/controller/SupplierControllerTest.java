package com.codigo2enter.almacenes.modules.purchases.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.purchases.dto.SupplierDTO;
import com.codigo2enter.almacenes.modules.purchases.service.SupplierService;
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
 * Pruebas de integración de la capa web para SupplierController.
 *
 * Verifica: enrutamiento HTTP correcto, presencia de @Valid en los parámetros
 * del controlador, códigos HTTP semánticamente correctos y serialización JSON.
 * La lógica de negocio (unicidad de RFC, bloqueo de baja) ya está cubierta
 * en SupplierServiceImplTest — no se duplica aquí.
 *
 * @MockBean JwtUtils es requerido porque @WebMvcTest carga SecurityConfig,
 * que necesita JwtAuthenticationFilter, que depende de JwtUtils.
 */
@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupplierService supplierService;

    @MockBean
    private JwtUtils jwtUtils;

    private SupplierDTO response;

    @BeforeEach
    void setUp() {
        response = SupplierDTO.builder()
                .id(1L)
                .rfc("ABC123456789A")
                .companyName("Ferretería SA")
                .email("compras@ferreteria.com")
                .active(true)
                .build();
    }

    // =========================================================================
    // POST /api/v1/purchases/suppliers
    // =========================================================================

    /**
     * Happy Path: body válido → 201 Created con los datos del proveedor creado.
     * Verifica que POST retorna 201 y no 200, y que id, rfc y active
     * se serializan correctamente en el JSON de respuesta.
     */
    @Test
    @DisplayName("POST /suppliers: debe retornar 201 Created con el proveedor creado")
    void shouldCreateSupplierAndReturn201() throws Exception {
        // ARRANGE
        when(supplierService.createSupplier(any(SupplierDTO.class))).thenReturn(response);

        String body = """
                {
                    "rfc":         "ABC123456789A",
                    "companyName": "Ferretería SA",
                    "email":       "compras@ferreteria.com"
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(post("/api/v1/purchases/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.rfc").value("ABC123456789A"))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Validación: rfc="" falla @NotBlank → 400 Bad Request.
     * Verifica que @Valid está en el @RequestBody del controlador.
     * Sin @Valid, el servicio recibiría un RFC vacío y el error sería diferente.
     */
    @Test
    @DisplayName("POST /suppliers: debe retornar 400 cuando el RFC está en blanco")
    void shouldReturn400WhenRfcIsBlank() throws Exception {
        String invalidBody = """
                {
                    "rfc":         "",
                    "companyName": "Ferretería SA"
                }
                """;

        mockMvc.perform(post("/api/v1/purchases/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * Validación: companyName="" falla @NotBlank → 400 Bad Request.
     * El segundo campo obligatorio del DTO también debe ser rechazado antes
     * de llegar al servicio.
     */
    @Test
    @DisplayName("POST /suppliers: debe retornar 400 cuando la razón social está en blanco")
    void shouldReturn400WhenCompanyNameIsBlank() throws Exception {
        String invalidBody = """
                {
                    "rfc":         "ABC123456789A",
                    "companyName": ""
                }
                """;

        mockMvc.perform(post("/api/v1/purchases/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/purchases/suppliers/active
    // =========================================================================

    /**
     * Happy Path: retorna lista de proveedores activos.
     * Verifica que la ruta /active no colisiona con /{id} y que la lista
     * se serializa correctamente como JSON array.
     */
    @Test
    @DisplayName("GET /suppliers/active: debe retornar 200 con la página de proveedores activos")
    void shouldReturnActiveSuppliers() throws Exception {
        // ARRANGE
        SupplierDTO response2 = SupplierDTO.builder()
                .id(2L).rfc("XYZ987654321B").companyName("Distribuidora Norte").active(true).build();
        // El endpoint ahora retorna PageResponseDTO — mockear la versión paginada.
        PageResponseDTO<SupplierDTO> page = PageResponseDTO.<SupplierDTO>builder()
                .content(List.of(response, response2))
                .currentPage(0).totalPages(1).totalElements(2).size(20)
                .first(true).last(true).build();
        when(supplierService.getAllActiveSuppliers(0, 20)).thenReturn(page);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/suppliers/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].companyName").value("Ferretería SA"));
    }

    // =========================================================================
    // GET /api/v1/purchases/suppliers/{id}
    // =========================================================================

    /**
     * Happy Path: retorna un proveedor por ID.
     * Verifica que @PathVariable Long id extrae correctamente el id del path.
     */
    @Test
    @DisplayName("GET /suppliers/{id}: debe retornar 200 con el proveedor encontrado")
    void shouldReturnSupplierById() throws Exception {
        // ARRANGE
        when(supplierService.findById(1L)).thenReturn(response);

        // ACT + ASSERT
        mockMvc.perform(get("/api/v1/purchases/suppliers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rfc").value("ABC123456789A"));
    }

    // =========================================================================
    // PUT /api/v1/purchases/suppliers/{id}
    // =========================================================================

    /**
     * Happy Path: actualiza un proveedor existente.
     * Verifica que PUT retorna 200 (no 201 ni 204) y que el cuerpo
     * contiene los datos actualizados.
     */
    @Test
    @DisplayName("PUT /suppliers/{id}: debe retornar 200 con el proveedor actualizado")
    void shouldUpdateSupplierAndReturn200() throws Exception {
        // ARRANGE
        when(supplierService.updateSupplier(eq(1L), any(SupplierDTO.class))).thenReturn(response);

        String body = """
                {
                    "rfc":         "ABC123456789A",
                    "companyName": "Ferretería SA",
                    "phone":       "5559876543",
                    "active":      true
                }
                """;

        // ACT + ASSERT
        mockMvc.perform(put("/api/v1/purchases/suppliers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // =========================================================================
    // DELETE /api/v1/purchases/suppliers/{id}
    // =========================================================================

    /**
     * Happy Path: desactiva un proveedor.
     * Verifica que DELETE retorna 204 No Content (no 200) y que no hay
     * cuerpo de respuesta. deactivateSupplier es void — 204 es correcto.
     */
    @Test
    @DisplayName("DELETE /suppliers/{id}: debe retornar 204 No Content")
    void shouldDeactivateSupplierAndReturn204() throws Exception {
        mockMvc.perform(delete("/api/v1/purchases/suppliers/1"))
                .andExpect(status().isNoContent());
    }
}
