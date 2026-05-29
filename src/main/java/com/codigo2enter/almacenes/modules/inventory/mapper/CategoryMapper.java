package com.codigo2enter.almacenes.modules.inventory.mapper;

import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para la conversión entre Category y CategoryDTO.
 *
 * Cubre dos sentidos de conversión:
 *   - Category    → CategoryDTO  (para respuestas HTTP)
 *   - CategoryDTO → Category     (para crear una nueva entidad)
 *
 * MapStruct genera la implementación CategoryMapperImpl en tiempo de compilación.
 * El bean resultante es gestionado por Spring gracias a componentModel = "spring".
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    /**
     * Convierte una entidad Category a su DTO de salida.
     * Los campos de auditoría de usuario se aplanan: createdBy y updatedBy
     * (relaciones @ManyToOne) se exponen como pares de ID + username.
     *
     * @param category entidad persistida con datos de la base de datos
     * @return CategoryDTO listo para serializar como JSON en la respuesta HTTP
     */
    @Mapping(source = "createdBy.id",       target = "createdById")
    @Mapping(source = "createdBy.username", target = "createdByUsername")
    @Mapping(source = "updatedBy.id",       target = "updatedById")
    @Mapping(source = "updatedBy.username", target = "updatedByUsername")
    CategoryDTO toDTO(Category category);

    /**
     * Convierte una lista de entidades Category a una lista de CategoryDTO.
     *
     * MapStruct genera esta implementación automáticamente iterando sobre
     * la lista y aplicando toDTO() a cada elemento — no hay lógica manual.
     * Declararlo aquí evita escribir el stream en cada método del servicio
     * que necesite devolver una colección de categorías.
     *
     * @param categories lista de entidades recuperadas de la base de datos
     * @return lista de DTOs lista para serializar como JSON array
     */
    List<CategoryDTO> toDTOList(List<Category> categories);

    /**
     * Convierte un CategoryDTO de entrada en una nueva entidad Category.
     *
     * Se ignoran 'id' y 'active' porque:
     *   - id: lo genera PostgreSQL con IDENTITY, no debe venir del cliente.
     *   - active: el servicio lo inicializa en true en toda nueva categoría.
     *
     * @param dto datos enviados por el cliente en el request
     * @return entidad Category lista para persistir con save()
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "active",    ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Category toEntity(CategoryDTO dto);
}
