package com.codigo2enter.almacenes.modules.purchases.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.SupplierDTO;
import com.codigo2enter.almacenes.modules.purchases.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestión de proveedores del módulo de compras.
 *
 * Ruta base: /api/v1/purchases/suppliers
 * Todas las rutas están protegidas por JWT — requieren cabecera:
 *   Authorization: Bearer <token>
 *
 * Responsabilidad exclusiva: recibir la petición HTTP, activar las
 * validaciones Jakarta con @Valid y delegar al servicio.
 * Cero lógica de negocio en esta capa.
 */
@Tag(name = "Proveedores", description = "Gestión de proveedores")
@RestController
@RequestMapping("/api/v1/purchases/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    /**
     * Depende de la interfaz SupplierService — Spring inyecta SupplierServiceImpl
     * en tiempo de ejecución, desacoplando el controlador de la implementación concreta.
     */
    private final SupplierService supplierService;

    /**
     * POST /api/v1/purchases/suppliers
     *
     * Registra un nuevo proveedor en el catálogo.
     * @Valid activa las validaciones de SupplierDTO (@NotBlank en rfc y companyName).
     * El campo 'id' se ignora — PostgreSQL lo genera con IDENTITY.
     * El campo 'active' no se envía — el servicio lo inicializa en true.
     * El servicio valida unicidad de RFC y razón social antes de persistir.
     *
     * @param dto datos del nuevo proveedor enviados por el cliente
     * @return 201 Created con el SupplierDTO que incluye el id asignado
     */
    @Operation(summary = "Crear proveedor", description = "Registra un nuevo proveedor en el catálogo — RFC y razón social deben ser únicos")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Proveedor creado"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "409", description = "RFC o razón social ya existe") })
    @PostMapping
    public ResponseEntity<SupplierDTO> createSupplier(@Valid @RequestBody SupplierDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.createSupplier(dto));
    }

    /**
     * GET /api/v1/purchases/suppliers/active
     *
     * Retorna proveedores activos paginados, con búsqueda opcional por razón social o RFC.
     * La búsqueda es insensible a mayúsculas y acentos (f_unaccent en PostgreSQL).
     * Si search se omite o está vacío, retorna todos los proveedores activos.
     *
     * @param search texto a buscar en company_name o rfc (opcional)
     * @return 200 OK con la página de proveedores activos
     */
    @Operation(summary = "Listar proveedores activos", description = "Retorna proveedores activos paginados con búsqueda accent-insensitive por razón social o RFC")
    @ApiResponse(responseCode = "200", description = "Página de proveedores activos")
    @GetMapping("/active")
    public ResponseEntity<PageResponseDTO<SupplierDTO>> getAllActiveSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(supplierService.searchSuppliers(search, page, size));
    }

    /**
     * GET /api/v1/purchases/suppliers/{id}
     *
     * Retorna los datos completos de un proveedor por su ID.
     * Retorna proveedores activos e inactivos — un proveedor dado de baja
     * sigue siendo consultable para el historial de órdenes antiguas.
     *
     * @param id identificador del proveedor
     * @return 200 OK con el SupplierDTO correspondiente
     */
    @Operation(summary = "Obtener proveedor por ID", description = "Devuelve datos completos del proveedor — aplica también a inactivos para preservar historial")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Proveedor encontrado"),
                    @ApiResponse(responseCode = "404", description = "Proveedor no encontrado") })
    @GetMapping("/{id}")
    public ResponseEntity<SupplierDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.findById(id));
    }

    /**
     * PUT /api/v1/purchases/suppliers/{id}
     *
     * Actualiza los datos editables de un proveedor existente.
     * El servicio valida que el nuevo RFC y razón social no pertenezcan
     * a otro proveedor distinto al que se está editando.
     * Enviar active=true en el body reactiva un proveedor dado de baja.
     *
     * @param id  identificador del proveedor a modificar
     * @param dto datos nuevos enviados por el cliente
     * @return 200 OK con el SupplierDTO actualizado
     */
    @Operation(summary = "Actualizar proveedor", description = "Actualiza datos editables del proveedor; enviar active=true reactiva un proveedor dado de baja")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Proveedor actualizado"),
                    @ApiResponse(responseCode = "404", description = "Proveedor no encontrado"),
                    @ApiResponse(responseCode = "409", description = "RFC o razón social ya existe en otro proveedor") })
    @PutMapping("/{id}")
    public ResponseEntity<SupplierDTO> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierDTO dto) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, dto));
    }

    /**
     * DELETE /api/v1/purchases/suppliers/{id}
     *
     * Desactiva lógicamente un proveedor (soft delete: active = false).
     * No elimina el registro — preserva el historial de órdenes de compra.
     * El servicio bloquea la desactivación si el proveedor tiene órdenes
     * en estado PENDING o APPROVED — deben cancelarse o recibirse primero.
     * El proveedor puede reactivarse con PUT /{id} enviando active=true.
     *
     * @param id identificador del proveedor a desactivar
     * @return 204 No Content — operación exitosa sin cuerpo de respuesta
     */
    @Operation(summary = "Desactivar proveedor", description = "Soft delete — bloqueado si tiene órdenes en PENDING o APPROVED; reactivable con PUT /{id}")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Proveedor desactivado"),
                    @ApiResponse(responseCode = "404", description = "Proveedor no encontrado"),
                    @ApiResponse(responseCode = "422", description = "Tiene órdenes activas en PENDING o APPROVED") })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateSupplier(@PathVariable Long id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
