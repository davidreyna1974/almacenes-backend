package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.sales.dto.ClientDTO;
import com.codigo2enter.almacenes.modules.sales.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Clientes", description = "Gestión de clientes")
@RestController
@RequestMapping("/api/v1/sales/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @Operation(summary = "Crear cliente", description = "Registra un nuevo cliente en el catálogo — RFC y nombre deben ser únicos")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Cliente creado"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "409", description = "RFC o nombre ya existe") })
    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody ClientDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.createClient(dto));
    }

    /**
     * GET /api/v1/sales/clients/active
     *
     * Retorna clientes activos paginados, con búsqueda opcional por nombre, RFC o contacto.
     * La búsqueda es insensible a mayúsculas y acentos (f_unaccent en PostgreSQL).
     * Si search se omite o está vacío, retorna todos los clientes activos.
     *
     * @param search texto a buscar en name, rfc o contact_name (opcional)
     * @return 200 OK con la página de clientes activos
     */
    @Operation(summary = "Listar clientes activos", description = "Retorna clientes activos paginados con búsqueda accent-insensitive por nombre, RFC o contacto")
    @ApiResponse(responseCode = "200", description = "Página de clientes activos")
    @GetMapping("/active")
    public ResponseEntity<PageResponseDTO<ClientDTO>> getAllActiveClients(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clientService.searchClients(search, page, size));
    }

    @Operation(summary = "Obtener cliente por ID", description = "Devuelve datos completos del cliente — aplica también a inactivos para preservar historial")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
                    @ApiResponse(responseCode = "404", description = "Cliente no encontrado") })
    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    @Operation(summary = "Actualizar cliente", description = "Actualiza datos editables del cliente; enviar active=true reactiva un cliente dado de baja")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Cliente actualizado"),
                    @ApiResponse(responseCode = "404", description = "Cliente no encontrado"),
                    @ApiResponse(responseCode = "409", description = "RFC o nombre ya existe en otro cliente") })
    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable Long id,
                                                   @Valid @RequestBody ClientDTO dto) {
        return ResponseEntity.ok(clientService.updateClient(id, dto));
    }

    @Operation(summary = "Desactivar cliente", description = "Soft delete — bloqueado si tiene órdenes activas en PENDING o APPROVED")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Cliente desactivado"),
                    @ApiResponse(responseCode = "404", description = "Cliente no encontrado"),
                    @ApiResponse(responseCode = "422", description = "Tiene órdenes activas en PENDING o APPROVED") })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateClient(@PathVariable Long id) {
        clientService.deactivateClient(id);
        return ResponseEntity.noContent().build();
    }
}
