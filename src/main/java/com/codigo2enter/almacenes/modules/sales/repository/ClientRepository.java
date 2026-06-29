package com.codigo2enter.almacenes.modules.sales.repository;

import com.codigo2enter.almacenes.modules.sales.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad Client.
 *
 * Combina query methods derivados para las operaciones habituales de catálogo.
 * Los métodos existsByRfc y existsByEmail generan SELECT COUNT — más eficientes
 * que findByRfc/findByEmail para validaciones de unicidad porque no traen
 * el objeto completo.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Retorna todos los clientes activos.
     * La condición AndActiveTrue excluye clientes dados de baja (soft delete).
     */
    List<Client> findByActiveTrue();

    /**
     * Versión paginada de la consulta de clientes activos.
     * La versión sin Pageable se conserva para uso en validaciones de servicio
     * (verificar órdenes activas antes de desactivar un cliente).
     *
     * @param pageable parámetros de paginación y ordenación
     * @return página de clientes activos
     */
    Page<Client> findByActiveTrue(Pageable pageable);

    /**
     * Busca un cliente por RFC. Usado para validar unicidad antes de crear
     * o actualizar un cliente.
     */
    Optional<Client> findByRfc(String rfc);

    /**
     * Verifica si ya existe un cliente con el RFC indicado.
     * Más eficiente que findByRfc() para validaciones de unicidad.
     */
    boolean existsByRfc(String rfc);

    /**
     * Busca un cliente por email. Usado para validar unicidad antes de crear
     * o actualizar un cliente.
     */
    Optional<Client> findByEmail(String email);

    /**
     * Verifica si ya existe un cliente con el email indicado.
     * Más eficiente que findByEmail() para validaciones de unicidad.
     */
    boolean existsByEmail(String email);

    /**
     * Búsqueda de clientes activos con filtro de texto opcional.
     *
     * search → coincidencia parcial en name, rfc o contact_name, insensible a
     *          mayúsculas Y acentos usando f_unaccent() (PostgreSQL extension 'unaccent').
     *          Si search es null, retorna todos los clientes activos.
     *
     * Query nativa porque JPQL no expone funciones PostgreSQL personalizadas.
     * Índices funcionales idx_clients_unaccent_name e idx_clients_unaccent_rfc
     * aceleran la consulta.
     */
    @Query(value =
           "SELECT c.* FROM clients c " +
           "WHERE c.active = true " +
           "AND (:search IS NULL OR (" +
           "     f_unaccent(lower(c.name))                        LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(COALESCE(c.rfc,'')))            LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(COALESCE(c.contact_name,'')))   LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%')) " +
           "ORDER BY c.name ASC",
           countQuery =
           "SELECT COUNT(*) FROM clients c " +
           "WHERE c.active = true " +
           "AND (:search IS NULL OR (" +
           "     f_unaccent(lower(c.name))                        LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(COALESCE(c.rfc,'')))            LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(COALESCE(c.contact_name,'')))   LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%'))",
           nativeQuery = true)
    Page<Client> searchClients(@Param("search") String search, Pageable pageable);
}
