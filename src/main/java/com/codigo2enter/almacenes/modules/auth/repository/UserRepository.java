package com.codigo2enter.almacenes.modules.auth.repository;

import com.codigo2enter.almacenes.modules.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
