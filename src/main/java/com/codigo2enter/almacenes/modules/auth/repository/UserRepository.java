package com.codigo2enter.almacenes.modules.auth.repository;

import com.codigo2enter.almacenes.modules.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad User.
 * Proporciona acceso directo a las operaciones CRUD en la tabla 'users' de PostgreSQL.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Recupera un usuario basándose en su nombre de usuario.
     * Utilizado principalmente durante el proceso de autenticación.
     */
    Optional<User> findByUsername(String username);

    /**
     * Recupera un usuario con sus roles cargados en una sola query (JOIN FETCH).
     * Necesario cuando User.roles es FetchType.LAZY y el código accede a getRoles()
     * fuera del contexto de la query original (ej. login, update de roles).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    /**
     * Recupera un usuario basándose en su correo electrónico.
     * Utilizado para flujos de recuperación de cuenta o auditoría.
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica la existencia de un nombre de usuario en la base de datos.
     * Previene la duplicidad de registros en el formulario de inscripción.
     */
    Boolean existsByUsername(String username);

    /**
     * Verifica la existencia de un correo electrónico en la base de datos.
     * Previene la duplicidad de registros en el formulario de inscripción.
     */
    Boolean existsByEmail(String email);

    /** Retorna todos los usuarios activos para el panel de administración. */
    List<User> findByActiveTrue();

    /**
     * Retorna una página de usuarios activos, ordenada según el Pageable indicado.
     * La versión sin Pageable se conserva para uso interno (tests de integración,
     * métodos que necesitan la lista completa sin paginación).
     *
     * @param pageable parámetros de paginación y ordenación
     * @return página de usuarios activos
     */
    Page<User> findByActiveTrue(Pageable pageable);

    /**
     * Busca un usuario por email excluyendo un ID específico.
     * Usado en updateUser() para validar que el nuevo email no pertenece
     * a otro usuario diferente al que se está editando.
     */
    Optional<User> findByEmailAndIdNot(String email, Long id);

    /**
     * Busca un usuario por username excluyendo un ID específico.
     * Mismo patrón que findByEmailAndIdNot — para validar unicidad en update.
     */
    Optional<User> findByUsernameAndIdNot(String username, Long id);
}
