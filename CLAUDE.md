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

# Ejecutar la aplicación (forma segura — evita interferencia del IDE)
./mvnw clean package -DskipTests && java -jar target/almacenes-0.0.1-SNAPSHOT.jar

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
└── service/      → Lógica de negocio (interfaz + implementación)
```

El paquete `core/` contiene configuración transversal:
- `core/exception/` — Manejo centralizado de excepciones
- `core/security/` — Configuración de Spring Security y utilidades JWT

## Dependencias clave

- **MapStruct 1.5.5** — Los mappers se generan en tiempo de compilación. El orden de los `annotationProcessorPaths` en `pom.xml` importa: Lombok debe ir antes que MapStruct.
- **Lombok** — Usado en modelos y DTOs para reducir boilerplate (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`).
- **Spring Security** — Configurado con cadena de filtros JWT stateless. Ver `core/security/SecurityConfig.java`.
- **JJWT 0.12.6** — Librería para generación y validación de tokens JWT (jjwt-api, jjwt-impl, jjwt-jackson).

---

## Estándares y convenciones globales

### Documentación de código
Todo código generado debe incluir comentarios o Javadoc que expliquen el **por qué** del funcionamiento, no solo el qué. Esto aplica a: clases, métodos no triviales, decisiones de diseño y comportamientos que podrían sorprender a un lector.

### Inyección de dependencias
Usar siempre **inyección por constructor** con campos `final` y `@RequiredArgsConstructor`. Nunca usar `@Autowired` en campos.

```java
@Service
@RequiredArgsConstructor
public class MiServiceImpl implements MiService {
    private final MiRepository miRepository;
    private final MiMapper miMapper;
}
```

### Transacciones
- `@Transactional` a nivel de clase en `ServiceImpl` para operaciones de escritura.
- `@Transactional(readOnly = true)` en métodos de solo lectura — Hibernate omite el flush y optimiza la sesión.

### Respuestas HTTP en controladores
- Retornar siempre `ResponseEntity<T>` con estilo fluido.
- `201 Created` para operaciones que persisten un recurso nuevo.
- `200 OK` para consultas y actualizaciones.
- `204 No Content` para operaciones exitosas sin cuerpo de respuesta (void del servicio).

```java
return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto)); // POST que persiste
return ResponseEntity.ok(service.findById(id));                              // GET / PUT
return ResponseEntity.noContent().build();                                   // DELETE / void
```

### Nota sobre el compilador del IDE
El compilador incremental de VS Code puede sobreescribir clases generadas por MapStruct con stubs inválidos. El proyecto incluye `.vscode/settings.json` que excluye `target/` del escaneo. Siempre ejecutar con:

```bash
./mvnw clean package -DskipTests && java -jar target/almacenes-0.0.1-SNAPSHOT.jar
```

---

## Estándares del módulo auth

### Seguridad y contraseñas
- Las contraseñas se cifran con **BCrypt** antes de persistirse. Nunca en texto plano.
- `passwordEncoder.matches(plain, hash)` verifica credenciales — BCrypt es unidireccional.

### Tokens JWT
- Los tokens se generan en `core/security/JwtUtils.java` con HMAC-SHA256, vigentes **2 horas**.
- Claims del payload: `sub` (username), `roles`, `iat`, `exp`.
- `JwtAuthenticationFilter` valida el token en cada petición antes de llegar al controlador.
- Rutas públicas: `/api/v1/auth/**`. Todo lo demás requiere `Authorization: Bearer <token>`.

---

## Estándares del módulo inventory

### Modelos JPA

- Todas las entidades usan `@Builder` con `@Builder.Default` para campos con valor inicial.
- `active` (boolean) implementa **soft delete** en todas las entidades — nunca se elimina un registro físicamente.
- `createdAt` siempre lleva `updatable = false` para que Hibernate nunca lo sobreescriba.
- Relaciones `@ManyToOne` usan `FetchType.LAZY` para evitar queries N+1 al listar colecciones.
- Enumerados como `MovementType` se almacenan con `@Enumerated(EnumType.STRING)` para que los registros históricos sean legibles sin traducción.

