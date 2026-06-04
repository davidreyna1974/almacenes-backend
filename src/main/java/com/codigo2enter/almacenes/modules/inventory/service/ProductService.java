package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementResponseDTO;

import java.util.List;

/**
 * Contrato de la capa de servicio para la gestión de productos e inventario.
 *
 * Centraliza tanto las operaciones CRUD de productos como la lógica de
 * movimientos de stock, manteniendo la coherencia entre el nivel de existencias
 * y el historial de Kardex en cada operación.
 */
public interface ProductService {

    /**
     * Registra un nuevo producto en el inventario.
     * Valida unicidad del SKU y resuelve la entidad Category desde categoryId.
     *
     * @param dto datos del producto enviados por el cliente
     * @return ProductResponseDTO con el id y categoryName resueltos
     */
    ProductResponseDTO createProduct(ProductRequestDTO dto);

    /**
     * Actualiza los datos de un producto existente.
     * Si categoryId cambió, el servicio resuelve la nueva entidad Category.
     *
     * @param id  identificador del producto a modificar
     * @param dto datos nuevos enviados por el cliente
     * @return ProductResponseDTO con los datos actualizados
     */
    ProductResponseDTO updateProduct(Long id, ProductRequestDTO dto);

    /**
     * Retorna los productos activos cuyo stock actual está en nivel crítico
     * (currentStock <= minimumStock). Alimenta el panel de alertas del frontend.
     *
     * @return lista de productos con stock bajo o igual al mínimo configurado
     */
    List<ProductResponseDTO> getLowStockProducts();

    /**
     * Retorna una página de productos con stock en nivel crítico.
     *
     * @param page número de página (base 0)
     * @param size cantidad de registros por página
     * @return PageResponseDTO con los productos de stock bajo de la página solicitada
     */
    PageResponseDTO<ProductResponseDTO> getLowStockProducts(int page, int size);

    /**
     * Registra un movimiento de stock (entrada o salida) sobre un producto.
     * Actualiza currentStock del producto y genera un registro en stock_movements.
     * Para movimientos OUT valida que el stock no quede negativo.
     * El campo 'type' del DTO llega como String ("IN"/"OUT") y el servicio
     * lo convierte al enum MovementType antes de ejecutar la lógica.
     *
     * @param request DTO con productId, quantity, reason y type
     */
    void registerStockMovement(StockMovementRequestDTO request);

    /**
     * Busca un producto por su ID.
     *
     * @param id identificador del producto
     * @return ProductResponseDTO si existe
     */
    ProductResponseDTO getById(Long id);

    /**
     * Busca un producto por su SKU.
     *
     * @param sku código único del producto
     * @return ProductResponseDTO si existe
     */
    ProductResponseDTO getBySku(String sku);

    /**
     * Retorna todos los productos activos que pertenecen a una categoría.
     * Usa findByCategoryIdAndActiveTrue para excluir productos dados de baja.
     *
     * @param categoryId id de la categoría por la que filtrar
     * @return lista de productos activos de esa categoría
     */
    List<ProductResponseDTO> getByCategoryId(Long categoryId);

    /**
     * Retorna una página de productos activos de una categoría, ordenados por nombre.
     *
     * @param categoryId id de la categoría
     * @param page       número de página (base 0)
     * @param size       cantidad de registros por página
     * @return PageResponseDTO con los productos de la página solicitada
     */
    PageResponseDTO<ProductResponseDTO> getByCategoryId(Long categoryId, int page, int size);

    /**
     * Desactiva lógicamente un producto (soft delete: active = false).
     *
     * @param id identificador del producto a desactivar
     */
    void deactivateProduct(Long id);

    /**
     * Retorna el historial completo de movimientos de un producto,
     * ordenado del más reciente al más antiguo (Kardex).
     *
     * @param productId id del producto consultado
     * @return lista de movimientos ordenados por fecha descendente
     */
    List<StockMovementResponseDTO> getStockMovementsByProduct(Long productId);

    /**
     * Retorna una página del historial de movimientos de un producto (Kardex paginado).
     * El orden por fecha descendente se hereda del método de repositorio.
     *
     * @param productId id del producto
     * @param page      número de página (base 0)
     * @param size      cantidad de registros por página
     * @return PageResponseDTO con los movimientos de la página solicitada
     */
    PageResponseDTO<StockMovementResponseDTO> getStockMovementsByProduct(Long productId, int page, int size);
}
