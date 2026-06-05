# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Descripción del proyecto

Backend REST API para gestión de almacenes. Proyecto Spring Boot 3.5.14 con Java 17, Maven y PostgreSQL.

**Módulos principales:**
- `auth` — Autenticación y autorización (JWT/Spring Security, RBAC con 4 roles)
- `inventory` — Gestión de inventario (categorías, productos, movimientos de stock)
- `purchases` — Gestión de compras (proveedores, órdenes de compra)
- `sales` — Gestión de ventas (clientes, órdenes de venta, reservas de stock, analítica de costo)
- `reports` — Reportes analíticos por audiencia (ejecutivos, gestión, operativos)

## Documentación obligatoria por módulo

Todo módulo nuevo — tanto en este backend como en el futuro frontend — requiere dos archivos en la raíz del repositorio antes de iniciar la implementación:

### `propuesta_modulo_<nombre>.txt`

Documento de planificación creado **antes de escribir código**. Contiene:
- Contexto y justificación (qué problema resuelve y por qué ahora)
- Análisis de audiencias y necesidades (quién lo usa y con qué frecuencia)
- Catálogo de funcionalidades/reportes/pantallas con especificación completa
- Disponibilidad de datos en el schema actual (sin nuevas tablas si es posible)
- Arquitectura técnica: paquetes, clases, decisiones de diseño
- Especificación de endpoints y control de acceso RBAC
- Plan de implementación por fases con entregables
- Plan de tests (tipos A/B/B*/C/D estimados)
- Estructura de la memoria técnica
- Tabla de riesgos con probabilidad y mitigación
- Criterios de éxito del módulo (checklist verificable)

### `memoria_tecnica_modulo_<nombre>.md`

Documento vivo creado en la **Fase 0** y actualizado al finalizar cada fase. Orientado a cualquier desarrollador externo que necesite entender el trabajo ejecutado. Incluye no solo qué se hizo, sino por qué, qué problemas se encontraron y cómo verificar que el módulo funciona.

Secciones obligatorias:

| Sección | Contenido | Se llena en |
|---|---|---|
| 1. Contexto y justificación | Problemática, usuarios beneficiados, relación con módulos anteriores | Fase 0 |
| 2. Decisiones de diseño | Por qué se eligió cada patrón (SRP, no MapStruct, readOnly, etc.) | Fase 0-1 |
| 3. Especificación de funcionalidades | Por cada reporte/endpoint: audiencia, fórmula, DTO, criterio de éxito, casos edge | Fase 1 |
| 4. Queries JPQL | Por cada query: JPQL, SQL equivalente, justificación, dependencias de dialecto | Fase 2 |
| 5. Algoritmos no triviales | Pseudocódigo de lógica compleja; manejo de división por cero, fechas, etc. | Fase 3 |
| 6. RBAC — criterio de acceso | Matriz endpoint × rol, justificación, reglas exactas de SecurityConfig | Fase 4 |
| 7. Ejecución de tests y resultados | Ver detalle abajo | Fase 5 |
| 8. Bugs y retos | Sección viva: síntoma, causa, por qué los mocks no lo detectaron, corrección | Fases 2-5 |
| 9. Estándares y buenas prácticas | Convenciones aplicadas específicas del módulo | Fase 6 |
| 10. Cumplimiento y validación | Checklist final de criterios de éxito | Fase 6 |

**Criterio de la Sección 7 — Ejecución de tests y resultados:**

Esta sección documenta evidencia verificable de que el módulo funciona. Debe incluir:

- **Por cada clase de test ejecutada:**
  - Nombre de la clase y tipo (A/B/B*/C/D)
  - Comando: `./mvnw test -Dtest=NombreClase`
  - Resultado exacto: `Tests run: X, Failures: 0, Errors: 0 — BUILD SUCCESS`
  - Si hubo fallos: síntoma, causa raíz, corrección aplicada

- **Suite consolidada:**
  - `./mvnw test` → `Tests run: X, Failures: 0 — BUILD SUCCESS`
  - Comparativa: X tests antes del módulo → X tests después

- **Cobertura JaCoCo:**
  - `./mvnw verify` → líneas X%, métodos X%, ramas X%
  - Referencia: `target/site/jacoco/index.html`
  - Si algún paquete no alcanza el 70%: justificación documentada

- **Validación E2E con curl** (por cada endpoint):
  - Comando curl ejecutado
  - Código HTTP recibido vs esperado
  - Campos del response verificados
  - Resultado: X/Y endpoints — 0 fallos

- **Regresiones en módulos anteriores:**
  - Suite pre-módulo: X/X — BUILD SUCCESS
  - Suite post-módulo: X/X — BUILD SUCCESS

### Convención de nombres y ubicación

```
# Raíz del repositorio (junto a CLAUDE.md)
propuesta_modulo_reports.txt
propuesta_modulo_reportes_frontend.txt   # cuando aplique al frontend
memoria_tecnica_modulo_reports.md
memoria_tecnica_modulo_reportes_frontend.md
```

La propuesta va en `.txt` (documento de planificación sin formato especial).
La memoria técnica va en `.md` (documento con formato, tablas y código).
Ambos archivos se commitean en la `feature/<nombre>` branch correspondiente.

---

## Memoria técnica global del proyecto

Para entender el sistema completo (decisiones arquitectónicas, contratos de
integración frontend↔backend, RBAC transversal, guía de configuración y roadmap)
consultar primero:

**`memoria_tecnica_global_proyecto.md`** — en la raíz de este repositorio

Se actualiza al finalizar cada módulo si hay nuevas decisiones transversales.

---

## ⚠️ Convenciones de Git — REGLAS CRÍTICAS

### NUNCA commitear directamente en `develop` o `main`

`develop` y `main` son ramas protegidas. **Solo reciben trabajo mediante merges
de feature/fix branches.** Un commit directo viola el historial y dificulta
la trazabilidad de qué cambios llegaron juntos.

**Flujo obligatorio para cualquier cambio:**

```bash
# 1. Crear branch desde develop
git checkout develop
git checkout -b feature/<nombre>   # o fix/<nombre> / chore/<nombre>

# 2. Trabajar y commitear en la branch
git add <archivos>
git commit -m "tipo(scope): descripción"

# 3. Merge a develop con commit de merge explícito
git checkout develop
git merge --no-ff feature/<nombre> -m "Merge branch 'feature/<nombre>' into develop"

# 4. Push
git push origin develop
git push origin feature/<nombre>
```

**Si ya commiteaste en `develop` por error:**
```bash
# Crear branch retroactivamente con los commits del error
git checkout -b fix/<nombre>
# Revertir develop al estado anterior (solo si no has hecho push)
git checkout develop
git reset --hard HEAD~<N>   # N = número de commits incorrectos
# Luego hacer el merge correcto de fix/<nombre>
```

### Hook local de protección

El repositorio incluye un hook pre-commit en `hooks/pre-commit` que bloquea
automáticamente cualquier intento de commitear directamente en `develop` o `main`.

**Instalación en una máquina nueva** (obligatorio al clonar el repo):
```bash
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

Una vez instalado, git rechaza el commit con un mensaje explicativo antes de
que ocurra, sin posibilidad de olvidar la regla.

### Convención de nombres de branches

| Prefijo | Cuándo usarlo |
|---|---|
| `feature/<nombre>` | Módulo nuevo o funcionalidad nueva |
| `fix/<nombre>` | Corrección de bug detectado después del merge |
| `chore/<nombre>` | Infraestructura, configuración, documentación |
| `hotfix/<nombre>` | Corrección urgente directamente desde `main` |

---

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

**Implicación crítica**: toda nueva columna en una entidad JPA debe existir primero en la BD antes de reiniciar la aplicación. El orden correcto siempre es: `ALTER TABLE` → editar la entidad → compilar. En sentido inverso, Hibernate lanza `SchemaValidationException` al arrancar.

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
- `core/config/` — Configuración de arranque (`DataInitializer` — crea el admin inicial)
- `core/exception/` — Manejo centralizado de excepciones
- `core/security/` — Configuración de Spring Security, utilidades JWT y CORS

## Dependencias clave

- **MapStruct 1.5.5** — Los mappers se generan en tiempo de compilación. El orden de los `annotationProcessorPaths` en `pom.xml` importa: Lombok debe ir antes que MapStruct.
- **Lombok** — Usado en modelos y DTOs para reducir boilerplate (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`).
- **Spring Security** — Configurado con cadena de filtros JWT stateless y CORS habilitado. Ver `core/security/SecurityConfig.java`.
- **JJWT 0.12.6** — Librería para generación y validación de tokens JWT (jjwt-api, jjwt-impl, jjwt-jackson).
- **springdoc-openapi 2.7.0** — Genera Swagger UI y especificación OpenAPI 3.0 automáticamente desde los controladores. Requiere versión ≥ 2.7.0 para compatibilidad con Spring Framework 6.2.x (versiones anteriores lanzan `NoSuchMethodError` en `ControllerAdviceBean`).

---

## Estándares y convenciones globales

### Swagger / OpenAPI — implementar desde el primer módulo

