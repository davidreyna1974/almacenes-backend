package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.mapper.ProductMapper;
import com.codigo2enter.almacenes.modules.inventory.mapper.StockMovementMapper;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.model.MovementType;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación concreta de ProductService.
 *
 * Gestiona tanto el ciclo de vida del producto (CRUD) como la lógica de
 * movimientos de stock. Ambas responsabilidades coexisten aquí porque un
 * movimiento de stock siempre modifica el estado de un producto — mantenerlos
 * en el mismo servicio garantiza consistencia transaccional sin coordinación
 * entre servicios distintos.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductMapper productMapper;
    private final StockMovementMapper stockMovementMapper;

    /**
     * {@inheritDoc}
     *
     * Flujo:
     *   1. Valida unicidad del SKU (existsBySku — más eficiente que findBySku
     *      para validaciones porque genera SELECT COUNT en lugar de SELECT *).
     *   2. Convierte el DTO a entidad (mapper ignora category, id, active, createdAt).
     *   3. Resuelve la entidad Category desde el categoryId del DTO.
     *   4. Asigna la categoría y persiste.
     */
    @Override
    public ProductResponseDTO createProduct(ProductRequestDTO dto) {
        if (productRepository.existsBySku(dto.getSku())) {
            throw new RuntimeException(
                "Ya existe un producto con el SKU '" + dto.getSku() + "'."
            );
        }

        Category category = resolveCategory(dto.getCategoryId());

        // El mapper copia: sku, name, description, price, currentStock, minimumStock,
        // status, supplierId. El servicio asigna category y deja active/createdAt
        // a @Builder.Default de la entidad.
        Product product = productMapper.toEntity(dto);
        product.setCategory(category);

        return productMapper.toResponseDTO(productRepository.save(product));
    }

    /**
     * {@inheritDoc}
     *
     * El mapper updateFromDTO actualiza los campos escalares del producto.
     * Luego el servicio resuelve siempre la categoría desde el categoryId
     * del DTO para reflejar cualquier cambio en la asignación.
     */
    @Override
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO dto) {
        Product product = findProductOrThrow(id);

        productMapper.updateFromDTO(dto, product);
        product.setCategory(resolveCategory(dto.getCategoryId()));

        return productMapper.toResponseDTO(productRepository.save(product));
    }

    /**
     * {@inheritDoc}
     *
     * readOnly = true porque solo consulta — Hibernate omite el flush al cerrar
     * la transacción, reduciendo el tiempo de respuesta en listas potencialmente grandes.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getLowStockProducts() {
        return productMapper.toResponseDTOList(productRepository.findLowStockProducts());
    }

    /**
     * {@inheritDoc}
     *
     * Lógica de negocio central del inventario:
     *   1. Valida que un movimiento OUT no deje el stock en negativo.
     *   2. Calcula el nuevo nivel de stock según el tipo de movimiento.
     *   3. Actualiza currentStock en el producto.
     *   4. Registra el movimiento en stock_movements como bitácora inmutable.
     *
     * Ambas operaciones (actualizar producto y guardar movimiento) ocurren
     * dentro de la misma transacción — si cualquiera falla, Hibernate hace
     * rollback de las dos, garantizando que el stock y el historial
     * estén siempre sincronizados.
     */
    @Override
    public void registerStockMovement(Long productId, int quantity, String reason, MovementType type) {
        Product product = findProductOrThrow(productId);

        // Validar que una salida no genere stock negativo.
        if (type == MovementType.OUT && product.getCurrentStock() - quantity < 0) {
            throw new RuntimeException(
                "Stock insuficiente. Disponible: " + product.getCurrentStock()
                + ", solicitado: " + quantity + "."
            );
        }

        // Calcular y aplicar el nuevo nivel de stock.
        int newStock = type == MovementType.IN
                ? product.getCurrentStock() + quantity
                : product.getCurrentStock() - quantity;

        product.setCurrentStock(newStock);
        productRepository.save(product);

        // Registrar el movimiento como bitácora — no se actualiza ni elimina jamás.
        StockMovement movement = StockMovement.builder()
                .product(product)
                .quantity(quantity)
                .reason(reason)
                .type(type)
                .build();

        stockMovementRepository.save(movement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException(
                    "Producto con SKU '" + sku + "' no encontrado."
                ));
        return productMapper.toResponseDTO(product);
    }

    /**
     * {@inheritDoc}
     *
     * Usa findByCategoryIdAndActiveTrue para excluir automáticamente
     * los productos dados de baja lógica dentro de esa categoría.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getByCategoryId(Long categoryId) {
        return productMapper.toResponseDTOList(
            productRepository.findByCategoryIdAndActiveTrue(categoryId)
        );
    }

    /**
     * {@inheritDoc}
     *
     * Soft delete: setActive(false) sin borrar el registro.
     * Hibernate dirty-checking persiste el cambio al cerrar la transacción.
     */
    @Override
    public void deactivateProduct(Long id) {
        findProductOrThrow(id).setActive(false);
    }

    /**
     * {@inheritDoc}
     *
     * El repositorio ya devuelve los movimientos ordenados por createdAt DESC,
     * listo para pintar el Kardex en el frontend sin ordenamiento adicional.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponseDTO> getStockMovementsByProduct(Long productId) {
        return stockMovementMapper.toResponseDTOList(
            stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId)
        );
    }

    // -------------------------------------------------------------------------
    // Métodos privados de apoyo
    // -------------------------------------------------------------------------

    /**
     * Busca un producto por ID o lanza RuntimeException si no existe.
     * Centraliza el manejo del "not found" para no repetir el mismo bloque
     * en cada método del servicio.
     */
    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Producto con id " + id + " no encontrado."
                ));
    }

    /**
     * Busca una categoría por ID o lanza RuntimeException si no existe.
     * Usado en createProduct y updateProduct para validar que el categoryId
     * del DTO corresponde a una categoría real antes de asignarla al producto.
     */
    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException(
                    "Categoría con id " + categoryId + " no encontrada."
                ));
    }
}
