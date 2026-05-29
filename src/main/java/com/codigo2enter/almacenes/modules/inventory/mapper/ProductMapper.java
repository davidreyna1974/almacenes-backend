package com.codigo2enter.almacenes.modules.inventory.mapper;

import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper MapStruct para la conversión entre Product y sus DTOs.
 *
 * El caso más relevante de este mapper es toResponseDTO: la entidad Product
 * tiene una relación @ManyToOne con Category (objeto anidado), pero
 * ProductResponseDTO expone esa relación aplanada en dos campos simples
 * (categoryId, categoryName). Las anotaciones @Mapping con 'source' de
 * múltiples niveles (category.id, category.name) le indican a MapStruct
 * cómo navegar la relación y extraer los valores correctos.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    /**
     * Convierte una entidad Product a su DTO de salida.
     *
     * Las instrucciones source = "category.id" y source = "category.name"
     * le indican a MapStruct que navegue la relación LAZY de la entidad
     * (product.getCategory().getId() y product.getCategory().getName())
     * y deposite esos valores en los campos planos del DTO.
     *
     * Esto debe ejecutarse dentro de una transacción activa para que
     * Hibernate pueda resolver el proxy LAZY de Category sin lanzar
     * LazyInitializationException.
     *
     * @param product entidad persistida con su relación Category cargada
     * @return ProductResponseDTO con categoryId y categoryName resueltos
     */
    @Mapping(source = "category.id",        target = "categoryId")
    @Mapping(source = "category.name",      target = "categoryName")
    @Mapping(source = "createdBy.id",       target = "createdById")
    @Mapping(source = "createdBy.username", target = "createdByUsername")
    @Mapping(source = "updatedBy.id",       target = "updatedById")
    @Mapping(source = "updatedBy.username", target = "updatedByUsername")
    ProductResponseDTO toResponseDTO(Product product);

    /**
     * Convierte una lista de entidades Product a lista de ProductResponseDTO.
     * MapStruct aplica toResponseDTO() a cada elemento automáticamente.
     * Usado en los endpoints que devuelven colecciones (findAllActive, findLowStock, etc.).
     *
     * @param products lista de entidades recuperadas de la base de datos
     * @return lista de DTOs lista para serializar como JSON array
     */
    List<ProductResponseDTO> toResponseDTOList(List<Product> products);

    /**
     * Convierte un ProductRequestDTO en una nueva entidad Product.
     *
     * Campos ignorados y motivo:
     *   - id          : generado por PostgreSQL con IDENTITY
     *   - active      : el servicio lo inicializa en true
     *   - createdAt   : el servicio lo inicializa con @Builder.Default = LocalDateTime.now()
     *   - category    : el servicio resuelve la entidad Category desde categoryId
     *                   consultando CategoryRepository; no puede inferirse solo del ID
     *   - categoryId  : es un campo del DTO sin campo equivalente en la entidad
     *                   (la entidad tiene el objeto Category, no su ID como campo propio)
     *
     * @param dto datos del producto enviados por el cliente
     * @return entidad Product lista para que el servicio complete category y persista
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "active",    ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "category",  ignore = true)
    Product toEntity(ProductRequestDTO dto);

    /**
     * Actualiza una entidad Product existente con los datos del DTO.
     * Mismo conjunto de ignores que toEntity por las mismas razones.
     * El servicio se encarga de resolver y actualizar la relación Category
     * si categoryId cambió en el request.
     *
     * @param dto     datos nuevos enviados por el cliente
     * @param product entidad existente recuperada de la base de datos
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "active",    ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "category",  ignore = true)
    void updateFromDTO(ProductRequestDTO dto, @MappingTarget Product product);
}