Swagger debe configurarse **antes de implementar el primer controlador**, no al final del proyecto. Agregarlo después obliga a actualizar todos los tests que verifican formatos de respuesta.

**Por qué desde el inicio**: el frontend consumirá la especificación OpenAPI generada por Swagger como contrato. Si se agrega tarde, el formato de respuesta de algunos endpoints puede cambiar (ej. paginación) y romper los tests existentes.

**Configuración mínima en `pom.xml`**:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.7.0</version>
</dependency>
```

**`core/config/OpenApiConfig.java`** — bean con esquema Bearer JWT global:
```java
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("API").version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"))
            .components(new Components().addSecuritySchemes("Bearer",
                new SecurityScheme().type(Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
```

**SecurityConfig** — agregar antes de las reglas de negocio:
```java
.requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                 "/v3/api-docs", "/v3/api-docs/**").permitAll()
```

**Cada controlador** lleva `@Tag(name = "Nombre", description = "...")` para agrupar endpoints en el UI. Los métodos `@Bean` van sin modificador `public` para evitar el warning `JAVA_PUBLIC_BEAN_METHOD` del plugin de VS Code.

**Acceso**: `http://localhost:8080/swagger-ui/index.html`

---

### Paginación — implementar desde el primer endpoint de colección

Todo endpoint `GET` que retorna una lista de entidades debe implementar paginación desde su creación. Añadirla después obliga a cambiar el formato de respuesta (de `List<T>` a `PageResponseDTO<T>`), lo que rompe los tests existentes y el contrato con el frontend.

**`core/dto/PageResponseDTO<T>`** — wrapper genérico (crear una vez, reutilizar en todos los módulos):
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PageResponseDTO<T> {
    private List<T> content;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private int size;
    private boolean first;
    private boolean last;

    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return PageResponseDTO.<T>builder()
            .content(page.getContent()).currentPage(page.getNumber())
            .totalPages(page.getTotalPages()).totalElements(page.getTotalElements())
            .size(page.getSize()).first(page.isFirst()).last(page.isLast()).build();
    }
}
```

**Patrón estándar** por capa:
```java
// Repositorio
Page<Product> findByActiveTrue(Pageable pageable);

// Servicio
public PageResponseDTO<ProductResponseDTO> getAllActive(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
    return PageResponseDTO.from(repository.findByActiveTrue(pageable)
        .map(mapper::toResponseDTO));
}

// Controlador
@GetMapping("/active")
public ResponseEntity<PageResponseDTO<ProductResponseDTO>> getAllActive(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(service.getAllActive(page, size));
}
```

**Qué NO paginar**: endpoints que retornan un único objeto (`/products/{id}`), subrecursos acotados por padre (detalles de una orden específica), o endpoints analíticos de reports (ya son datos agregados).

**Sort por defecto recomendado por entidad**:
- Usuarios, órdenes: `createdAt DESC`
- Productos, categorías, clientes, proveedores: `name ASC`
- Movimientos de stock: `createdAt DESC`

---

### Documentación de código

Todo código generado debe incluir comentarios o Javadoc que expliquen el **por qué** del funcionamiento, no solo el qué. Aplica a: clases, métodos no triviales, decisiones de diseño y comportamientos que podrían sorprender a un lector.

La documentación de cada método debe incluir:
- **Justificación**: por qué existe y qué problema resuelve
- **Flujo**: el orden lógico de operaciones cuando no es obvio
- **Criterios de éxito**: qué condiciones deben cumplirse para considerar la operación exitosa
- **Casos de borde**: qué sucede en escenarios límite o de error

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
- Claims del payload: `sub` (username), `roles` (lista de strings), `iat`, `exp`.
- `JwtAuthenticationFilter` valida el token y carga los roles como `SimpleGrantedAuthority` en cada petición.
- La clave secreta se lee de la variable de entorno `JWT_SECRET` vía `@Value("${jwt.secret}")` en `application.yaml`. En desarrollo usa un valor por defecto; en producción `JWT_SECRET` debe establecerse explícitamente.
- Ruta pública: `POST /api/v1/auth/login`. Todo lo demás requiere `Authorization: Bearer <token>`.

### RBAC — Roles y permisos

El sistema implementa 4 roles con acceso diferenciado por URL en `SecurityConfig.java`:

| Rol | Descripción |
|---|---|
| `ROLE_ADMIN` | Acceso total incluyendo gestión de usuarios |
| `ROLE_MANAGER` | Inventario, compras y ventas completos; sin gestión de usuarios |
| `ROLE_WAREHOUSEMAN` | Lectura de inventario, recepción de compras, entrega de ventas |
| `ROLE_SALES` | Crear y gestionar órdenes de venta; lectura de inventario |

`SecurityConfig.java` usa `@EnableMethodSecurity` y reglas `hasRole()` / `hasAnyRole()` por URL. El orden de las reglas importa: las más específicas van antes que las generales.

**DataInitializer**: `core/config/DataInitializer.java` crea el usuario `admin`/`Admin123!` con `ROLE_ADMIN` + `ROLE_WAREHOUSEMAN` al arrancar si la tabla `users` está vacía. Resuelve el problema chicken-and-egg de no tener un admin inicial sin registro público.

**Gestión de usuarios — solo ADMIN**:
- `POST /api/v1/auth/login` — público
- `GET/POST/PUT/DELETE /api/v1/auth/users/**` — solo `ROLE_ADMIN`
- `GET/PUT /api/v1/auth/me/**` — cualquier autenticado

**`users.updated_by` no implementado**: existe `updated_at` en la tabla pero no `updated_by`. La FK `users → users` crearía una referencia circular que complica la carga JPA. La inmutabilidad del auditor se garantiza a nivel de negocio.

### CORS

`SecurityConfig.java` expone un bean `CorsConfigurationSource` que permite todas las peticiones cross-origin durante desarrollo. En producción, reemplazar `allowedOriginPatterns(List.of("*"))` por el dominio del frontend.

**Por qué CORS va en SecurityConfig y no en `@CrossOrigin`**: el filtro CORS debe ejecutarse antes que el filtro JWT. Si CORS no se aplica primero, el preflight `OPTIONS` del browser llega sin `Authorization` header, Spring Security lo rechaza con 403, y el browser nunca envía la petición real.

**Por qué `allowedOriginPatterns("*")` y no `allowedOrigins("*")`**: cuando `allowCredentials = true` (necesario para que el frontend pueda enviar cookies de sesión en el futuro), la especificación CORS prohíbe usar el wildcard literal `"*"` en `allowedOrigins`. `allowedOriginPatterns("*")` es el equivalente seguro que sí funciona con credenciales.

---

## Columnas de auditoría

### Esquema de tablas y columnas presentes

| Tabla | `created_at` | `created_by` | `updated_at` | `updated_by` | `approved_by` | `received_by` | `cancelled_by` |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `users` | ✓ | — | ✓ | — | — | — | — |
| `roles` | — | — | — | — | — | — | — |
| `user_roles` | — | — | — | — | — | — | — |
| `categories` | ✓ | ✓ | ✓ | ✓ | — | — | — |
| `products` | ✓ | ✓ | ✓ | ✓ | — | — | — |
| `stock_movements` | ✓ | ✓ | N/A | N/A | — | — | — |
| `suppliers` | ✓ | ✓ | ✓ | ✓ | — | — | — |
| `purchase_orders` | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| `purchase_order_details` | — | — | — | — | — | — | — |

**Decisiones de diseño:**
- `roles` y `user_roles`: catálogos de configuración estática, sin auditoría
- `users.updated_by`: no implementado — FK `users → users` crearía referencia circular que complica la carga JPA. Solo se registra `updated_at`
- `purchase_order_details`: hereda la auditoría del padre (`purchase_orders`) — los detalles solo existen mientras la orden está en PENDING y el mismo usuario los gestiona
- `stock_movements`: inmutables por diseño (Kardex) — `updated_at`/`updated_by` no aplican

### Patrón de implementación en la entidad

```java
// Campos de creación — inmutables: updatable=false garantiza que Hibernate
// nunca los sobreescriba en un UPDATE posterior
@Builder.Default
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt = LocalDateTime.now();

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "created_by", updatable = false)
private User createdBy;

// Campos de modificación — mutables: sin updatable=false para que Hibernate
// los incluya en cada UPDATE. La inmutabilidad se garantiza a nivel de
// negocio (el servicio solo los escribe cuando hay cambios reales).
@Column(name = "updated_at")
private LocalDateTime updatedAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "updated_by")
private User updatedBy;
```

**Regla crítica — cuándo NO usar `updatable = false` en campos de auditoría**:
`updatable = false` solo es correcto para campos que se establecen en el INSERT y nunca cambian (e.g., `createdAt`, `createdBy`). Para campos que comienzan como NULL y se asignan en un UPDATE posterior (e.g., `approvedBy`, `receivedBy`, `cancelledBy` en órdenes de compra), `updatable = false` hace que Hibernate los excluya del UPDATE statement y el valor nunca se persiste en la BD. La inmutabilidad de esos campos se garantiza con lógica de negocio en el servicio, no con `updatable = false`.

### Resolución del usuario autenticado

Todos los servicios que escriben columnas de auditoría de usuario usan el mismo método privado:

```java
private User resolveAuthenticatedUser() {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException(
                    "Usuario autenticado no encontrado en el sistema."));
}
```

Este método requiere que `UserRepository` esté inyectado en el servicio. Los servicios que lo usan son: `CategoryServiceImpl`, `ProductServiceImpl`, `SupplierServiceImpl`, `PurchaseOrderServiceImpl`.

**Criterio de éxito**: el username extraído del JWT debe existir en la tabla `users`. Si el token es válido pero el usuario fue eliminado entre emisión y uso, el método lanza RuntimeException — comportamiento correcto para mantener integridad de auditoría.

---

## Estándares del módulo inventory

### Modelos JPA

- Todas las entidades usan `@Builder` con `@Builder.Default` para campos con valor inicial.
- `active` (boolean) implementa **soft delete** en todas las entidades — nunca se elimina un registro físicamente.
- `createdAt` siempre lleva `updatable = false` — el momento de alta es inmutable.
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
- **DTOs unificados** (`CategoryDTO`, `SupplierDTO`): se usan tanto como request como response. Los campos de auditoría son de solo salida — el cliente los envía como null en POST/PUT y el mapper los ignora en `toEntity()`. Este patrón es coherente con `id` y `active` que ya funcionaban igual.
- Regla `@NotBlank` vs `@NotNull`:
  - `String` obligatorio → `@NotBlank` (rechaza null, `""` y `"   "`)
  - `BigDecimal`, `Long`, `Integer` obligatorio → `@NotNull`
  - Primitivos (`int`, `boolean`) → no necesitan anotación (nunca son null)

### Mappers (MapStruct)

- Todos los mappers usan `@Mapper(componentModel = "spring")`.
- Campos ignorados en `toEntity`: `id`, `active`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, y todas las relaciones `@ManyToOne` resueltas por el servicio.
- Relaciones `@ManyToOne` se mapean con `source` de múltiples niveles:

```java
@Mapping(source = "category.id",        target = "categoryId")
@Mapping(source = "category.name",      target = "categoryName")
@Mapping(source = "supplier.id",        target = "supplierId")
@Mapping(source = "createdBy.id",       target = "createdById")
@Mapping(source = "createdBy.username", target = "createdByUsername")
@Mapping(source = "updatedBy.id",       target = "updatedById")
@Mapping(source = "updatedBy.username", target = "updatedByUsername")
ProductResponseDTO toResponseDTO(Product product);
```

**Regla crítica — siempre ignorar relaciones `@ManyToOne` en `toEntity()`**: si una entidad tiene `@ManyToOne Supplier supplier` y el DTO tiene `Long supplierId`, MapStruct no puede convertir automáticamente de Long a Supplier. Sin `@Mapping(target = "supplier", ignore = true)`, MapStruct intentará una conversión que falla silenciosamente (deja null), y Hibernate luego lanza una excepción de constraint NOT NULL al hacer el INSERT. El servicio es quien resuelve la relación via `repository.findById()`.

```java
// toEntity() siempre ignora relaciones y campos de auditoría:
@Mapping(target = "id",        ignore = true)
@Mapping(target = "active",    ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "updatedBy", ignore = true)
@Mapping(target = "category",  ignore = true)
@Mapping(target = "supplier",  ignore = true)
Product toEntity(ProductRequestDTO dto);
```

- Declarar siempre el método de lista (`toDTOList`, `toResponseDTOList`) para evitar streams en los servicios.
- `@Named` + `qualifiedByName` para conversiones no triviales que MapStruct no puede inferir automáticamente.
- MapStruct genera null-safe para relaciones lazy: si `product.getSupplier()` es null, el campo `supplierId` en el DTO queda null sin lanzar NPE.

### Servicios

- Separar siempre **interfaz** (`XxxService`) de **implementación** (`XxxServiceImpl`).
- Métodos privados de apoyo (`findProductOrThrow`, `resolveCategory`, `resolveSupplier`, `resolveAuthenticatedUser`) centralizan el manejo de `orElseThrow` para no repetirlo en cada método.
- `ProductServiceImpl` requiere `SupplierRepository` para resolver el proveedor desde el `supplierId` del DTO en `createProduct()` y `updateProduct()`. Sin esta resolución, `supplier` queda null y Hibernate falla con NOT NULL constraint.

```java
// Patrón de resolución de relaciones en createProduct():
Category category = resolveCategory(dto.getCategoryId());   // resolver relación 1
Supplier supplier = resolveSupplier(dto.getSupplierId());    // resolver relación 2
Product product = productMapper.toEntity(dto);               // mapper ignora las relaciones
product.setCategory(category);                               // servicio las asigna
product.setSupplier(supplier);
product.setCreatedBy(resolveAuthenticatedUser());            // auditoría
return productMapper.toResponseDTO(productRepository.save(product));
```

- **Orden de validaciones en el servicio** (de más barata a más costosa):
  1. Validar tipos/formatos (conversión de enum, quantity > 0)
  2. Verificar existencia de entidades (`findById`, `existsById`)
  3. Validar reglas de negocio (unicidad de nombre/SKU, stock suficiente, productos activos)

- **Regla de visibilidad de errores**: para operaciones de autenticación, lanzar el mismo mensaje independientemente de qué falló (usuario no encontrado o contraseña incorrecta) — evita enumerar usuarios válidos.

- **Validación de existencia antes de consultar colecciones**: si un método retorna `List<>` filtrada por un ID (categoría, producto), verificar primero que ese ID exista. Un ID inválido que devuelve `[]` con HTTP 200 es indistinguible de una entidad vacía.

- **Soft delete** sobre entidades con dependencias: verificar que no existan registros activos dependientes antes de desactivar.

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

## Estándares del módulo purchases

### Máquina de estados de PurchaseOrder

```
PENDING ──────────────────────────→ CANCELLED
  │                                    ↑
  ↓                                    │
APPROVED ─────────────────────────→ CANCELLED
  │
  ↓
RECEIVED (estado terminal — no cancelable)
```

**Transiciones válidas:**
- `PENDING → APPROVED`: vía `approveOrder()` — requiere al menos un detalle
- `APPROVED → RECEIVED`: vía `receiveOrder()` — genera movimientos de stock IN por cada detalle
- `PENDING → CANCELLED`: vía `cancelOrder()`
- `APPROVED → CANCELLED`: vía `cancelOrder()`
- `RECEIVED → cualquier`: **bloqueado** — estado terminal

**Criterios de éxito por transición:**
- `approve`: la orden pasa a APPROVED, se registra `approvedAt` y `approvedBy` (el usuario que aprobó)
- `receive`: la orden pasa a RECEIVED, se generan movimientos de stock IN por cada detalle en `stock_movements`, el `currentStock` de cada producto se incrementa
- `cancel`: la orden pasa a CANCELLED, se registra `cancelledAt` y `cancelledBy`

### Modelos JPA — purchases

- `PurchaseOrder.details` usa `cascade = CascadeType.ALL` + `orphanRemoval = true`: al eliminar un detalle de la lista, Hibernate lo borra físicamente (consistente con `ON DELETE CASCADE` en la BD).
- `approvedBy`, `receivedBy`, `cancelledBy` son `@ManyToOne` **sin** `updatable = false` — comienzan como null y se establecen en el UPDATE de cada transición. La inmutabilidad la garantiza la lógica de negocio del servicio, no Hibernate.
- `createdBy` en `PurchaseOrder` sí usa `updatable = false` — el creador de la orden nunca cambia.
- El campo `totalAmount` nunca lo envía el cliente — se calcula como suma de subtotales en el servicio.

### DTOs — purchases

- `PurchaseOrderResponseDTO` aplana tres relaciones `@ManyToOne`:
  - `supplier → supplierId + supplierName` (usa `supplier.companyName`, no `supplier.name`)
  - `createdBy → createdById + createdByUsername`
  - `approvedBy → approvedById + approvedByUsername`
  - `receivedBy → receivedById + receivedByUsername`
  - `cancelledBy → cancelledById + cancelledByUsername`
- Los campos de auditoría de transición (`approvedById`, `receivedById`, `cancelledById`) son null hasta que ocurre la transición correspondiente — el frontend debe tratarlos como opcionales.
- `PurchaseOrderDetailResponseDTO` incluye `productSku` además de `productId` y `productName` para que el frontend pueda mostrar el SKU sin petición adicional.

### Generación de número de orden

`generateOrderNumber()` produce formato `OC-YYYY-NNNN` con contador anual reiniciable:

```java
// Justificación: el formato OC-2026-0001 es legible en documentos físicos
// y permite ordenamiento cronológico. El contador reinicia cada año para
// que los números sean cortos (4 dígitos) dentro de cada ejercicio fiscal.
// El bucle do-while protege contra colisiones en alta concurrencia.
private String generateOrderNumber() {
    int year = Year.now().getValue();
    long count = purchaseOrderRepository.countByYear(year);
    String candidate;
    do {
        count++;
        candidate = "OC-" + year + "-" + String.format("%04d", count);
    } while (purchaseOrderRepository.findByOrderNumber(candidate).isPresent());
    return candidate;
}
```

### Validaciones de negocio — purchases

- **Supplier**: no se puede desactivar si tiene órdenes en PENDING o APPROVED
- **Detalle duplicado**: no se puede agregar el mismo producto dos veces a una orden (usar `updateDetail` para cambiar cantidad)
- **Detalles solo en PENDING**: `addDetail`, `updateDetail`, `removeDetail` fallan si la orden no está en PENDING
- **Aprobar con detalles**: no se puede aprobar una orden sin al menos un detalle

### Integración inventory ↔ purchases

El método `receiveOrder()` en `PurchaseOrderServiceImpl` llama a `productService.registerStockMovement()` por cada detalle. Esto ocurre dentro de la **misma transacción** (`propagation = REQUIRED` por defecto):

```java
// Si cualquier movimiento de stock falla, Hibernate hace rollback de toda
// la operación — la orden no pasa a RECEIVED y ningún stock se modifica.
for (PurchaseOrderDetail detail : order.getDetails()) {
    StockMovementRequestDTO movement = StockMovementRequestDTO.builder()
            .productId(detail.getProduct().getId())
            .quantity(detail.getQuantity())
            .type("IN")
            .reason("Recepción orden de compra " + order.getOrderNumber())
            .build();
    productService.registerStockMovement(movement);
}
```

**Criterio de éxito de receiveOrder()**: la orden tiene `status = RECEIVED`, cada producto del detalle tiene su `currentStock` incrementado, y cada movimiento aparece en `stock_movements` con `reason = "Recepción orden de compra OC-YYYY-NNNN"` y `createdBy` del usuario que recibió.

### Controladores — purchases

- `PurchaseOrderController` usa `@PatchMapping` para las transiciones de estado (no PUT) porque modifican parcialmente la orden sin recibir body con todos los campos.
- Rutas con doble filtro: `GET /orders/supplier/{supplierId}/status/{status}` permite al frontend filtrar eficientemente sin cargar toda la colección.
- `DELETE /orders/{id}/details/{detailId}` valida tanto que el detalle existe como que pertenece a esa orden (`findByIdAndPurchaseOrderId`) — previene accesos cruzados entre órdenes.

---

## Estándares del módulo sales

### Máquina de estados de SaleOrder

```
PENDING ──────────────────────────→ CANCELLED
  │                                    ↑
  ↓                                    │
APPROVED ─────────────────────────→ CANCELLED
  │
  ↓
DELIVERED (estado terminal positivo — no cancelable)
```

**Transiciones válidas:**
- `PENDING → APPROVED`: vía `approveOrder()` — valida available stock para todos los productos (Fase 1), luego reserva (Fase 2). El two-phase approach evita reservas parciales.
- `APPROVED → DELIVERED`: vía `deliverOrder()` — libera reserva + OUT en stock físico en la misma transacción.
- `PENDING → CANCELLED`: sin impacto en stock.
- `APPROVED → CANCELLED`: libera reservas (`reservedStock -= qty` por detalle).
- `DELIVERED → cualquier`: **bloqueado** — estado terminal.
- `CANCELLED → cualquier`: **bloqueado** — estado terminal.

**Criterios de éxito por transición:**
- `approve`: `reservedStock += qty`, `approvedAt`/`approvedBy` persistidos en BD, `currentStock` sin cambio.
- `deliver`: `reservedStock -= qty`, `currentStock -= qty` (via `registerStockMovement(OUT)`), `deliveredAt`/`deliveredBy` persistidos, movimiento OUT en Kardex con `reason = "Entrega orden de venta OV-YYYY-NNNN"`.
- `cancel desde APPROVED`: `reservedStock -= qty` por cada detalle, `cancelledAt`/`cancelledBy` persistidos.
- `cancel desde PENDING`: sin cambio en stock, `cancelledAt`/`cancelledBy` persistidos.

### Tres magnitudes de stock en Product

```
currentStock   = unidades físicas en el almacén (solo cambia con movimientos IN/OUT)
reservedStock  = comprometido con órdenes APPROVED no entregadas
availableStock = currentStock - reservedStock (calculado — no almacenado en BD)
```

**Regla crítica en deliverOrder()**: verificar `currentStock >= qty` (no `availableStock >= qty`). Cuando se entrega, la reserva de ESA orden ya está en `reservedStock` — usar `availableStock` causaría doble resta de las mismas unidades.

**Regla crítica en OUT manual** (registerStockMovement): verificar `availableStock >= qty` para proteger las unidades ya reservadas para órdenes APPROVED.

### Optimistic Locking en approveOrder()

`Product` tiene `@Version Long version` — Hibernate incrementa este campo en cada UPDATE. Si dos `approveOrder()` simultáneos intentan reservar el mismo producto:
- La primera transacción actualiza `version=0 → 1`.
- La segunda intenta actualizar con `version=0`, Hibernate detecta que la BD tiene `version=1` → lanza `ObjectOptimisticLockingFailureException`.

**Por qué `saveAndFlush()` en lugar de `save()`**: `save()` encola el UPDATE para el commit de la transacción. La excepción de versión se lanzaría fuera del try-catch de `approveOrder()`, llegando al `GlobalExceptionHandler` con el mensaje interno de Hibernate. `saveAndFlush()` fuerza el SQL inmediatamente, capturando la excepción dentro del try-catch y convirtiendo a mensaje de negocio claro.

```java
try {
    for (SaleOrderDetail detail : order.getDetails()) {
        product.setReservedStock(product.getReservedStock() + detail.getQuantity());
        productRepository.saveAndFlush(product); // flush inmediato → @Version verificado aquí
    }
} catch (ObjectOptimisticLockingFailureException e) {
    throw new RuntimeException("Stock modificado concurrentemente. Intente nuevamente.");
}
```

### Captura de unitCost para analítica financiera

`SaleOrderDetail.unitCost` captura `Product.unitCost` en el momento de crear o actualizar el detalle (mientras la orden está en PENDING). Una vez APPROVED, los detalles no son editables — el costo queda congelado implícitamente.

**Por qué el cliente nunca envía unitCost**: si el frontend pudiera enviarlo, podría falsificar el costo histórico. El servicio lo toma automáticamente de `Product.unitCost`.

**`unitCost` es NOT NULL**: `Product.unitCost` es obligatorio (`@NotNull` en DTO, `NOT NULL` en BD). La captura progresiva se eliminó antes de implementar el módulo `reports` para garantizar integridad en los cálculos de COGS.

**Fórmula de margen** (módulo `reports`):
```
margen_unitario = unitPrice - unitCost
margen_total    = margen_unitario × quantity
```

### Modelos JPA — sales

- `Client`: RFC y email opcionales (no todos los clientes son personas morales). Ambos tienen constraint UNIQUE cuando se proporcionan.
- `SaleOrder`: todos los campos de transición (`approvedBy`, `deliveredBy`, `cancelledBy`) **sin** `updatable=false` — misma regla que purchases. `createdBy` sí usa `updatable=false`.
- `SaleOrderDetail`: `unitCost` NOT NULL, sin `updatable=false` — el servicio lo re-lee del producto en cada actualización de detalle en PENDING. Una vez APPROVED, la inmovilidad la garantiza la lógica de negocio.
- `SaleOrder.details` usa `cascade = CascadeType.ALL` + `orphanRemoval = true` — eliminar un detalle de la lista lo borra físicamente.

### Repositorios — sales

Queries JPQL que no pueden expresarse con query methods derivados:

```java
// findActiveOrdersByClient: usa IN con FQN del enum — sintaxis necesaria
// para que el parser JPQL resuelva los literales sin ambigüedad
@Query("SELECT so FROM SaleOrder so WHERE so.client.id = :clientId " +
       "AND so.status IN (" +
       "com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.PENDING, " +
       "com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.APPROVED)")
List<SaleOrder> findActiveOrdersByClient(@Param("clientId") Long clientId);

// findByProductId: requiere JOIN a través de sale_order_details
@Query("SELECT DISTINCT so FROM SaleOrder so JOIN so.details d " +
       "WHERE d.product.id = :productId ORDER BY so.createdAt DESC")
List<SaleOrder> findByProductId(@Param("productId") Long productId);

// countByYear: YEAR() es función de dialecto — verificado en @DataJpaTest
// contra PostgreSQL real. Si el dialecto cambia, el test lo detecta.
@Query("SELECT COUNT(so) FROM SaleOrder so WHERE YEAR(so.createdAt) = :year")
long countByYear(@Param("year") int year);
```

### DTOs — sales

- **DTOs unificados no aplican aquí**: a diferencia de `CategoryDTO` y `SupplierDTO`, `SaleOrder` tiene flujos de request muy distintos (crear con detalles, actualizar solo notas/cliente), por lo que se usan DTOs separados.
- `SaleOrderDetailRequestDTO` y `SaleOrderDetailUpdateRequestDTO` **no incluyen `unitCost`** — lo gestiona el servicio automáticamente.
- `SaleOrderDetailResponseDTO` **sí incluye `unitCost`** — es dato de salida para el módulo financiero futuro.
- Los 5 DTOs de reservas (`ReservationSummaryDTO`, `ReservedProductDTO`, etc.) se construyen directamente en `ReservationServiceImpl` sin mappers — son vistas agregadas que cruzan varias entidades.

### Mappers — sales

- `SaleOrderMapper` usa `uses = {SaleOrderDetailMapper.class}` para delegar el mapeo de la lista de detalles.
- `SaleOrderDetailMapper.toEntity()` ignora `unitCost`, `subtotal`, `saleOrder` y `product` — el servicio los asigna.
- El campo `status` (enum `SaleOrderStatus`) se mapea a String en `toResponseDTO()` con un método `@Named`:

```java
@Named("statusToString")
default String statusToString(SaleOrderStatus status) {
    return status == null ? null : status.name();
}
```

### Servicios — sales

**ClientServiceImpl**:
- Valida unicidad de RFC y email (ambos opcionales pero únicos cuando se proporcionan).
- `deactivateClient()` bloquea si `findActiveOrdersByClient()` retorna resultados — mismo patrón que `SupplierServiceImpl` con órdenes activas.

**SaleOrderServiceImpl** — dependencias:
```
SaleOrderRepository, SaleOrderDetailRepository, ClientRepository,
ProductRepository, ProductService, UserRepository,
SaleOrderMapper, SaleOrderDetailMapper
```

Patrón de creación de orden con captura de `unitCost`:
```java
for (SaleOrderDetailRequestDTO detailDto : dto.getDetails()) {
    Product product = findActiveProductOrThrow(detailDto.getProductId());
    BigDecimal subtotal = detailDto.getUnitPrice()
            .multiply(BigDecimal.valueOf(detailDto.getQuantity()));
    SaleOrderDetail detail = saleOrderDetailMapper.toEntity(detailDto);
    detail.setProduct(product);
    detail.setSaleOrder(order);
    detail.setSubtotal(subtotal);
    detail.setUnitCost(product.getUnitCost()); // captura del costo histórico
    order.getDetails().add(detail);
}
```

**ReservationServiceImpl**: todos los métodos `@Transactional(readOnly=true)`. Sin `SecurityContextHolder`. Los DTOs se construyen directamente (sin mappers) cruzando `ProductRepository` y `SaleOrderRepository`.

### Controladores — sales

Rutas base:
- `/api/v1/sales/clients` — CRUD de clientes
- `/api/v1/sales/orders` — ciclo de vida de órdenes de venta
- `/api/v1/sales/reservations` — consulta de reservas activas (solo lectura)

`SaleOrderController` expone 14 endpoints incluyendo filtros combinados:
- `GET /product/{productId}/status/{status}` — órdenes que contienen un producto específico en un estado dado
- `GET /client/{clientId}/status/{status}` — filtro combinado cliente + estado

### Integración inventory ↔ sales

`deliverOrder()` en `SaleOrderServiceImpl` llama a `productService.registerStockMovement()` dentro de la misma transacción:
```java
// Liberar reserva PRIMERO, luego registrar movimiento OUT
product.setReservedStock(product.getReservedStock() - detail.getQuantity());
productRepository.save(product);
// registerStockMovement decrementa currentStock — usa availableStock (ya sin la reserva liberada)
productService.registerStockMovement(StockMovementRequestDTO.builder()
        .productId(product.getId())
        .quantity(detail.getQuantity())
        .type("OUT")
        .reason("Entrega orden de venta " + order.getOrderNumber())
        .build());
```

**Por qué liberar la reserva ANTES de registerStockMovement**: `registerStockMovement` valida `availableStock = currentStock - reservedStock >= qty`. Si la reserva no se libera primero, las unidades a entregar siguen contadas como reservadas, haciendo que `availableStock` parezca menor de lo que realmente es, y la validación fallaría incorrectamente.

### Columnas de auditoría — sales

| Tabla | `created_at` | `created_by` | `updated_at` | `updated_by` | `approved_by` | `delivered_by` | `cancelled_by` |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `clients` | ✓ | ✓ (NOT NULL) | ✓ | ✓ | — | — | — |
| `sale_orders` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `sale_order_details` | — | — | — | — | — | — | — |

**Nota sobre `clients.created_by`**: inicialmente el esquema SQL no tenía NOT NULL en esta columna — inconsistencia detectada durante la implementación. Se corrigió con `ALTER TABLE clients ALTER COLUMN created_by SET NOT NULL` antes de agregar la restricción en la entidad JPA.

---

## Patrones de pruebas

### Taxonomía de tests del proyecto

El proyecto usa cinco tipos de test automatizados (A–D en Maven) más tests E2E manuales:

| Tipo | Tecnología | BD | Spring Security | Velocidad | Detecta |
|---|---|---|---|---|---|
| **A** | `@ExtendWith(MockitoExtension.class)` | No | No | Muy rápida | Lógica de negocio |
| **B** | `@WebMvcTest` + `addFilters=false` | No | No (deshabilitado) | Rápida | HTTP status, @Valid, JSON |
| **B\*** | `@WebMvcTest` + `@Import(SecurityConfig)` | No | **Sí (activo)** | Rápida | Spring Security, 403s, JWT |
| **C** | `@SpringBootTest(RANDOM_PORT)` | **PostgreSQL real** | **Sí** | Lenta | Hibernate, FK, auditoría en BD |
| **D** | `@DataJpaTest` + `replace=NONE` | **PostgreSQL real** | No | Media | JPQL queries, constraints BD |
| **E** | Scripts curl (fuera de Maven) | **PostgreSQL real** | **Sí** | Manual | Flujo completo de negocio |

**Regla**: B* sustituye a B cuando se necesita verificar que Spring Security no bloquea rutas públicas o sí bloquea rutas protegidas. Todos los `@WebMvcTest` existentes mantienen `addFilters=false` para aislar la capa web; `SecurityFilterTest` es la única clase B* que verifica seguridad.

### Tests unitarios de servicios (Tipo A)

- `@ExtendWith(MockitoExtension.class)` — sin contexto Spring, instancia solo la clase bajo prueba.
- `@Mock` para dependencias, `@InjectMocks` para la clase bajo prueba.
- `@BeforeEach` reinicia los datos en cada test para garantizar independencia.
- Patrón AAA: **Arrange** → **Act** → **Assert**.
- Cubrir siempre: happy path + entidad no encontrada + reglas de negocio que lanzan excepción.
- `verify(repo, never()).save(any())` — verifica que operaciones costosas no se ejecutan cuando la validación falla.

**Regla crítica — dirty-checking vs save()**: los servicios `@Transactional` que NO llaman `save()` explícitamente (usan dirty-checking de Hibernate) deben verificarse con aserciones sobre el estado de la entidad, NO con `verify(repository.save(...))`. En tests Mockito no hay Hibernate real — `save()` nunca se llama aunque el comportamiento en producción sea correcto.

```java
// INCORRECTO para servicios con dirty-checking:
verify(supplierRepository).save(entity); // falla porque save() no se llama

// CORRECTO:
assertDoesNotThrow(() -> supplierService.updateSupplier(1L, dto));
assertNotNull(entity.getUpdatedAt()); // el servicio sí llama setUpdatedAt()
assertEquals(user, entity.getUpdatedBy());
```

**Servicios con `SecurityContextHolder` (categorías, productos, proveedores, órdenes)**:

```java
@BeforeEach
void setUp() {
    // lenient() porque los tests de consulta no invocan resolveAuthenticatedUser()
    Authentication auth = mock(Authentication.class);
    lenient().when(auth.getName()).thenReturn("operador01");
    SecurityContext ctx = mock(SecurityContext.class);
    lenient().when(ctx.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(ctx);

    user = User.builder().id(1L).username("operador01").password("hashed").build();
    lenient().when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));
}

@AfterEach
void tearDown() {
    // Obligatorio: SecurityContextHolder es estado estático global.
    // Sin limpiar puede filtrarse entre tests y producir resultados no deterministas.
    SecurityContextHolder.clearContext();
}
```

**Servicios con relaciones que deben resolverse (ProductServiceImpl)**:

```java
// ProductServiceImpl resuelve category, supplier y usuario.
// Todos los stubs deben ser lenient() para que los tests de solo lectura
// no fallen por stubs no utilizados (UnnecessaryStubbingException).
supplier = Supplier.builder().id(1L).rfc("FERN123456").companyName("Ferretería SA").active(true).build();
lenient().when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
```

### Tests de integración de controladores (Tipo B)

- `@WebMvcTest(XxxController.class)` + `@AutoConfigureMockMvc(addFilters = false)`.
- `@MockBean XxxService` para aislar la capa web.
- `@MockBean JwtUtils` siempre requerido — `SecurityConfig` lo necesita para construir `JwtAuthenticationFilter`.
- Verificar validaciones Jakarta con bodies inválidos → confirmar que `@Valid` está en el parámetro del controlador.
- `jsonPath("$.campo").value(...)` para verificar el body de respuesta.
- Métodos `void` del servicio no requieren `when/thenReturn` — Mockito los ignora por defecto.

### Tests de seguridad (Tipo B*)

`SecurityFilterTest` verifica que Spring Security aplica correctamente las reglas de autorización. Se diferencia de los Tipo B en que los filtros están ACTIVOS:

```java
@WebMvcTest({UserController.class, CategoryController.class, /* todos los controllers */})
@Import(SecurityConfig.class)
// SIN @AutoConfigureMockMvc(addFilters = false)
class SecurityFilterTest {
    @MockBean JwtUtils jwtUtils; // controla qué tokens se consideran válidos