```java
@Builder.Default
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt = LocalDateTime.now();

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private Category category;
```

### Repositorios

- Query methods derivados para consultas simples: `findByActiveTrue()`, `findBySku()`, `existsBySku()`.
- `@Query` con JPQL solo cuando la condición compara dos campos de la misma entidad (no expresable con query methods):

```java
@Query("SELECT p FROM Product p WHERE p.currentStock <= p.minimumStock AND p.active = true")
List<Product> findLowStockProducts();
```

- Métodos de búsqueda filtrada incluyen `AndActiveTrue` para excluir registros dados de baja:

```java
List<Product> findByCategoryIdAndActiveTrue(Long categoryId);
```

### DTOs

- **DTOs de request** llevan validaciones Jakarta (`@NotBlank`, `@NotNull`, `@Min`, `@DecimalMin`, `@Size`).
- **DTOs de response** no llevan validaciones — son solo de salida.
- Relaciones `@ManyToOne` se **aplanan** en el DTO de respuesta con campos simples (`categoryId`, `categoryName`) en lugar de objetos anidados — Angular puede mostrar el nombre sin acceso profundo.
- Enums en DTOs de entrada se reciben como `String` para desacoplar el cliente del modelo Java; el servicio convierte con `Enum.valueOf()`.
- Regla `@NotBlank` vs `@NotNull`:
  - `String` obligatorio → `@NotBlank` (rechaza null, `""` y `"   "`)
  - `BigDecimal`, `Long`, `Integer` obligatorio → `@NotNull`
  - Primitivos (`int`, `boolean`) → no necesitan anotación (nunca son null)

### Mappers (MapStruct)

- Todos los mappers usan `@Mapper(componentModel = "spring")`.
- Campos ignorados en `toEntity`: `id`, `active`, `createdAt` y relaciones resueltas por el servicio.
- Relaciones `@ManyToOne` se mapean con `source` de múltiples niveles:

```java
@Mapping(source = "category.id",   target = "categoryId")
@Mapping(source = "category.name", target = "categoryName")
ProductResponseDTO toResponseDTO(Product product);
```

- Declarar siempre el método de lista (`toDTOList`, `toResponseDTOList`) para evitar streams en los servicios:

```java
List<ProductResponseDTO> toResponseDTOList(List<Product> products);
```

- `@Named` + `qualifiedByName` para conversiones no triviales que MapStruct no puede inferir automáticamente.

### Servicios

- Separar siempre **interfaz** (`XxxService`) de **implementación** (`XxxServiceImpl`).
- Métodos privados de apoyo (`findProductOrThrow`, `resolveCategory`) centralizan el manejo de `orElseThrow` para no repetirlo en cada método.

```java
private Product findProductOrThrow(Long id) {
    return productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Producto con id " + id + " no encontrado."));
}
```

- **Orden de validaciones en el servicio** (de más barata a más costosa):
  1. Validar tipos/formatos (conversión de enum, quantity > 0)
  2. Verificar existencia de entidades (`findById`, `existsById`)
  3. Validar reglas de negocio (unicidad de nombre/SKU, stock suficiente, productos activos)

- **Regla de visibilidad de errores**: para operaciones de autenticación, lanzar el mismo mensaje independientemente de qué falló (usuario no encontrado o contraseña incorrecta) — evita enumerar usuarios válidos.

- **Validación de existencia antes de consultar colecciones**: si un método retorna `List<>` filtrada por un ID (categoría, producto), verificar primero que ese ID exista. Sin esta validación, un ID inválido devuelve `[]` con HTTP 200, haciendo imposible que el cliente distinga "vacío" de "no encontrado".

- **Soft delete** sobre entidades con dependencias: verificar que no existan registros activos dependientes antes de desactivar. Ejemplo: no desactivar una `Category` si tiene `Product` activos asignados.

