# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Descripción del proyecto

Backend REST API para gestión de almacenes. Proyecto Spring Boot 3.5.14 con Java 17, Maven y PostgreSQL.

**Módulos principales:**
- `auth` — Autenticación y autorización (JWT/Spring Security)
- `inventory` — Gestión de inventario
- `purchases` — Gestión de compras

## Comandos comunes

```bash
# Compilar
./mvnw clean package

# Ejecutar en modo desarrollo (puerto 8080)
./mvnw spring-boot:run

# Ejecutar tests
./mvnw test

# Ejecutar un test específico
./mvnw test -Dtest=NombreDeClaseTest

# Compilar sin ejecutar tests
./mvnw clean package -DskipTests
```

## Configuración de base de datos

PostgreSQL requerido antes de levantar la aplicación:
- Host: `localhost:5432`
- Base de datos: `almacen_db`
- Usuario: `postgres`

La configuración está en `src/main/resources/application.yaml`. Hibernate usa `ddl-auto: validate` — el schema debe existir previamente; no se crea automáticamente.

## Arquitectura

Cada módulo en `src/main/java/com/codigo2enter/almacenes/modules/<modulo>/` sigue la misma estructura en capas:

```
modules/<modulo>/
├── controller/   → Endpoints REST (@RestController)
├── dto/          → Objetos de transferencia de datos (request/response)
├── mapper/       → Conversión entre entidades y DTOs (MapStruct)
├── model/        → Entidades JPA (@Entity)
├── repository/   → Acceso a datos (Spring Data JPA)
└── service/      → Lógica de negocio
```

El paquete `core/` contiene configuración transversal:
- `core/exception/` — Manejo centralizado de excepciones
- `core/security/` — Configuración de Spring Security

## Dependencias clave

- **MapStruct 1.5.5** — Los mappers se generan en tiempo de compilación con anotaciones. Los procesadores de anotaciones están configurados en el `pom.xml` junto con Lombok; el orden de los `annotationProcessorPaths` importa (Lombok antes que MapStruct).
- **Lombok** — Usado en modelos y DTOs para reducir boilerplate (`@Data`, `@Builder`, `@NoArgsConstructor`, etc.)
- **Spring Security** — Configurado con cadena de filtros JWT stateless. Ver `core/security/SecurityConfig.java`.
- **JJWT 0.12.6** — Librería para generación y validación de tokens JWT (jjwt-api, jjwt-impl, jjwt-jackson).

## Estándares del módulo auth

### Seguridad y contraseñas
- Las contraseñas se cifran con **BCrypt** antes de persistirse. Nunca se almacenan en texto plano.
- El bean `PasswordEncoder` está declarado en `SecurityConfig` y se inyecta en los servicios que lo necesiten.
- El método `passwordEncoder.matches(plain, hash)` se usa para verificar credenciales en el login — BCrypt es unidireccional, nunca se desencripta.

### Tokens JWT
- Los tokens se generan en `core/security/JwtUtils.java` usando HMAC-SHA256.
- El login exitoso devuelve un `AuthResponseDTO` con el token JWT firmado, válido por **2 horas**.
- El token contiene los claims: `sub` (username), `roles`, `iat` (emisión) y `exp` (expiración).
- `JwtAuthenticationFilter` intercepta cada petición y valida el token antes de que llegue al controlador.
- Las rutas bajo `/api/v1/auth/**` son públicas (no requieren token). Todas las demás requieren `Authorization: Bearer <token>`.

### Inyección de dependencias en servicios
- Todos los servicios deben usar **inyección por constructor** declarando las dependencias como campos `final` y anotando la clase con `@RequiredArgsConstructor` de Lombok.
- No usar `@Autowired` en campos — dificulta las pruebas unitarias y oculta las dependencias reales.

```java
// Correcto
@Service
@RequiredArgsConstructor
public class MiServiceImpl implements MiService {
    private final MiRepository miRepository;
    private final JwtUtils jwtUtils;
}
```

### Transacciones
- Usar `@Transactional` a nivel de clase en los `ServiceImpl` para operaciones que escriben en la base de datos.
- Para métodos de solo lectura (consultas), usar `@Transactional(readOnly = true)` — Hibernate omite el flush y el pool puede enrutar a réplicas de lectura.

```java
@Transactional               // escritura (clase)
public UserResponseDTO registerUser(...) { ... }

@Transactional(readOnly = true)   // lectura (método)
public AuthResponseDTO login(...) { ... }
```

### Respuestas HTTP en controladores
- Los controladores deben retornar siempre `ResponseEntity<T>` usando el estilo fluido.
- Usar `ResponseEntity.status(HttpStatus.CREATED).body(...)` para creaciones (POST que persisten).
- Usar `ResponseEntity.ok(...)` para consultas y operaciones que no crean recursos nuevos.

```java
// Registro de recurso → 201 Created
return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerUser(request));

// Login / consulta → 200 OK
return ResponseEntity.ok(userService.login(request));
```

### Nota sobre el compilador del IDE
El compilador incremental de VS Code puede sobreescribir clases generadas por MapStruct con stubs inválidos. Para evitar este problema, el proyecto incluye `.vscode/settings.json` que excluye `target/` del escaneo del IDE. Siempre ejecutar la aplicación con:

```bash
./mvnw clean package -DskipTests && java -jar target/almacenes-0.0.1-SNAPSHOT.jar
```

### Documentar el funcionamiento de todo codigo que se genere
- Para todo codigo de programación que se genere, se deben agregar comentarios o Javadoc que informen el funcionamiento de este codigo.

## Estado actual del proyecto

El módulo `auth` está completamente implementado con:
- Entidades `User` y `Role` con relación `@ManyToMany`
- Repositorios `UserRepository` y `RoleRepository`
- Mapper `UserMapper` con MapStruct
- Servicio `UserServiceImpl` con registro y login
- Controlador `UserController` con endpoints `POST /register` y `POST /login`
- Filtro JWT `JwtAuthenticationFilter` integrado en la cadena de Spring Security
- Tests unitarios (`JwtUtilsTest`) y de integración (`UserControllerTest`)

Los módulos `inventory` y `purchases` están pendientes de implementación.

Rama activa de desarrollo: `feature/auth`