    // autenticarConRol() mockea extractUsername, validateToken Y extractRoles —
    // los tres son llamados por JwtAuthenticationFilter tras la refactorización RBAC
    private void autenticarConRol(String... roles) {
        when(jwtUtils.extractUsername(TOKEN)).thenReturn("tester01");
        when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtils.extractRoles(TOKEN)).thenReturn(List.of(roles));
    }
}
```

Cubre cinco bloques:
1. Rutas públicas (`/auth/login`) accesibles sin JWT.
2. Rutas protegidas sin JWT → 403.
3. Rutas protegidas con JWT válido → no 403.
4. Token con firma manipulada o expirado → 403.
5. Reglas RBAC: MANAGER/WAREHOUSEMAN/SALES → 403 en rutas de solo ADMIN.

**Regla**: la aserción para "no debe ser 403" usa un `ResultMatcher` personalizado porque MockMvc no tiene `status().isNot(403)`:
```java
.andExpect(result -> assertNotEquals(HttpStatus.FORBIDDEN.value(),
    result.getResponse().getStatus(), "mensaje explicativo"))
```

### Tests de repositorio (Tipo D)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional // rollback automático después de cada test
class ProductRepositoryTest { ... }
```

- `replace=NONE` es **obligatorio** — el esquema usa características de PostgreSQL (IDENTITY, NUMERIC, CHECK) que H2 no soporta. Sin esta anotación, Spring Boot intenta configurar H2 y falla con SchemaValidationException.
- `@Transactional` heredado de `@DataJpaTest` hace rollback automático — los datos de prueba no persisten entre tests ni contaminan la BD de desarrollo.
- Solo carga la capa JPA — sin Spring MVC, sin servicios, sin controladores.
- Setup en `@BeforeEach`: crear el grafo mínimo de entidades (User → Category → Supplier → Product → Client) con sufijos únicos basados en `System.currentTimeMillis()` para evitar colisiones entre ejecuciones.