```java
if (!productRepository.findByCategoryIdAndActiveTrue(id).isEmpty()) {
    throw new RuntimeException("La categoría tiene productos activos asignados.");
}
```

- **Defensa en profundidad**: mantener validaciones en el servicio aunque el DTO ya las tenga con anotaciones Jakarta, para proteger invocaciones directas al servicio sin pasar por el controlador.

### Controladores

- Cero lógica de negocio — delegación pura al servicio.
- `@PathVariable` para IDs en la URL; `@RequestBody` con `@Valid` para datos del cliente.
- Rutas REST semánticas (sustantivos, no verbos):
  - `GET /products/low-stock` — colección filtrada
  - `GET /products/sku/{sku}` — búsqueda por atributo único
  - `GET /products/category/{categoryId}` — colección por relación
  - `GET /products/{id}/movements` — subrecurso jerárquico
  - `POST /products/movement` — acción sobre recurso
- `void` del servicio → `ResponseEntity<Void>` con `204 No Content`.

---

## Patrones de pruebas

### Tests unitarios de servicios
- `@ExtendWith(MockitoExtension.class)` — sin contexto Spring, instancia solo la clase bajo prueba.
- `@Mock` para dependencias, `@InjectMocks` para la clase bajo prueba.
- `@BeforeEach` reinicia los datos en cada test para garantizar independencia.
- Patrón AAA: **Arrange** → **Act** → **Assert**.
- Cubrir siempre: happy path + entidad no encontrada + reglas de negocio que lanzan excepción.
- Verificar con `verify(repo, never()).save(any())` que operaciones costosas no se ejecutan cuando la validación falla.

### Tests de integración de controladores
- `@WebMvcTest(XxxController.class)` + `@AutoConfigureMockMvc(addFilters = false)`.
- `@MockBean XxxService` para aislar la capa web.
- `@MockBean JwtUtils` siempre requerido — `SecurityConfig` lo necesita para construir `JwtAuthenticationFilter`.
- Verificar validaciones Jakarta con bodies inválidos → confirmar que `@Valid` está en el parámetro del controlador.
- `jsonPath("$.campo").value(...)` para verificar el body de respuesta.
- Métodos `void` del servicio no requieren `when/thenReturn` — Mockito los ignora por defecto.

---

## Estado actual del proyecto

### Módulo `auth` — completo
- Entidades: `User`, `Role` con relación `@ManyToMany`
- Repositorios: `UserRepository`, `RoleRepository`
- Mapper: `UserMapper`
- Servicio: `UserServiceImpl` (registro y login con JWT)
- Controlador: `UserController` — `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
- Seguridad: `JwtAuthenticationFilter`, `SecurityConfig`, `JwtUtils`
- Tests: `JwtUtilsTest` (3), `UserControllerTest` (2)

### Módulo `inventory` — completo
- Entidades: `Category`, `Product`, `StockMovement`, enum `MovementType`
- Repositorios: `CategoryRepository`, `ProductRepository`, `StockMovementRepository`
- DTOs: `CategoryDTO`, `ProductRequestDTO`, `ProductResponseDTO`, `StockMovementRequestDTO`, `StockMovementResponseDTO`
- Mappers: `CategoryMapper`, `ProductMapper`, `StockMovementMapper`
- Servicios: `CategoryServiceImpl`, `ProductServiceImpl`
- Controladores:
  - `CategoryController` — `POST /`, `GET /active`, `PUT /{id}`, `DELETE /{id}`
  - `ProductController` — `POST /`, `PUT /{id}`, `DELETE /{id}`, `GET /sku/{sku}`, `GET /category/{id}`, `GET /low-stock`, `POST /movement`, `GET /{id}/movements`
- Tests unitarios: `CategoryServiceImplTest` (9), `ProductServiceImplTest` (20)
- Tests de integración: `CategoryControllerTest` (6), `ProductControllerTest` (10)

### Módulo `purchases` — pendiente de implementación

### Suite de tests actual: 51 tests — 0 fallos

Rama activa de desarrollo: `feature/auth`
