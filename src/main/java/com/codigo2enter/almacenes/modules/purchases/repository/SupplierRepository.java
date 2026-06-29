package com.codigo2enter.almacenes.modules.purchases.repository;

import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad Supplier.
 *
 * Spring Data JPA genera automáticamente las implementaciones SQL de todos
 * los métodos declarados aquí, derivándolas del nombre del método.
 *
 * La unicidad de RFC y companyName se valida en dos niveles:
 *   1. Base de datos — constraint UNIQUE en 'rfc' y constraint UNIQUE en 'email'
 *   2. Servicio — validación programática usando existsByRfc y existsByCompanyName
 *      antes de persistir, para devolver mensajes de error claros al cliente
 *      en lugar de excepciones de constraint de PostgreSQL.
 */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Busca un proveedor por su RFC.
     * Usado en SupplierServiceImpl para validar unicidad al crear y actualizar:
     * si el Optional tiene valor y su id es diferente al que se edita,
     * el RFC ya pertenece a otro proveedor.
     *
     * @param rfc identificador fiscal del proveedor (máximo 13 caracteres)
     * @return Optional con el proveedor si existe, vacío si no
     */
    Optional<Supplier> findByRfc(String rfc);

    /**
     * Busca un proveedor por su razón social.
     * Usado para validar unicidad de companyName antes de crear o actualizar.
     * La unicidad se valida en el servicio (no con constraint en BD) para
     * permitir flexibilidad ante casos donde dos entidades distintas puedan
     * tener la misma razón social en diferentes jurisdicciones.
     *
     * @param companyName razón social del proveedor
     * @return Optional con el proveedor si existe, vacío si no
     */
    Optional<Supplier> findByCompanyName(String companyName);

    /**
     * Verifica si ya existe un proveedor con el RFC indicado.
     * Más eficiente que findByRfc para validaciones de unicidad al crear,
     * ya que genera SELECT COUNT en lugar de SELECT *.
     *
     * @param rfc RFC a verificar
     * @return true si el RFC ya está registrado, false si está disponible
     */
    boolean existsByRfc(String rfc);

    /**
     * Verifica si ya existe un proveedor con la razón social indicada.
     * Usado en createSupplier para validar unicidad antes de persistir.
     *
     * @param companyName razón social a verificar
     * @return true si la razón social ya está registrada, false si está disponible
     */
    boolean existsByCompanyName(String companyName);

    /**
     * Recupera todos los proveedores con active = true.
     * Usado en getAllActiveSuppliers para poblar los selectores de Angular
     * al crear una nueva orden de compra. Los proveedores dados de baja
     * (soft delete) no aparecen en esta consulta.
     *
     * @return lista de proveedores activos, vacía si no hay ninguno
     */
    List<Supplier> findByActiveTrue();

    /**
     * Versión paginada de la consulta de proveedores activos.
     * La versión sin Pageable se conserva para uso en validaciones de servicio
     * (e.g. verificar órdenes activas antes de desactivar).
     *
     * @param pageable parámetros de paginación y ordenación
     * @return página de proveedores activos
     */
    Page<Supplier> findByActiveTrue(Pageable pageable);

    /**
     * Búsqueda de proveedores activos con filtro de texto opcional.
     *
     * search → coincidencia parcial en company_name o rfc, insensible a
     *          mayúsculas Y acentos usando f_unaccent() (PostgreSQL extension 'unaccent').
     *          Si search es null, retorna todos los proveedores activos.
     *
     * Query nativa porque JPQL no expone funciones PostgreSQL personalizadas.
     * Índices funcionales idx_suppliers_unaccent_company e idx_suppliers_unaccent_rfc
     * aceleran la consulta.
     */
    @Query(value =
           "SELECT s.* FROM suppliers s " +
           "WHERE s.active = true " +
           "AND (:search IS NULL OR (" +
           "     f_unaccent(lower(s.company_name)) LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(s.rfc))          LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%')) " +
           "ORDER BY s.company_name ASC",
           countQuery =
           "SELECT COUNT(*) FROM suppliers s " +
           "WHERE s.active = true " +
           "AND (:search IS NULL OR (" +
           "     f_unaccent(lower(s.company_name)) LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(s.rfc))          LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%'))",
           nativeQuery = true)
    Page<Supplier> searchSuppliers(@Param("search") String search, Pageable pageable);
}