### Tests @SpringBootTest con BD real (Tipo C)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditAndConstraintIntegrationTest {
    @Autowired TestRestTemplate restTemplate;
}
```

- Levanta el contexto completo en un puerto aleatorio — sin conflicto con la app corriendo en 8080.
- `@TestMethodOrder` + `@Order(n)` para tests que comparten estado (IDs de entidades creadas).
- Tests que usan entidades propias (no comparten estado) no necesitan `@Order`.
- `TestRestTemplate` envía requests HTTP reales — Spring Security activo, Hibernate activo, PostgreSQL real.
- **Patrón de doble verificación**: verificar el campo tanto en la respuesta inmediata (memoria) como en un GET posterior (BD persistida). La respuesta inmediata puede reflejar el valor en memoria antes del rollback/commit; el GET posterior confirma que llegó a la BD.

```java
// Verificación en memoria (inmediata):
assertNotNull(approveResp.getBody().get("approvedById"));

// Verificación en BD (GET posterior):
ResponseEntity<Map> getAfterApprove = restTemplate.exchange(
    base + "/purchases/orders/" + id, HttpMethod.GET, ...);
assertNotNull(getAfterApprove.getBody().get("approvedById"),
    "approvedById debe persistir en BD — verifica updatable=false");
```

### Qué detecta cada tipo de test

| Tipo de bug | A (Mockito) | B (@WebMVC) | B* (Seguridad) | C (@SpringBootTest) | D (@DataJpaTest) |
|---|---|---|---|---|---|
| Lógica de negocio del servicio | ✓ | ~ | ~ | ✓ | ✗ |
| Validaciones Jakarta (@Valid) | ✗ | ✓ | ✓ | ✓ | ✗ |
| Spring Security (403, JWT) | ✗ | ✗ | ✓ | ✓ | ✗ |
| MapStruct: mapeos faltantes | ✗ | ✗ | ✗ | ✓ | ✗ |
| Hibernate: updatable=false incorrecto | ✗ | ✗ | ✗ | ✓ | ✗ |
| FK constraints (NOT NULL en BD) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Queries JPQL (sintaxis, dialecto) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Auditoría persistida en BD real | ✗ | ✗ | ✗ | ✓ | ✗ |
| Concurrencia / Optimistic Locking | ✗ | ✗ | ✗ | ✓ | ✗ |

### JaCoCo — cobertura de código

Plugin configurado en `pom.xml`. Genera reporte en `target/site/jacoco/index.html` al ejecutar `./mvnw test`.

```bash
# Ver reporte en el navegador (macOS)
open target/site/jacoco/index.html

