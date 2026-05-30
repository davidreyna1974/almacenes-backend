package com.codigo2enter.almacenes.modules.sales.repository;

import com.codigo2enter.almacenes.modules.sales.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
