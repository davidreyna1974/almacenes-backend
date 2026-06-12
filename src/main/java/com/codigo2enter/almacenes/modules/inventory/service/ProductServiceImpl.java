package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.core.exception.DuplicateResourceException;
import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
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
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import com.codigo2enter.almacenes.modules.purchases.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.GrantedAuthority;
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
    private final SupplierRepository supplierRepository;
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
            throw new DuplicateResourceException(
                "Ya existe un producto con el SKU '" + dto.getSku() + "'."
            );
        }

        Category category = resolveCategory(dto.getCategoryId());

        // El mapper copia: sku, name, description, price, currentStock, minimumStock,
        // status, supplierId. El servicio asigna category y deja active/createdAt
        // a @Builder.Default de la entidad.
        Product product = productMapper.toEntity(dto);
        product.setCategory(category);
        product.setSupplier(resolveSupplier(dto.getSupplierId()));
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
                throw new DuplicateResourceException(
                    "El SKU '" + dto.getSku() + "' ya está en uso por otro producto."
                );
            }
        });

        // El mapper actualiza: sku, name, description, price, currentStock,
        // minimumStock, status, supplierId. Ignora id, active, createdAt y category.
        productMapper.updateFromDTO(dto, product);

        // Resuelve y asigna categoría y proveedor — si cambiaron en el request, se aplican los nuevos.
        product.setCategory(resolveCategory(dto.getCategoryId()));
        product.setSupplier(resolveSupplier(dto.getSupplierId()));
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
        return redactUnitCost(productMapper.toResponseDTOList(productRepository.findLowStockProducts()));
    }

    /**
     * {@inheritDoc}
     *
     * Sort por id ASC como criterio neutro — el criterio de relevancia ya está
     * en el WHERE de la query (availableStock <= minimumStock). No tiene sentido
     * ordenar por stock porque el frontend ya muestra todos los productos críticos.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductResponseDTO> getLowStockProducts(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<Product> result = productRepository.findLowStockProducts(pageable);
        return redactUnitCost(PageResponseDTO.from(result.map(productMapper::toResponseDTO)));
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
            throw new BusinessRuleException(
                "Tipo de movimiento inválido: '" + request.getType() + "'. Use 'IN' o 'OUT'."
            );
        }

        Product product = findProductOrThrow(request.getProductId());

        // Defensa en profundidad: valida quantity > 0 aunque el DTO ya lo restringe
        // con @Min(1) para el caso en que el método sea llamado sin pasar por @Valid.
        if (request.getQuantity() <= 0) {
            throw new BusinessRuleException(
                "La cantidad debe ser mayor a cero. Valor recibido: " + request.getQuantity() + "."
            );
        }

        // Validar que una salida no consuma stock reservado para órdenes de venta.
        // Con la introducción de reservedStock, la validación compara contra el
        // stock DISPONIBLE (físico - reservado), no el físico total.
        //
        // Justificación: un OUT manual (merma, ajuste) no puede consumir unidades
        // que ya están comprometidas con órdenes APPROVED. Si se permitiera,
        // deliverOrder() fallaría después por falta de stock físico.
        //
        // Los movimientos OUT generados por deliverOrder() también pasan por aquí,
        // pero en ese flujo la reserva ya fue liberada ANTES de llamar a este método,
        // por lo que available incluye las unidades liberadas y la validación pasa.
        if (type == MovementType.OUT) {
            int available = product.getCurrentStock() - product.getReservedStock();
            if (available - request.getQuantity() < 0) {
                throw new BusinessRuleException(
                    "No se puede registrar la salida: solo hay " + available +
                    " unidades disponibles (stock físico " + product.getCurrentStock() +
                    " − " + product.getReservedStock() +
                    " reservadas para órdenes de venta). Solicitado: " +
                    request.getQuantity() + "."
                );
            }
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

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getById(Long id) {
        return redactUnitCost(productMapper.toResponseDTO(findProductOrThrow(id)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Producto con SKU '" + sku + "' no encontrado."
                ));
        return redactUnitCost(productMapper.toResponseDTO(product));
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
            throw new ResourceNotFoundException(
                "Categoría con id " + categoryId + " no encontrada."
            );
        }
        return redactUnitCost(productMapper.toResponseDTOList(
            productRepository.findByCategoryIdAndActiveTrue(categoryId)
        ));
    }

    /**
     * {@inheritDoc}
     *
     * Valida la existencia de la categoría antes de paginar para distinguir
     * entre "categoría inexistente" (RuntimeException → 500/400) y
     * "categoría sin productos" (PageResponseDTO con content vacío → 200).
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductResponseDTO> getByCategoryId(Long categoryId, int page, int size) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException(
                "Categoría con id " + categoryId + " no encontrada."
            );
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Product> result = productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
        return redactUnitCost(PageResponseDTO.from(result.map(productMapper::toResponseDTO)));
    }

    /**
     * {@inheritDoc}
     *
     * Normaliza search: cadena en blanco o nula → null, para que el patrón
     * (:search IS NULL OR ...) de JPQL omita la condición correctamente.
     * El resultado se ordena por name ASC — criterio estándar para catálogos.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductResponseDTO> searchProducts(String search, Long categoryId,
                                                              String status, Long supplierId,
                                                              int page, int size) {
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        PageRequest pageable = PageRequest.of(page, size);
        Page<Product> result = productRepository.searchProducts(
                normalizedSearch, categoryId, status, supplierId, pageable);
        return redactUnitCost(PageResponseDTO.from(result.map(productMapper::toResponseDTO)));
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

    /**
     * {@inheritDoc}
     *
     * Se pasa Pageable sin Sort explícito porque el ORDER BY ya está embebido
     * en el nombre del método del repositorio (OrderByCreatedAtDesc).
     * Agregar un Sort aquí causaría un ORDER BY doble en el SQL generado.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<StockMovementResponseDTO> getStockMovementsByProduct(Long productId, int page, int size) {
        findProductOrThrow(productId);
        PageRequest pageable = PageRequest.of(page, size);
        Page<StockMovement> result =
                stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        return PageResponseDTO.from(result.map(stockMovementMapper::toResponseDTO));
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
                .orElseThrow(() -> new ResourceNotFoundException(
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
     * Determina si el usuario autenticado puede ver unitCost (costo de compra).
     * Solo ADMIN y MANAGER tienen visibilidad de datos financieros — WAREHOUSEMAN
     * y SALES no, aunque puedan leer el catálogo de productos (BUG-INV-11).
     */
    private boolean canViewUnitCost() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_MANAGER"));
    }

    /**
     * Oculta unitCost (lo deja en null) si el usuario autenticado no tiene
     * permiso para ver datos financieros (BUG-INV-11). Se aplica a cada
     * endpoint de lectura antes de retornar la respuesta.
     */
    private ProductResponseDTO redactUnitCost(ProductResponseDTO dto) {
        if (!canViewUnitCost()) {
            dto.setUnitCost(null);
        }
        return dto;
    }

    private List<ProductResponseDTO> redactUnitCost(List<ProductResponseDTO> dtos) {
        if (!canViewUnitCost()) {
            dtos.forEach(dto -> dto.setUnitCost(null));
        }
        return dtos;
    }

    private PageResponseDTO<ProductResponseDTO> redactUnitCost(PageResponseDTO<ProductResponseDTO> page) {
        if (!canViewUnitCost()) {
            page.getContent().forEach(dto -> dto.setUnitCost(null));
        }
        return page;
    }

    /**
     * Busca una categoría por ID o lanza RuntimeException si no existe.
     * Usado en createProduct y updateProduct para validar que el categoryId
     * del DTO corresponde a una categoría real antes de asignarla al producto.
     */
    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Categoría con id " + categoryId + " no encontrada."
                ));
    }

    /**
     * Busca un proveedor por ID o lanza ResourceNotFoundException si no existe.
     * Usado en createProduct y updateProduct para validar que el supplierId
     * del DTO corresponde a un proveedor real antes de asignarlo al producto.
     */
    private Supplier resolveSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Proveedor con id " + supplierId + " no encontrado."
                ));
    }
}