# Ejecutar y verificar umbral (fase verify)
./mvnw verify
```

**Umbrales configurados**: 70% de cobertura de líneas por paquete (excluyendo paquetes `dto`, `model`, `mapper` — son código auto-generado por MapStruct o solo campos sin lógica).

**Métricas actuales** (365 tests — backend completo):
- Líneas: 84.6% ✓ (umbral: 70%)
- Métodos: 87.5% ✓
- Ramas: 61.6% ~ (área de mejora — sin umbral configurado aún)

**Regla de exclusión en JaCoCo**: los paquetes `*.mapper` contienen solo interfaces — las implementaciones (`*MapperImpl`) son auto-generadas por MapStruct en `target/generated-sources`. JaCoCo los instrumenta pero los tests Mockito los mockean, resultando en 0% de cobertura. Estos paquetes están excluidos del check para evitar falsos negativos.

### Tests de seguridad — JwtUtils

`JwtUtils` usa `@Value("${jwt.secret}")` para leer la clave desde `application.yaml`. Como `JwtUtilsTest` es un test unitario puro (sin Spring context), `@Value` no se inyecta automáticamente. Se usa `ReflectionTestUtils.setField()` para simular la inyección:

```java
private static final String TEST_SECRET =
        "4a8f3b2e9c1d7f6a0b5e2c8d4f1a9b3e7c0d6f2a5b8e3c1d9f4a7b0e2c6d8f1";

