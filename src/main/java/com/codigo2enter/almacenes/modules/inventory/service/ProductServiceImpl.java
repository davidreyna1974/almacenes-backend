package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;
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
        product.setCreatedBy(resolveAuthenticatedUser());

        return productMapper.toResponseDTO(productRepository.save(product));
    }

    /**
     * {@inheritDoc}
     *
     * Flujo de validaciones antes de actualizar:
     *   1. Verifica que el producto exista.
     *   2. Valida que el nuevo SKU no esté en uso por un producto DIFERENTE.
     *      Sin esta validación, si el cliente cambia el SKU a uno ya registrado,
     *      PostgreSQL lanzaría una excepción de constraint UNIQUE sin mensaje
     *      de negocio — el frontend no podría mostrar un error claro al usuario.
     *      La condición !existing.getId().equals(id) permite que el producto
     *      conserve su propio SKU sin disparar el error (editar sin cambiar SKU).
     *   3. Aplica los cambios escalares con el mapper.
     *   4. Resuelve la categoría actualizada desde el categoryId del DTO.
     */
    @Override
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO dto) {
        Product product = findProductOrThrow(id);

        // Validar que el nuevo SKU no pertenezca a otro producto distinto.
        productRepository.findBySku(dto.getSku()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException(
                    "El SKU '" + dto.getSku() + "' ya está en uso por otro producto."
                );
            }
        });

        // El mapper actualiza: sku, name, description, price, currentStock,
        // minimumStock, status, supplierId. Ignora id, active, createdAt y category.
        productMapper.updateFromDTO(dto, product);

        // Resuelve y asigna la categoría — si categoryId cambió, se aplica el nuevo.
        product.setCategory(resolveCategory(dto.getCategoryId()));
        product.setUpdatedAt(java.time.LocalDateTime.now());
        product.setUpdatedBy(resolveAuthenticatedUser());

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
    /**
     * {@inheritDoc}
     *
     * Convierte el campo 'type' del DTO (String) al enum MovementType usando
     * MovementType.valueOf(). Si el cliente envía un valor distinto de "IN" u "OUT",
     * valueOf() lanza IllegalArgumentException — el controlador debe capturarla
     * y devolverla como HTTP 400 Bad Request con un mensaje claro.
     *
     * Orden de validaciones:
     *   1. Conversión del tipo (falla rápido si el valor es inválido).
     *   2. Existencia del producto.
     *   3. Quantity positiva — aunque @Min(1) en el DTO ya lo garantiza en el
     *      controlador, se mantiene como defensa en profundidad si el método
     *      es invocado directamente desde otro servicio sin pasar por el @Valid.
     *   4. Stock suficiente para movimientos OUT.
     */
    @Override
    public void registerStockMovement(StockMovementRequestDTO request) {

        // Convertir String a enum. valueOf() lanza IllegalArgumentException
        // si el valor no es exactamente "IN" o "OUT" (case-sensitive).
        MovementType type;
        try {
            type = MovementType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "Tipo de movimiento inválido: '" + request.getType() + "'. Use 'IN' o 'OUT'."
            );
        }

        Product product = findProductOrThrow(request.getProductId());

        // Defensa en profundidad: valida quantity > 0 aunque el DTO ya lo restringe
        // con @Min(1) para el caso en que el método sea llamado sin pasar por @Valid.
        if (request.getQuantity() <= 0) {
            throw new RuntimeException(
                "La cantidad debe ser mayor a cero. Valor recibido: " + request.getQuantity() + "."
            );
        }

        // Validar que una salida no genere stock negativo.
        if (type == MovementType.OUT && product.getCurrentStock() - request.getQuantity() < 0) {
            throw new RuntimeException(
                "Stock insuficiente. Disponible: " + product.getCurrentStock()
                + ", solicitado: " + request.getQuantity() + "."
            );
        }

        // Calcular y aplicar el nuevo nivel de stock según el tipo de movimiento.
        int newStock = type == MovementType.IN
                ? product.getCurrentStock() + request.getQuantity()
                : product.getCurrentStock() - request.getQuantity();

        product.setCurrentStock(newStock);
        productRepository.save(product);

        // Registrar el movimiento como bitácora inmutable del Kardex.
        // createdBy se resuelve desde el JWT para trazabilidad de auditoría.
        User creator = resolveAuthenticatedUser();

        StockMovement movement = StockMovement.builder()
                .product(product)
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .type(type)
                .createdBy(creator)
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
     * Valida primero que la categoría exista para distinguir entre
     * "categoría no encontrada" (error) y "categoría sin productos" (lista vacía).
     * Sin esta validación, ambos casos devolverían [] con HTTP 200, haciendo
     * imposible que el cliente diferencie un ID inválido de una categoría vacía.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getByCategoryId(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new RuntimeException(
                "Categoría con id " + categoryId + " no encontrada."
            );
        }
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
        Product product = findProductOrThrow(id);
        product.setActive(false);
        product.setUpdatedAt(java.time.LocalDateTime.now());
        product.setUpdatedBy(resolveAuthenticatedUser());
    }

    /**
     * {@inheritDoc}
     *
     * Valida primero que el producto exista para distinguir entre
     * "producto no encontrado" (error) y "producto sin movimientos" (lista vacía).
     * Sin esta validación, un productId inexistente devolvería [] con HTTP 200,
     * siendo imposible diferenciar un ID inválido de un producto sin historial.
     *
     * El repositorio ya devuelve los movimientos ordenados por createdAt DESC,
     * listos para pintar el Kardex en el frontend sin ordenamiento adicional.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponseDTO> getStockMovementsByProduct(Long productId) {
        // Verificar existencia del producto antes de consultar sus movimientos.
        findProductOrThrow(productId);

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
     * Resuelve el usuario autenticado desde el JWT en SecurityContextHolder.
     * Mismo patrón que PurchaseOrderServiceImpl.resolveAuthenticatedUser().
     */
    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(
                        "Usuario autenticado no encontrado en el sistema."));
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
