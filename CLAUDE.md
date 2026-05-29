# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Descripción del proyecto

Backend REST API para gestión de almacenes. Proyecto Spring Boot 3.5.14 con Java 17, Maven y PostgreSQL.

**Módulos principales:**
- `auth` — Autenticación y autorización (JWT/Spring Security)
- `inventory` — Gestión de inventario (categorías, productos, movimientos de stock)
- `purchases` — Gestión de compras (proveedores, órdenes de compra)

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
- `core/exception/` — Manejo centralizado de excepciones
- `core/security/` — Configuración de Spring Security, utilidades JWT y CORS

## Dependencias clave

- **MapStruct 1.5.5** — Los mappers se generan en tiempo de compilación. El orden de los `annotationProcessorPaths` en `pom.xml` importa: Lombok debe ir antes que MapStruct.
- **Lombok** — Usado en modelos y DTOs para reducir boilerplate (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`).
- **Spring Security** — Configurado con cadena de filtros JWT stateless y CORS habilitado. Ver `core/security/SecurityConfig.java`.
- **JJWT 0.12.6** — Librería para generación y validación de tokens JWT (jjwt-api, jjwt-impl, jjwt-jackson).

---

## Estándares y convenciones globales

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
- Claims del payload: `sub` (username), `roles`, `iat`, `exp`.
- `JwtAuthenticationFilter` valida el token en cada petición antes de llegar al controlador.
- Rutas públicas: `/api/v1/auth/**`. Todo lo demás requiere `Authorization: Bearer <token>`.

### CORS

`SecurityConfig.java` expone un bean `CorsConfigurationSource` que permite todas las peticiones cross-origin durante desarrollo. En producción, reemplazar `allowedOriginPatterns(List.of("*"))` por el dominio del frontend.

**Por qué CORS va en SecurityConfig y no en `@CrossOrigin`**: el filtro CORS debe ejecutarse antes que el filtro JWT. Si CORS no se aplica primero, el preflight `OPTIONS` del browser llega sin `Authorization` header, Spring Security lo rechaza con 403, y el browser nunca envía la petición real.

**Por qué `allowedOriginPatterns("*")` y no `allowedOrigins("*")`**: cuando `allowCredentials = true` (necesario para que el frontend pueda enviar cookies de sesión en el futuro), la especificación CORS prohíbe usar el wildcard literal `"*"` en `allowedOrigins`. `allowedOriginPatterns("*")` es el equivalente seguro que sí funciona con credenciales.

---

## Columnas de auditoría

### Esquema de tablas y columnas presentes

| Tabla | `created_at` | `created_by` | `updated_at` | `updated_by` | `approved_by` | `received_by` | `cancelled_by` |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `users` | ✓ | — | — | — | — | — | — |
| `roles` | — | — | — | — | — | — | — |
| `user_roles` | — | — | — | — | — | — | — |
| `categories` | ✓ | ✓ | ✓ | ✓ | — | — | — |
| `products` | ✓ | ✓ | ✓ | ✓ | — | — | — |
| `stock_movements` | ✓ | ✓ | N/A | N/A | — | — | — |
| `suppliers` | ✓ | ✓ | ✓ | ✓ | — | — | — |
| `purchase_orders` | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| `purchase_order_details` | — | — | — | — | — | — | — |

**Decisiones de diseño:**
- `roles` y `user_roles`: catálogos/configuración estática, sin auditoría (pendiente cuando se implemente gestión dinámica de roles)
- `users.updated_by`: pendiente — depende de implementar el endpoint de actualización de usuarios
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

## Patrones de pruebas

### Tests unitarios de servicios

- `@ExtendWith(MockitoExtension.class)` — sin contexto Spring, instancia solo la clase bajo prueba.
- `@Mock` para dependencias, `@InjectMocks` para la clase bajo prueba.
- `@BeforeEach` reinicia los datos en cada test para garantizar independencia.
- Patrón AAA: **Arrange** → **Act** → **Assert**.
- Cubrir siempre: happy path + entidad no encontrada + reglas de negocio que lanzan excepción.
- Verificar con `verify(repo, never()).save(any())` que operaciones costosas no se ejecutan cuando la validación falla.

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

### Tests de integración de controladores

- `@WebMvcTest(XxxController.class)` + `@AutoConfigureMockMvc(addFilters = false)`.
- `@MockBean XxxService` para aislar la capa web.
- `@MockBean JwtUtils` siempre requerido — `SecurityConfig` lo necesita para construir `JwtAuthenticationFilter`.
- Verificar validaciones Jakarta con bodies inválidos → confirmar que `@Valid` está en el parámetro del controlador.
- `jsonPath("$.campo").value(...)` para verificar el body de respuesta.
- Métodos `void` del servicio no requieren `when/thenReturn` — Mockito los ignora por defecto.

### Limitaciones conocidas de los tests actuales y qué no detectan

Los tests unitarios (Mockito) y de controlador (`@WebMvcTest`) **no detectan**:

| Tipo de bug | Por qué los tests actuales no lo ven | Ejemplo de lo que pasó |
|---|---|---|
| Mapper sin `@Mapping(target="x", ignore=true)` para relación `@ManyToOne` | El mapper está mockeado — nunca corre el código MapStruct real | `supplier` llegaba null a Hibernate → NOT NULL constraint violation |
| Campo de respuesta sin `@Mapping(source="x.id", target="xId")` | `toResponseDTO()` está mockeado — el test devuelve lo que configuró | `supplierId` siempre null en respuesta |
| `updatable=false` en campo que empieza null | El repositorio está mockeado — nunca hay Hibernate ni SQL real | `approvedBy` nunca se persistía en BD |
| DB constraints (NOT NULL, UNIQUE, FK) | No hay BD real en los tests | Solo se detectan con curl o `@SpringBootTest` |

### Tests `@SpringBootTest` pendientes de implementar

Estos tests son **críticos** y deben agregarse para cubrir los gaps detectados. Usan el contexto completo de Spring + Hibernate + PostgreSQL (o H2 en modo test).

**Criterios de éxito que deben verificar:**

1. **Crear producto con proveedor**: el `INSERT` en `products` incluye `supplier_id` no nulo. Verificar que `GET /products/{id}` devuelve `supplierId` correcto en el response.

2. **Flujo completo de orden de compra**:
   - `POST /orders` → `POST /orders/{id}/details` → `PATCH /orders/{id}/approve` → `PATCH /orders/{id}/receive`
   - Verificar que `approvedById`, `receivedById` persisten en BD y aparecen en consultas posteriores
   - Verificar que el stock del producto se incrementa en `receive`
   - Verificar que aparece el movimiento en `GET /products/{id}/movements`

3. **Columnas de auditoría persistidas**:
   - Crear categoría → `GET /categories/active` → verificar `createdById != null`
   - Actualizar categoría → `GET /categories/active` → verificar `updatedById != null` y `updatedAt != null`

4. **Constraint NOT NULL en auditoría**: intentar crear un producto sin autenticación JWT → verificar `403`, no `500` (el campo `createdBy` no debe llegar null a Hibernate)

```java
// Estructura base de un @SpringBootTest de integración
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class ProductIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void crearProducto_conProveedorValido_debeGuardarSupplierId() {
        // ARRANGE: crear proveedor, obtener JWT, preparar request
        // ACT: POST /api/v1/inventory/products
        // ASSERT: status 201, supplierId en response != null,
        //         GET /products/{id} también devuelve supplierId != null
    }
}
```

---

## Estado actual del proyecto

### Módulo `auth` — completo

- Entidades: `User`, `Role` con relación `@ManyToMany`
- Repositorios: `UserRepository`, `RoleRepository`
- Mapper: `UserMapper`
- Servicio: `UserServiceImpl` (registro y login con JWT)
- Controlador: `UserController` — `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
- Seguridad: `JwtAuthenticationFilter`, `SecurityConfig` (con CORS habilitado), `JwtUtils`
- Tests: `JwtUtilsTest` (3), `UserControllerTest` (2)

### Módulo `inventory` — completo

- Entidades: `Category`, `Product`, `StockMovement`, enum `MovementType`
  - `Category` y `Product`: columnas de auditoría `created_at`, `created_by`, `updated_at`, `updated_by`
  - `StockMovement`: columnas `created_at`, `created_by` (inmutable por diseño)
  - `Product`: relación `@ManyToOne Supplier supplier` — el servicio la resuelve desde `dto.getSupplierId()` via `SupplierRepository`
- Repositorios: `CategoryRepository`, `ProductRepository`, `StockMovementRepository`
- DTOs: `CategoryDTO`, `ProductRequestDTO`, `ProductResponseDTO`, `StockMovementRequestDTO`, `StockMovementResponseDTO`
- Mappers: `CategoryMapper`, `ProductMapper` (con ignore de `supplier` en `toEntity`/`updateFromDTO` y mapping de `supplier.id` en `toResponseDTO`), `StockMovementMapper`
- Servicios: `CategoryServiceImpl`, `ProductServiceImpl` (con `SupplierRepository` inyectado)
- Controladores:
  - `CategoryController` — `POST /`, `GET /active`, `PUT /{id}`, `DELETE /{id}`
  - `ProductController` — `POST /`, `PUT /{id}`, `DELETE /{id}`, `GET /sku/{sku}`, `GET /category/{id}`, `GET /low-stock`, `POST /movement`, `GET /{id}/movements`
- Tests unitarios: `CategoryServiceImplTest` (9), `ProductServiceImplTest` (20, incluye mock de `SupplierRepository`)
- Tests de integración: `CategoryControllerTest` (6), `ProductControllerTest` (10)
- **Tests `@SpringBootTest` pendientes**: flujo completo crear producto, auditoría persistida

### Módulo `purchases` — completo

- Entidades: `Supplier`, `PurchaseOrder`, `PurchaseOrderDetail`, enum `PurchaseOrderStatus`
  - `Supplier`: columnas de auditoría `created_at`, `created_by`, `updated_at`, `updated_by`
  - `PurchaseOrder`: columnas `created_at`, `created_by`, `updated_at`, más `approved_by`, `received_by`, `cancelled_by` (sin `updatable=false`)
  - `PurchaseOrderDetail`: sin columnas de auditoría propias (hereda trazabilidad del padre)
- Repositorios: `SupplierRepository`, `PurchaseOrderRepository`, `PurchaseOrderDetailRepository`
- DTOs: `SupplierDTO`, `PurchaseOrderRequestDTO`, `PurchaseOrderUpdateRequestDTO`, `PurchaseOrderResponseDTO`, `PurchaseOrderDetailRequestDTO`, `PurchaseOrderDetailUpdateRequestDTO`, `PurchaseOrderDetailResponseDTO`
- Mappers: `SupplierMapper`, `PurchaseOrderMapper`, `PurchaseOrderDetailMapper`
- Servicios: `SupplierServiceImpl`, `PurchaseOrderServiceImpl`
- Controladores:
  - `SupplierController` — `POST /`, `GET /active`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`
  - `PurchaseOrderController` — `POST /`, `GET /{id}`, `GET /status/{status}`, `GET /supplier/{id}`, `GET /supplier/{id}/status/{status}`, `GET /product/{id}`, `PUT /{id}`, `PATCH /{id}/approve`, `PATCH /{id}/receive`, `PATCH /{id}/cancel`, `POST /{id}/details`, `PUT /{id}/details/{did}`, `DELETE /{id}/details/{did}`
- Tests unitarios: `SupplierServiceImplTest` (13), `PurchaseOrderServiceImplTest` (29)
- Tests de integración: `SupplierControllerTest` (7), `PurchaseOrderControllerTest` (18)
- **Tests `@SpringBootTest` pendientes**: flujo completo de orden (create→approve→receive con verificación de stock)

### Suite de tests actual: 118 tests — 0 fallos

Rama activa de desarrollo: `feature/auth`

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

### Regla general para prevenir estos bugs

Antes de dar por terminado cualquier nuevo endpoint, verificar con **curl real** (no solo tests unitarios):
1. El endpoint devuelve el código HTTP esperado
2. Todos los campos del response tienen valores correctos (no null inesperado)
3. Una consulta GET posterior devuelve los mismos datos (la BD persistió correctamente)
4. Los campos de auditoría (`createdById`, `updatedById`, etc.) tienen valor