@BeforeEach
void setUp() {
    jwtUtils = new JwtUtils();
    ReflectionTestUtils.setField(jwtUtils, "secret", TEST_SECRET);
}
```

**Test de token expirado**: construir un token JJWT con `expiration` en el pasado usando `TEST_SECRET`. Tener la clave de desarrollo en el archivo de test es aceptable — los archivos de test no se despliegan a producción y la clave real de producción se inyecta vía variable de entorno `JWT_SECRET`.

```java
SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
String expiredToken = Jwts.builder()
        .subject("usuario")
        .expiration(new Date(System.currentTimeMillis() - 1_000))
        .signWith(key)
        .compact();
assertFalse(jwtUtils.validateToken(expiredToken));
```

---

## Estado actual del proyecto

### Módulo `auth` — completo (incluye RBAC)

- Entidades: `User` (con `updated_at`), `Role` con relación `@ManyToMany`
- Repositorios: `UserRepository` (con `findByActiveTrue`, `findByUsernameAndIdNot`, `findByEmailAndIdNot`), `RoleRepository`
- Mapper: `UserMapper` (con `toResponseDTOList`)
- DTOs: `AuthRequestDTO`, `AuthResponseDTO`, `UserCreateDTO`, `UserUpdateDTO`, `UserRoleAssignDTO`, `ChangePasswordDTO`, `UserResponseDTO`
- Servicio: `UserServiceImpl` — login, `getAllUsers`, `getUserById`, `createUser`, `updateUser`, `deactivateUser`, `assignRoles`, `getMyProfile`, `changePassword`
- Controlador: `UserController` — 9 endpoints organizados en 3 grupos (público / ADMIN / autenticado)
- Seguridad:
  - `JwtAuthenticationFilter` — carga roles como `SimpleGrantedAuthority`; try-catch para tokens malformados
  - `SecurityConfig` — `@EnableMethodSecurity`; 34 reglas RBAC por URL para los 4 módulos
  - `JwtUtils` — `generateToken`, `validateToken`, `extractUsername`, `extractRoles`; clave vía `@Value("${jwt.secret}")`
  - `DataInitializer` — crea `admin`/`Admin123!` al arrancar si la tabla `users` está vacía
- 4 roles en BD: `ROLE_ADMIN`, `ROLE_WAREHOUSEMAN`, `ROLE_MANAGER`, `ROLE_SALES`
- Tests (Tipo A): `JwtUtilsTest` (4), `UserServiceImplTest` (20)
- Tests (Tipo B): `UserControllerTest` (12)
- Tests (Tipo B*): `SecurityFilterTest` (45 — verifica 403s, rutas públicas, reglas RBAC por rol y reglas RBAC del módulo reports)
- Tests (Tipo C): `RbacIntegrationTest` (17 — tokens JWT reales, flujo completo MANAGER, firma manipulada)

### Módulo `inventory` — completo

- Entidades: `Category`, `Product`, `StockMovement`, enum `MovementType`
  - `Product`: `reservedStock` (int, default 0), `@Version Long version` (Optimistic Locking), `unitCost` (BigDecimal, **NOT NULL**)
  - Todos con columnas de auditoría `created_at`/`created_by`; `updated_at`/`updated_by` donde aplica
- Repositorios: `CategoryRepository`, `ProductRepository` (con `findLowStockProducts` usando `availableStock`), `StockMovementRepository`
- DTOs: `CategoryDTO`, `ProductRequestDTO` (con `unitCost` obligatorio — `@NotNull`), `ProductResponseDTO` (con `reservedStock`, `availableStock`, `unitCost`), `StockMovementRequestDTO`, `StockMovementResponseDTO`
- Mappers: `CategoryMapper`, `ProductMapper` (con `calcAvailableStock @Named`, ignores de `reservedStock`/`version` en `toEntity`)
- Servicios: `CategoryServiceImpl`, `ProductServiceImpl` (validación OUT contra `availableStock`)
- Controladores:
  - `CategoryController` — `POST /`, `GET /active`, `PUT /{id}`, `DELETE /{id}`
  - `ProductController` — `POST /`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`, `GET /sku/{sku}`, `GET /category/{id}`, `GET /low-stock`, `POST /movement`, `GET /{id}/movements`
