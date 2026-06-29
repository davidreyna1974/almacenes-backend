package com.codigo2enter.almacenes.modules.inventory.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.service.CategoryService;
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
 * Controlador REST para la gestión de categorías del inventario.
 *
 * Ruta base: /api/v1/inventory/categories
 * Todas las rutas están protegidas por JWT — requieren cabecera:
 *   Authorization: Bearer <token>
 *
 * Responsabilidad exclusiva: recibir la petición HTTP, activar las
 * validaciones Jakarta con @Valid y delegar al servicio. Cero lógica
 * de negocio en esta capa.
 */
@Tag(name = "Categorías", description = "Gestión de categorías de productos")
@RestController
@RequestMapping("/api/v1/inventory/categories")
@RequiredArgsConstructor
public class CategoryController {

    /**
     * Depende de la interfaz CategoryService, nunca de la implementación
     * concreta. Spring inyecta CategoryServiceImpl en tiempo de ejecución,
     * lo que facilita el reemplazo y las pruebas con mocks.
     */
    private final CategoryService categoryService;

    /**
     * POST /api/v1/inventory/categories
     *
     * Crea una nueva categoría en el sistema.
     * @Valid activa las validaciones del DTO antes de invocar al servicio:
     *   - @NotBlank en name → rechaza null, vacío y solo espacios
     *   - @Size(max=80) en name y @Size(max=255) en description
     * Si alguna validación falla, Spring retorna HTTP 400 automáticamente.
     *
     * @param dto datos de la nueva categoría enviados por el cliente
     * @return 201 Created con el CategoryDTO que incluye el id asignado
     */
    @Operation(summary = "Crear categoría", description = "Crea una nueva categoría de productos")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Categoría creada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "409", description = "Nombre ya existe") })
    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@Valid @RequestBody CategoryDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(dto));
    }

    /**
     * GET /api/v1/inventory/categories/active
     *
     * Retorna categorías activas paginadas, con búsqueda opcional por nombre.
     * La búsqueda es insensible a mayúsculas y acentos (f_unaccent en PostgreSQL).
     * Si search se omite o está vacío, retorna todas las categorías activas.
     *
     * @param search texto a buscar en nombre (opcional)
     * @return 200 OK con la página de categorías activas
     */
    @Operation(summary = "Listar categorías activas", description = "Retorna categorías activas paginadas con búsqueda accent-insensitive")
    @ApiResponse(responseCode = "200", description = "Lista de categorías")
    @GetMapping("/active")
    public ResponseEntity<PageResponseDTO<CategoryDTO>> getAllActiveCategories(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(categoryService.searchCategories(search, page, size));
    }

    /**
     * PUT /api/v1/inventory/categories/{id}
     *
     * Actualiza los datos de una categoría existente.
     * El servicio valida que el id exista y que el nuevo nombre no
     * pertenezca a otra categoría diferente antes de aplicar los cambios.
     *
     * @param id  identificador de la categoría a modificar
     * @param dto datos nuevos enviados por el cliente
     * @return 200 OK con el CategoryDTO actualizado
     */
    @Operation(summary = "Actualizar categoría", description = "Actualiza nombre y descripción de una categoría existente")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
                    @ApiResponse(responseCode = "404", description = "Categoría no encontrada"),
                    @ApiResponse(responseCode = "409", description = "Nombre ya existe en otra categoría") })
    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryDTO dto) {
        return ResponseEntity.ok(categoryService.updateCategory(id, dto));
    }

    /**
     * DELETE /api/v1/inventory/categories/{id}
     *
     * Desactiva lógicamente una categoría (soft delete: active = false).
     * No elimina el registro — preserva la integridad referencial con
     * los productos asociados. El servicio valida que no existan productos
     * activos asignados antes de permitir la desactivación.
     *
     * Se retorna 204 No Content porque la operación no produce un cuerpo
     * de respuesta — solo confirma que fue exitosa.
     *
     * @param id identificador de la categoría a desactivar
     * @return 204 No Content
     */
    @Operation(summary = "Desactivar categoría", description = "Desactivación lógica (soft delete) — solo si no tiene productos activos")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Desactivada correctamente"),
                    @ApiResponse(responseCode = "404", description = "Categoría no encontrada"),
                    @ApiResponse(responseCode = "422", description = "Tiene productos activos asociados") })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }
}
