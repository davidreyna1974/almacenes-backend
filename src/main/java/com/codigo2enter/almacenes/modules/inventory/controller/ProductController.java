package com.codigo2enter.almacenes.modules.inventory.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
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
 * Controlador REST para la gestión de productos e inventario.
 *
 * Ruta base: /api/v1/inventory/products
 * Todas las rutas están protegidas por JWT — requieren cabecera:
 *   Authorization: Bearer <token>
 *
 * Expone 8 endpoints que cubren el ciclo de vida completo del producto:
 * creación, consulta, actualización, desactivación y gestión de stock.
 */
@Tag(name = "Productos", description = "Gestión de productos e inventario")
@RestController
@RequestMapping("/api/v1/inventory/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /api/v1/inventory/products
     *
     * Búsqueda paginada de productos activos con filtros opcionales combinables.
     * Este es el endpoint principal del catálogo de productos del frontend.
     *
     * Parámetros opcionales — si se omiten, se retornan todos los productos activos:
     *   search     → búsqueda parcial (case-insensitive) sobre sku y nombre
     *   categoryId → filtra por categoría exacta
     *   status     → filtra por estado (AVAILABLE, DISCONTINUED, OUT_OF_STOCK)
     *   supplierId → filtra por proveedor exacto
     *   page / size → paginación (defaults: 0 / 20)
     *
     * @return 200 OK con PageResponseDTO de productos que cumplen los criterios
     */
    @Operation(summary = "Buscar productos", description = "Búsqueda paginada con filtros opcionales combinables (search, categoryId, status, supplierId)")
    @ApiResponse(responseCode = "200", description = "Página de productos")
    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductResponseDTO>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                productService.searchProducts(search, categoryId, status, supplierId, page, size));
    }

    /**
     * POST /api/v1/inventory/products
     *
     * Registra un nuevo producto en el inventario.
     * El servicio valida unicidad del SKU y resuelve la entidad Category
     * desde el categoryId del DTO antes de persistir.
     *
     * @param dto datos del nuevo producto con SKU, precio, stock y categoría
     * @return 201 Created con el ProductResponseDTO que incluye id y categoryName
     */
    @Operation(summary = "Crear producto", description = "Registra un nuevo producto en el inventario")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Producto creado"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "409", description = "SKU ya existe") })
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(dto));
    }

    /**
     * PUT /api/v1/inventory/products/{id}
     *
     * Actualiza los datos de un producto existente.
     * El servicio valida que el id exista, que el nuevo SKU no pertenezca
     * a otro producto diferente y que el categoryId sea válido.
     *
     * @param id  identificador del producto a modificar
     * @param dto datos nuevos del producto
     * @return 200 OK con el ProductResponseDTO actualizado
     */
    @Operation(summary = "Actualizar producto", description = "Actualiza datos del producto (no modifica stock directamente)")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Producto actualizado"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado"),
                    @ApiResponse(responseCode = "409", description = "SKU ya existe en otro producto") })
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequestDTO dto) {
        return ResponseEntity.ok(productService.updateProduct(id, dto));
    }

    /**
     * DELETE /api/v1/inventory/products/{id}
     *
     * Desactiva lógicamente un producto (soft delete: active = false).
     * No elimina el registro — preserva el historial de movimientos de
     * stock y la integridad referencial con otras entidades.
     *
     * @param id identificador del producto a desactivar
     * @return 204 No Content
     */
    @Operation(summary = "Desactivar producto", description = "Soft delete — preserva historial de movimientos")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Desactivado"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/inventory/products/{id}
     *
     * Consulta un producto por su ID. Usado principalmente por el módulo de
     * concurrencia y por el frontend cuando necesita el estado actual de stock
     * (currentStock, reservedStock, availableStock) de un producto concreto.
     *
     * @param id identificador del producto
     * @return 200 OK con el ProductResponseDTO
     */
    @Operation(summary = "Obtener producto por ID", description = "Devuelve el estado actual de stock (currentStock, reservedStock, availableStock)")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Producto encontrado"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    /**
     * GET /api/v1/inventory/products/sku/{sku}
     *
     * Busca un producto por su código SKU.
     * Útil para el frontend cuando el operador escanea un código de barras
     * o busca un artículo por su referencia única.
     *
     * @param sku código único del producto
     * @return 200 OK con el ProductResponseDTO correspondiente
     */
    @Operation(summary = "Buscar producto por SKU", description = "Búsqueda por código único de producto (útil para lectores de código de barras)")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Producto encontrado"),
                    @ApiResponse(responseCode = "404", description = "SKU no existe") })
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponseDTO> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getBySku(sku));
    }

    /**
     * GET /api/v1/inventory/products/category/{categoryId}
     *
     * Retorna todos los productos activos que pertenecen a una categoría.
     * El servicio valida que la categoría exista antes de ejecutar la consulta,
     * evitando que un ID inválido devuelva una lista vacía sin aviso.
     *
     * @param categoryId identificador de la categoría por la que filtrar
     * @return 200 OK con la lista de productos activos de esa categoría
     */
    @Operation(summary = "Productos por categoría", description = "Lista paginada de productos activos de una categoría específica")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Lista de productos"),
                    @ApiResponse(responseCode = "404", description = "Categoría no encontrada") })
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<PageResponseDTO<ProductResponseDTO>> getByCategoryId(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.getByCategoryId(categoryId, page, size));
    }

    /**
     * GET /api/v1/inventory/products/low-stock
     *
     * Retorna los productos cuyo stock actual es menor o igual al mínimo
     * configurado (currentStock <= minimumStock). Alimenta el panel de
     * alertas del frontend para indicar productos que necesitan reposición.
     *
     * @return 200 OK con la lista de productos en nivel crítico de stock
     */
    @Operation(summary = "Productos con stock bajo", description = "Productos con currentStock ≤ minimumStock — panel de alertas de reposición")
    @ApiResponse(responseCode = "200", description = "Lista de productos en nivel crítico")
    @GetMapping("/low-stock")
    public ResponseEntity<PageResponseDTO<ProductResponseDTO>> getLowStockProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.getLowStockProducts(page, size));
    }

    /**
     * POST /api/v1/inventory/products/movement
     *
     * Registra un movimiento de stock (entrada IN o salida OUT) sobre un producto.
     * Actualiza currentStock y genera un registro inmutable en stock_movements.
     *
     * El servicio aplica las siguientes validaciones antes de procesar:
     *   - El campo type debe ser exactamente "IN" o "OUT"
     *   - La quantity debe ser mayor a cero
     *   - Para OUT: el stock resultante no puede ser negativo
     *
     * Se retorna 204 No Content porque registerStockMovement es void —
     * la operación no produce un recurso nuevo que devolver al cliente.
     *
     * @param request DTO con productId, quantity, type y reason
     * @return 204 No Content
     */
    @Operation(summary = "Registrar movimiento de stock", description = "Entrada (IN) o salida (OUT) de unidades — actualiza currentStock y genera registro en Kardex")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Movimiento registrado"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos o stock insuficiente para OUT"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @PostMapping("/movement")
    public ResponseEntity<Void> registerStockMovement(
            @Valid @RequestBody StockMovementRequestDTO request) {
        productService.registerStockMovement(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/inventory/products/{id}/movements
     *
     * Retorna el historial completo de movimientos de stock de un producto,
     * ordenado del más reciente al más antiguo (Kardex). El servicio valida
     * que el producto exista antes de consultar sus movimientos.
     *
     * @param id identificador del producto
     * @return 200 OK con la lista de movimientos ordenados por fecha descendente
     */
    @Operation(summary = "Kardex del producto", description = "Historial completo de movimientos de stock ordenado del más reciente al más antiguo")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Historial de movimientos"),
                    @ApiResponse(responseCode = "404", description = "Producto no encontrado") })
    @GetMapping("/{id}/movements")
    public ResponseEntity<PageResponseDTO<StockMovementResponseDTO>> getStockMovementsByProduct(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.getStockMovementsByProduct(id, page, size));
    }
}