- Tests (Tipo A): `CategoryServiceImplTest` (9), `ProductServiceImplTest` (20)
- Tests (Tipo B): `CategoryControllerTest` (6), `ProductControllerTest` (10)
- Tests (Tipo D): `ProductRepositoryTest` (4 — `findLowStockProducts` con `availableStock`, `findProductsWithActiveReservations`)

### Módulo `purchases` — completo

- Entidades: `Supplier`, `PurchaseOrder`, `PurchaseOrderDetail`, enum `PurchaseOrderStatus`
  - `PurchaseOrder`: `approved_by`, `received_by`, `cancelled_by` sin `updatable=false`
- Repositorios: `SupplierRepository`, `PurchaseOrderRepository`, `PurchaseOrderDetailRepository`
- DTOs: 7 DTOs (request, update, response para orden y detalles; SupplierDTO unificado)
- Mappers: `SupplierMapper`, `PurchaseOrderMapper`, `PurchaseOrderDetailMapper`
- Servicios: `SupplierServiceImpl`, `PurchaseOrderServiceImpl`
- Controladores: `SupplierController` (5 endpoints), `PurchaseOrderController` (13 endpoints)
- Tests (Tipo A): `SupplierServiceImplTest` (14 — incluye RFC propio permitido), `PurchaseOrderServiceImplTest` (29)
- Tests (Tipo B): `SupplierControllerTest` (7), `PurchaseOrderControllerTest` (18)

### Módulo `sales` — completo

- Entidades: `Client`, `SaleOrder`, `SaleOrderDetail`, enum `SaleOrderStatus`
  - `SaleOrder`: `approved_by`, `delivered_by`, `cancelled_by` sin `updatable=false`
  - `SaleOrderDetail`: `unitCost` **NOT NULL** (capturado de `Product.unitCost` al crear el detalle)
- Repositorios: `ClientRepository`, `SaleOrderRepository` (con queries JPQL: `findActiveOrdersByClient`, `findByProductId`, `countByYear`), `SaleOrderDetailRepository`
- DTOs: 12 DTOs incluyendo 5 DTOs de reservas (`ReservationSummaryDTO`, `ReservedProductDTO`, `ReservedProductOrderDTO`, `ReservedClientDTO`, `ReservedClientOrderDTO`)
- Mappers: `ClientMapper`, `SaleOrderDetailMapper`, `SaleOrderMapper` (usa `SaleOrderDetailMapper`)
- Servicios: `ClientServiceImpl`, `SaleOrderServiceImpl` (con `saveAndFlush` en `approveOrder`), `ReservationServiceImpl` (readOnly, sin mappers)
- Controladores:
  - `ClientController` — 5 endpoints (`/api/v1/sales/clients`)
  - `SaleOrderController` — 14 endpoints (`/api/v1/sales/orders`)
  - `ReservationController` — 5 endpoints GET (`/api/v1/sales/reservations`)
- Tests (Tipo A): `ClientServiceImplTest` (11), `SaleOrderServiceImplTest` (24), `ReservationServiceImplTest` (12)
- Tests (Tipo B): `ClientControllerTest` (6), `SaleOrderControllerTest` (14), `ReservationControllerTest` (5)
- Tests (Tipo C): `SaleOrderConcurrencyTest` (3 — Optimistic Locking con threads reales)
- Tests (Tipo D): `SaleOrderRepositoryTest` (5 — `countByYear`, `findActiveOrdersByClient`, `findByProductId`)

### Módulo `reports` — completo

- Sin entidades JPA propias — módulo de solo lectura que agrega datos de otros módulos
- Sin mappers MapStruct — DTOs construidos directamente en servicios con builders
- 3 servicios `@Transactional(readOnly=true)` por audiencia: `ExecutiveReportServiceImpl`, `ManagementReportServiceImpl`, `OperationalReportServiceImpl`
- 1 controlador: `ReportController` — 12 endpoints GET en `/api/v1/reports/**`
- 15 DTOs en 3 subpaquetes: `executive/` (4), `management/` (5), `operational/` (6)
- 18 queries JPQL analíticas agregadas a repositorios existentes (sin nuevas tablas)
- Bug detectado: `FUNCTION('TO_CHAR', ..., :format)` con GROUP BY falla en PostgreSQL — resuelto con `nativeQuery = true` y subquery (detectado por `ReportRepositoryTest` Tipo D)
- Queries nuevas en repositorios:
  - `SaleOrderDetailRepository`: 6 (revenue, COGS, quantitySold, revenueByProduct, cogsByProduct, countDelivered)
  - `ProductRepository`: 2 (inventoryValueByCategory, totalInventoryValue)
  - `StockMovementRepository`: 3 (findByProductAndPeriod, sumIn, sumOut)
  - `PurchaseOrderRepository`: 3 (countPendingAndApproved, totalsBySupplier, findPendingAndApproved)
  - `SaleOrderRepository`: 3 (countPendingAndApproved, revenueByPeriod native, findPendingAndApproved)
- Tests (Tipo A): `ExecutiveReportServiceImplTest` (11), `ManagementReportServiceImplTest` (17), `OperationalReportServiceImplTest` (12)
- Tests (Tipo B): `ReportControllerTest` (14)
- Tests (Tipo D): `ReportRepositoryTest` (7 — queries JPQL y native contra PostgreSQL real)

### Tests de integración transversales

- `AuditAndConstraintIntegrationTest` (8 tests, Tipo C): verifica en BD real que los bugs históricos no regresan:
  1. Sin JWT → 403 (Spring Security intercepta antes que Hibernate)
  2. `supplierId` persiste en BD al crear producto (Bug 1)
  3. `createdBy`/`updatedBy` persisten en categorías
  4. `approvedBy`/`receivedBy` persisten en orden de compra (Bug 3)
  5. `unitCost` capturado y congelado en orden de venta
  6. Ciclo completo PENDING→APPROVED→DELIVERED con `reservedStock`/`currentStock`
  7. Cancelación de orden de venta desde APPROVED: `reservedStock` liberado
  8. Cancelación de orden de compra desde APPROVED: `cancelledBy` persistido

- `RbacIntegrationTest` (17 tests, Tipo C): verifica reglas RBAC con tokens JWT reales:
  1. Admin crea usuarios MANAGER, WAREHOUSEMAN, SALES y obtiene JWT reales (sin mock de JwtUtils)
  2. Accesos denegados (403) por rol insuficiente en /auth/users, inventory write, purchases approve, sales approve, clients delete
  3. Accesos permitidos: lectura de inventario para WAREHOUSEMAN y SALES; /auth/me para todos
  4. Flujo completo MANAGER: crea categoría, producto, orden de compra y la aprueba — verifica en BD que `approvedByUsername` es el usuario MANAGER
  5. Token con firma manipulada → 403 (verifica try-catch real del filtro JWT)

### Suite de tests actual: 365 tests — 0 fallos

```
Tipo A (Mockito):            171 tests
Tipo B (@WebMvcTest):         82 tests
Tipo B* (con seguridad):      45 tests  (+12 RBAC reports)
Tipo C (@SpringBootTest):     51 tests
Tipo D (@DataJpaTest):        16 tests
──────────────────────────────────────
TOTAL MAVEN:                 365 tests
Tests E2E curl:              129 tests  (fuera del pipeline Maven)
```

Cobertura JaCoCo: **84.6% líneas · 87.5% métodos · 61.6% ramas**

Nota: `core.config` (DataInitializer) excluido del check JaCoCo — bootstrap code.

Estado: **backend completo — listo para desarrollo del frontend**

---

## Lecciones aprendidas — bugs detectados en pruebas E2E

Durante las pruebas con curl (end-to-end) se descubrieron bugs que los tests unitarios con mocks no detectaron. Se documentan aquí como guía para evitar patrones similares.

### Bug 1: relación `@ManyToOne` no resuelta en el servicio

**Síntoma**: `ERROR: null value in column "supplier_id" violates not-null constraint`

**Causa**: `ProductServiceImpl.createProduct()` no tenía `SupplierRepository` inyectado y no llamaba a `product.setSupplier(resolveSupplier(dto.getSupplierId()))`. El mapper ignora correctamente `supplier` en `toEntity()`, pero el servicio tampoco lo resolvía.

**Por qué los tests no lo detectaron**: el mapper y el repositorio estaban mockeados. El mock de `productMapper.toEntity()` devuelve el objeto `product` del test (que tenía todos los campos), y `productRepository.save()` lo acepta sin validar constraints de BD.

**Corrección**: añadir `SupplierRepository` al servicio y llamar `resolveSupplier()` explícitamente. Patrón: **toda relación `@ManyToOne` que viene del DTO como `Long xxxId` debe ser resuelta por el servicio antes de `save()`**.

### Bug 2: campo de respuesta null por `@Mapping` faltante

**Síntoma**: `supplierId: null` en el response de producto aunque el proveedor sí estaba guardado en BD.

**Causa**: `ProductMapper.toResponseDTO()` tenía mappings para `category.id → categoryId` y `category.name → categoryName`, pero faltaba `supplier.id → supplierId`.

**Corrección**: añadir `@Mapping(source = "supplier.id", target = "supplierId")`. Patrón: **al agregar una nueva relación `@ManyToOne` a una entidad, siempre revisar que `toResponseDTO()` tenga el mapping de aplanado correspondiente**.

### Bug 3: `updatable = false` impide persistir valores que comienzan null

**Síntoma**: `approvedById: null` en consultas posteriores a la aprobación, aunque en la respuesta inmediata de `approve` sí aparecía el valor.

**Causa**: `PurchaseOrder.approvedBy` tenía `updatable = false`. La orden se crea con `approved_by = NULL`. Cuando `approveOrder()` llama `order.setApprovedBy(user)` y Hibernate hace el UPDATE, excluye `approved_by` del statement (por `updatable = false`). El valor en memoria es correcto pero nunca llega a la BD. La siguiente consulta carga desde BD donde sigue siendo NULL.

**Por qué los tests no lo detectaron**: el repositorio está mockeado. `when(purchaseOrderRepository.findById(...)).thenReturn(Optional.of(order))` devuelve el mismo objeto en memoria donde `approvedBy` ya estaba seteado. No hay Hibernate real ni BD.

**Corrección**: eliminar `updatable = false` de `approvedBy`, `receivedBy`, `cancelledBy`. La inmutabilidad se garantiza con lógica de negocio. Patrón: **`updatable = false` solo para campos que se escriben en el INSERT y nunca cambian (como `created_at`, `created_by`). Campos que comienzan null y se asignan en un UPDATE posterior NO deben tener `updatable = false`**.

### Bug 4: JwtAuthenticationFilter sin manejo de excepciones

**Síntoma**: un cliente con token malformado provoca comportamiento indefinido del servidor (500 o error de Tomcat) en lugar de 403.

**Causa**: `JwtAuthenticationFilter.doFilterInternal()` llamaba a `jwtUtils.extractUsername(token)` sin try-catch. Si el token estaba malformado, la excepción propagaba por la cadena de filtros.

**Por qué los tests no lo detectaron**: todos los tests de controlador usaban `addFilters=false` — el filtro nunca se ejecutaba. Solo `SecurityFilterTest` (Tipo B*) lo detectó porque activa los filtros reales.

**Corrección**: envolver `extractUsername()` en try-catch; si lanza, llamar `filterChain.doFilter()` y `return` — la request pasa sin autenticar y Spring Security devuelve 403 para rutas protegidas.

```java
String username;
try {
    username = jwtUtils.extractUsername(token);
} catch (Exception e) {
    filterChain.doFilter(request, response);
    return;
}
```

### Bug 5: `extractRoles()` no mockeado en SecurityFilterTest tras refactorización RBAC

**Síntoma**: NPE en `JwtAuthenticationFilter` al ejecutar `SecurityFilterTest` después de agregar `extractRoles()` al filtro.

**Causa**: el helper `autenticar()` mockeaba `extractUsername` y `validateToken` pero no `extractRoles`. Mockito retorna null por defecto para métodos no configurados — el filtro intentaba iterar sobre null.

**Corrección**: `autenticarConRol(String... roles)` mockea los tres métodos. Toda prueba que simule un usuario autenticado debe incluir `extractRoles`.

### Bug 6: `unitCost` nullable — inconsistencia de integridad para módulo reports

**Síntoma** (detectado en análisis, no en producción): los cálculos de COGS en el módulo `reports` requerirían `WHERE unitCost IS NOT NULL` y un campo `cogsCompleteness` para indicar qué porcentaje del revenue tiene costo definido.

**Causa**: `Product.unitCost` y `SaleOrderDetail.unitCost` eran nullable por diseño de "captura progresiva". El módulo sales ya garantizaba que toda orden creada con el flujo normal tenía unitCost definido, pero sin NOT NULL en BD no había garantía formal.

**Por qué los tests no lo detectaron**: los mocks devuelven el objeto configurado en `@BeforeEach` — ningún test insertaba un producto sin unitCost en la BD real y luego creaba una orden de venta.

**Corrección**: `ALTER TABLE products ALTER COLUMN unit_cost SET NOT NULL`, `ALTER TABLE sale_order_details ALTER COLUMN unit_cost SET NOT NULL`, `@NotNull` en `ProductRequestDTO.unitCost`. El módulo `reports` puede calcular COGS sin filtros especiales.

### Bug 7: save() vs saveAndFlush() con Optimistic Locking

**Síntoma**: `ObjectOptimisticLockingFailureException` se lanzaba con el mensaje interno de Hibernate ("Row was updated or deleted by another transaction") en lugar del mensaje de negocio "Stock modificado concurrentemente."

**Causa**: `save()` encola el UPDATE para el commit de la transacción. El try-catch alrededor de `save()` en `approveOrder()` nunca capturaba la excepción porque ocurría fuera de su alcance (al cerrar la transacción). El `SaleOrderConcurrencyTest` (Tipo C) lo detectó.

**Corrección**: cambiar a `saveAndFlush()` para forzar el SQL inmediatamente dentro del try-catch.

### Bug 8: Regla SecurityConfig demasiado permisiva para un rol

**Síntoma** (detectado en E2E del módulo reports): WAREHOUSEMAN recibía HTTP 200 en
`/reports/inventory/valuation`, `/reports/inventory/abc` y `/reports/inventory/turnover`,
cuando debería recibir 403. Estos son reportes analíticos/financieros reservados para ADMIN
y MANAGER según el diseño.

**Causa**: la regla general `.requestMatchers(HttpMethod.GET, "/api/v1/reports/inventory/**")
.hasAnyRole("ADMIN","MANAGER","WAREHOUSEMAN")` era demasiado amplia. Cubría tanto los
endpoints operativos (low-stock, kardex, movements — correctos para WAREHOUSEMAN) como los
analíticos (valuation, abc, turnover — incorrectos para WAREHOUSEMAN).

**Por qué los tests no lo detectaron**: `ReportControllerTest` usa `@WebMvcTest` con
`addFilters=false` — Spring Security está completamente deshabilitado. Los tests de servicio
(Tipo A) y repositorio (Tipo D) tampoco verifican reglas de URL. Solo la validación E2E con
tokens JWT reales y la aplicación corriendo lo expuso.

**Corrección**: agregar regla específica para `/reports/inventory/movements` con WAREHOUSEMAN
ANTES de la regla general, y cambiar la general `/reports/inventory/**` a solo ADMIN+MANAGER.
El orden de las reglas en SecurityConfig es crítico — la más específica siempre antes de la
más general.

**Lección**: las reglas de autorización por URL en SecurityConfig deben verificarse con
validación E2E (curl real) O con tests Tipo B* (`@WebMvcTest` + `@Import(SecurityConfig.class)`
sin `addFilters=false`). Los tests Tipo B estándar con `addFilters=false` nunca detectarán
este tipo de error.

### Regla general para prevenir bugs de integración

Ante cualquier nuevo endpoint, verificar con **curl real** O con **@SpringBootTest** antes de dar por terminado:
1. El endpoint devuelve el código HTTP esperado.
2. Todos los campos del response tienen valores correctos (no null inesperado).
3. Un GET posterior devuelve los mismos datos (la BD persistió correctamente).
4. Los campos de auditoría (`createdById`, `updatedById`, `approvedById`, etc.) tienen valor.
5. Los campos calculados (`availableStock`, `totalAmount`) son coherentes con los datos de BD.

Los @SpringBootTest de `AuditAndConstraintIntegrationTest` automatizan exactamente estas verificaciones para los flujos críticos — no son necesarias manualmente si el test ya las cubre.
