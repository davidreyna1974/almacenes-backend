# CLAUDE_TEMPLATE.md

Archivo patrón para nuevos proyectos de desarrollo de software.
Generado a partir de los estándares, buenas prácticas y convenciones
consolidadas en el proyecto **Sistema de Gestión de Almacenes** (backend Spring Boot).

**Cómo usar este archivo:**
1. Copiar este archivo al nuevo proyecto como `CLAUDE.md`
2. Reemplazar todos los valores entre `[CORCHETES]` con los datos del proyecto
3. Eliminar las secciones del bloque técnico que no apliquen al stack
4. Completar las secciones específicas del stack con las dependencias y
   convenciones propias del proyecto

**Nota de mantenimiento:**
Si en el proyecto de almacenes se agrega información relevante al `CLAUDE.md`
que por su funcionalidad o importancia sea aplicable a cualquier proyecto,
debe agregarse también a este archivo en su momento.

---

## ÍNDICE

**BLOQUE UNIVERSAL** — aplica a cualquier proyecto, independiente del stack

- U1.  Estructura del proyecto
- U2.  Documentación obligatoria por módulo
- U3.  Convenciones de Git
- U4.  Estándares globales de código
- U5.  Patrones de base de datos
- U6.  Patrones de seguridad (Auth, RBAC)
- U7.  Taxonomía de tests
- U8.  Patrones de tests
- U9.  Documentación de bugs (Lecciones aprendidas)
- U10. Protocolo pre-código — Verificación de contratos API (proyectos cliente-servidor)

**BLOQUE TÉCNICO** — específico del stack [STACK]; reemplazar por equivalente

- T1.  Descripción del proyecto
- T2.  Comandos comunes
- T3.  Configuración de base de datos
- T4.  Dependencias clave
- T5.  Estándares técnicos del stack
- T6.  Estándares de módulos implementados
- T7.  Estado actual del proyecto

---

# ═══════════════════════════════════════════════════════════════
# BLOQUE UNIVERSAL
# ═══════════════════════════════════════════════════════════════

---

## U1. Estructura del proyecto

Todo proyecto se organiza en módulos de negocio. Cada módulo sigue la
misma estructura en capas, independientemente del stack tecnológico:

```
modules/<modulo>/
├── controller/   → Endpoints / Handlers (entrada HTTP)
├── dto/          → Objetos de transferencia (request / response)
├── mapper/       → Conversión entre entidades y DTOs
├── model/        → Entidades de dominio (JPA, Mongoose, SQLAlchemy, etc.)
├── repository/   → Acceso a datos (queries, ORM)
└── service/      → Lógica de negocio (interfaz + implementación)
```

Un paquete `core/` (o `shared/`, `common/`) contiene configuración transversal:
- `core/config/` — Inicialización de datos, configuración de arranque
- `core/exception/` — Manejo centralizado de errores
- `core/security/` — Autenticación, autorización, filtros

**Principio**: cero lógica de negocio en los controladores. El controlador
recibe la petición, valida el formato y delega al servicio. El servicio es
el único que conoce las reglas de negocio.

---

## U2. Documentación obligatoria por módulo

Todo módulo nuevo — tanto en el backend como en el frontend — requiere
dos archivos en la raíz del repositorio antes de iniciar la implementación.
Ambos se commitean en la `feature/<nombre>` branch correspondiente.

### `propuesta_modulo_<nombre>.txt`

Documento de planificación creado **antes de escribir código**. Contiene:

1. **Contexto y justificación** — qué problema resuelve y por qué ahora
2. **Análisis de audiencias y necesidades** — quién lo usa, con qué frecuencia,
   qué pregunta responde cada funcionalidad
3. **Catálogo de funcionalidades** — especificación completa de cada
   endpoint / reporte / pantalla: datos fuente, campos de respuesta,
   parámetros, acceso por rol, criterio de éxito, casos edge
4. **Disponibilidad de datos** — confirmar que los datos necesarios existen
   sin nuevas tablas; si se requieren, documentar el `ALTER TABLE` previo
5. **Arquitectura técnica** — paquetes, clases, decisiones de diseño
   con justificación (por qué este patrón y no otro)
6. **Especificación de endpoints y control de acceso** — rutas, métodos HTTP,
   matriz de acceso por rol, reglas exactas de seguridad
7. **Plan de implementación por fases** — entregable y actualización de
   memoria técnica al finalizar cada fase
8. **Plan de tests** — tipos estimados (A/B/B*/C/D), tests por clase,
   casos edge obligatorios
9. **Estructura de la memoria técnica** — las 10 secciones con su criterio
   de llenado por fase
10. **Riesgos conocidos** — tabla con probabilidad y mitigación antes de empezar
11. **Criterios de éxito** — checklist verificable (funcionalidad, seguridad,
    tests, documentación, calidad)

### `memoria_tecnica_modulo_<nombre>.md`

Documento vivo creado en la **Fase 0** y actualizado al finalizar cada fase.
Orientado a cualquier desarrollador externo. Incluye no solo qué se hizo,
sino por qué, qué problemas se encontraron y cómo verificar que funciona.

| Sección | Contenido | Se llena en |
|---|---|---|
| 1. Contexto y justificación | Problemática, usuarios, relación con módulos anteriores | Fase 0 |
| 2. Decisiones de diseño | Por qué cada patrón (SRP, arquitectura, etc.) | Fase 0-1 |
| 3. Especificación de funcionalidades | Por cada endpoint: audiencia, fórmula, DTO, criterio de éxito, casos edge | Fase 1 |
| 4. Queries / acceso a datos | Por cada query: sintaxis, SQL equivalente, justificación, limitaciones | Fase 2 |
| 5. Algoritmos no triviales | Pseudocódigo de lógica compleja; manejo de división por cero, fechas, estados | Fase 3 |
| 6. RBAC — criterio de acceso | Matriz endpoint × rol, justificación, reglas exactas de configuración | Fase 4 |
| 7. Ejecución de tests y resultados | Ver criterio detallado abajo | Fase 5 |
| 8. Bugs y retos | Sección viva: síntoma, causa, corrección, lección para futuros módulos | Fases 2-5 |
| 9. Estándares y buenas prácticas | Convenciones aplicadas específicas del módulo | Fase 6 |
| 10. Cumplimiento y validación | Checklist final de criterios de éxito | Fase 6 |

**Criterio detallado de la Sección 7 — Ejecución de tests y resultados:**

Esta sección documenta evidencia verificable de que el módulo funciona.
Debe incluir:

```
Por cada clase de test ejecutada:
  - Nombre de la clase y tipo (A/B/B*/C/D)
  - Comando: [TEST_COMMAND] -Dtest=NombreClase (o equivalente del stack)
  - Resultado: Tests: X passed — BUILD SUCCESS (o equivalente)
  - Si hubo fallos: síntoma exacto, causa raíz, corrección aplicada

Suite consolidada:
  - [TEST_COMMAND] completo → Tests: X passed, 0 failed — BUILD SUCCESS
  - Comparativa: X tests antes del módulo → X tests después

Cobertura de código:
  - [COVERAGE_COMMAND] → líneas X%, funciones X%, ramas X%
  - Si algún paquete no alcanza el umbral: justificación documentada

Validación E2E (por cada endpoint):
  - Comando ejecutado (curl / Postman / script)
  - Código HTTP recibido vs esperado
  - Campos del response verificados
  - Resultado: X/Y endpoints — 0 fallos

Regresiones en módulos anteriores:
  - Suite pre-módulo: X/X — BUILD SUCCESS
  - Suite post-módulo: X/X — BUILD SUCCESS
  - Diferencia: +X tests, 0 fallos nuevos
```

### Convención de nombres y ubicación

```
# Raíz del repositorio (junto a CLAUDE.md)
propuesta_modulo_[nombre].txt          # backend
propuesta_modulo_[nombre]_frontend.txt # frontend
memoria_tecnica_modulo_[nombre].md
memoria_tecnica_modulo_[nombre]_frontend.md
```

- Propuesta → `.txt` (documento de planificación, sin formato especial)
- Memoria técnica → `.md` (con formato, tablas y bloques de código)

---

## U3. Convenciones de Git

### Estructura de branches

```
main       → código de producción (solo recibe desde develop vía release)
develop    → integración; solo recibe merges desde feature branches
feature/[nombre] → desarrollo de un módulo o funcionalidad
hotfix/[nombre]  → correcciones urgentes que van directo a main y develop
chore/[nombre]   → tareas de infraestructura, configuración, documentación
```

**REGLA CRÍTICA — nunca commitear directamente en `develop` o `main`.**
Todo trabajo va en una `feature/` o `fix/` branch y llega a `develop`
exclusivamente vía `git merge --no-ff`. Un commit directo viola el historial
y dificulta saber qué cambios llegaron juntos.

**Protección automatizada**: incluir un hook pre-commit en `hooks/pre-commit`
que bloquee automáticamente commits en ramas protegidas:
```bash
#!/bin/sh
BRANCH=$(git branch --show-current)
for protected in develop main; do
  if [ "$BRANCH" = "$protected" ]; then
    echo "✗ COMMIT BLOQUEADO: '$BRANCH' está protegida. Usa una feature branch."
    exit 1
  fi
done
```
Instalación al clonar: `cp hooks/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit`

### Merge strategy

```bash
git merge --no-ff feature/[nombre]
```

`--no-ff` preserva el historial de la branch en el grafo de commits.
El merge commit documenta qué funcionalidad entró y cuándo.

### Commits

- Un commit por unidad de trabajo coherente (no uno por línea cambiada,
  no un mega-commit con todo el módulo)
- Mensaje en formato: `tipo(scope): descripción en infinitivo`
  - `feat(inventory): agregar endpoint de kardex por producto`
  - `fix(auth): capturar JwtException en filtro para tokens malformados`
  - `docs(reports): actualizar memoria técnica con resultados de tests`
  - `chore(security): externalizar JWT_SECRET a variable de entorno`
- El cuerpo del commit explica el **por qué**, no el qué (el diff ya muestra el qué)

### Archivos de propuesta y documentación

Los archivos `propuesta_modulo_*.txt` y `memoria_tecnica_modulo_*.md`
se commitean en la `feature/` branch del módulo correspondiente.
No deben commitearse directamente en `develop`.

### Secretos y credenciales

**Nunca** commitear contraseñas, API keys, tokens ni secretos en el código.
Usar variables de entorno con valores por defecto para desarrollo local:

```yaml
# application.yaml — patrón
jwt.secret: ${JWT_SECRET:clave-solo-para-desarrollo-local}
datasource.password: ${DB_PASSWORD:password-local}
```

En producción, las variables de entorno reales anulan los defaults.
Si un secreto llega al historial de git, debe considerarse comprometido
y rotarse de inmediato aunque se elimine del código.

---

## U4. Estándares globales de código

### Swagger / OpenAPI — implementar desde el primer módulo

Swagger debe configurarse **antes del primer controlador**. Agregarlo después obliga
a actualizar los tests que verifican formatos de respuesta y puede romper el contrato
con el frontend si el formato de algunos endpoints cambia (ej. al agregar paginación).

Configuración mínima para cualquier stack REST:
- Agregar la dependencia de Swagger/OpenAPI al gestor de dependencias
- Crear un bean de configuración con el esquema de autenticación JWT Bearer global
- Permitir las rutas de Swagger en la configuración de seguridad sin autenticación
- Agregar `@Tag(name, description)` a cada controlador desde su creación

**Acceso típico**: `http://localhost:[PUERTO]/swagger-ui/index.html`

**Nota de compatibilidad (Spring Boot)**: usar springdoc-openapi ≥ 2.7.0 para
compatibilidad con Spring Framework 6.2.x. Versiones anteriores lanzan
`NoSuchMethodError` en `ControllerAdviceBean`.

### Paginación — implementar desde el primer endpoint de colección

Todo endpoint GET que retorna una lista debe implementar paginación desde su creación.
Añadirla después cambia el formato de respuesta (de array `[...]` a objeto paginado
`{"content": [...], "totalPages": ..., ...}`), rompiendo los tests existentes y el
contrato con el frontend.

**Wrapper genérico de respuesta paginada** (crear una vez en `core/dto/`):
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

**Patrón estándar por capa**:
```
Repositorio  → Page<Entity> findByActiveTrue(Pageable pageable)
Servicio     → PageResponseDTO<DTO> getAll(int page, int size) usando PageRequest.of()
Controlador  → @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size
```

**Qué NO paginar**: endpoint de un solo objeto por ID, subrecursos acotados por
padre (detalles de una orden), endpoints analíticos ya agregados (reports).

**Sort por defecto recomendado**: colecciones de negocio → `name ASC`;
historial/auditoría → `createdAt DESC`.

---

### Documentación de código

Todo código generado debe incluir comentarios o documentación que expliquen
el **por qué** del funcionamiento, no el qué (el código mismo ya explica el qué).
Aplica a: clases, métodos no triviales, decisiones de diseño y comportamientos
que podrían sorprender a un lector.

La documentación de cada método no trivial incluye:
- **Justificación** — por qué existe y qué problema resuelve
- **Flujo** — orden lógico de operaciones cuando no es obvio
- **Criterio de éxito** — qué condiciones deben cumplirse
- **Casos edge** — qué sucede en escenarios límite o de error

**No documentar**: qué hace el código cuando los nombres ya lo expresan.
Un comentario que solo repite lo que el nombre dice es ruido, no documentación.

### Inyección de dependencias

Usar siempre **inyección por constructor** con campos inmutables.
Nunca inyección por campo con anotaciones directas (`@Autowired`, `@Inject`).

```java
// Java/Spring — patrón correcto
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;  // final = inmutable
}

// TypeScript/NestJS — equivalente
@Injectable()
export class ProductService {
    constructor(private readonly productRepository: ProductRepository) {}
}
```

**Por qué**: la inyección por constructor hace las dependencias explícitas,
permite instanciar la clase en tests sin framework, y hace imposible usar
el objeto en estado inválido (sin dependencias).

### Transacciones

- Operaciones de escritura: marcar la unidad de trabajo como transaccional
- Operaciones de solo lectura: indicarlo explícitamente para que el ORM
  optimice (omite flush, usa cursores de solo lectura, etc.)

### Respuestas HTTP en controladores/handlers

| Situación | Código | Cuándo |
|---|---|---|
| Creación exitosa | `201 Created` | POST que persiste un recurso nuevo |
| Operación exitosa con respuesta | `200 OK` | GET, PUT, PATCH con body |
| Operación exitosa sin cuerpo | `204 No Content` | DELETE, void del servicio |
| Error de validación | `400 Bad Request` | Input inválido del cliente |
| No autenticado | `401 Unauthorized` | Sin credenciales |
| No autorizado | `403 Forbidden` | Credenciales válidas, permisos insuficientes |
| No encontrado | `404 Not Found` | Recurso inexistente |
| Conflicto | `409 Conflict` | Duplicate key, optimistic locking |
| Error del servidor | `500 Internal Server Error` | Error no anticipado |

**Regla**: el código HTTP debe ser semánticamente correcto. Un 200 con un body
`{"error": "not found"}` es un antipatrón que rompe la semántica del protocolo.

### Separación interfaz / implementación en servicios

Siempre separar la interfaz del servicio de su implementación:

```
ProductService.java      → interfaz (contrato público)
ProductServiceImpl.java  → implementación (lógica de negocio)
```

**Por qué**: permite múltiples implementaciones (producción, prueba, mock),
facilita el testing con dobles de prueba, y hace explícito el contrato público
del servicio independientemente de cómo se implementa.

### Validaciones en capas

- **Capa de presentación** (controller/handler): validar formato y tipos del input
  (campos obligatorios, longitudes, rangos). Usar anotaciones de validación
  del framework (`@NotBlank`, `@NotNull`, `@Min`, etc.)
- **Capa de servicio**: validar reglas de negocio (unicidad, existencia de
  entidades relacionadas, invariantes de dominio). Estas validaciones deben
  existir aunque el controller ya tenga sus validaciones — protegen invocaciones
  directas al servicio sin pasar por el controller.
- **Capa de datos**: constraints de BD (NOT NULL, UNIQUE, FK) como última
  línea de defensa. Nunca la primera.

### Regla de visibilidad de errores en autenticación

En endpoints de autenticación (login, verificación), lanzar siempre el
mismo mensaje de error independientemente de qué falló (usuario no existe,
contraseña incorrecta, cuenta inactiva):

```
"Credenciales incorrectas."  → siempre este mensaje, sin detalle
```

**Por qué**: revelar si el usuario existe o no es una vulnerabilidad de
enumeración de usuarios. Un atacante puede usar mensajes específicos para
construir una lista de usuarios válidos.

### Orden de validaciones en el servicio (de más barata a más costosa)

1. Validar tipos y formatos (sin I/O)
2. Verificar existencia de entidades (`findById`, `existsById`) — 1 query
3. Validar reglas de negocio (unicidad, dependencias) — queries adicionales
4. Ejecutar la operación principal

---

## U5. Patrones de base de datos

### Schema-first (ddl-auto: validate)

El schema de base de datos es la fuente de verdad. Las entidades del ORM
se validan contra el schema existente, no al revés.

**Implicación crítica**: toda nueva columna en una entidad debe existir
primero en la BD antes de reiniciar la aplicación. El orden correcto siempre es:

```
1. ALTER TABLE (BD)
2. Editar la entidad/modelo
3. Compilar y reiniciar
```

En sentido inverso, el ORM lanza SchemaValidationException al arrancar.

### Soft delete

Las entidades no se eliminan físicamente. Un campo `active` (boolean) o
`deletedAt` (timestamp) marca los registros dados de baja:

```
active = true  → registro vigente
active = false → dado de baja (invisible para el usuario, preservado en BD)
```

**Por qué**: los registros históricos (órdenes, movimientos, facturas)
referencian entidades que no deben desaparecer. Un proveedor eliminado
físicamente rompe la integridad referencial de sus órdenes históricas.

**Soft delete con dependencias**: antes de desactivar una entidad, verificar
que no existan registros activos dependientes:

```
No se puede desactivar Supplier si tiene PurchaseOrders en PENDING o APPROVED
No se puede desactivar Category si tiene Products activos asignados
```

### Columnas de auditoría

Registrar quién creó y quién modificó cada registro:

```
created_at  → timestamp de creación (inmutable — updatable=false en ORM)
created_by  → usuario que creó (inmutable)
updated_at  → timestamp de última modificación (null si nunca fue editado)
updated_by  → usuario que modificó (null si nunca fue editado)
```

**Regla crítica — `updatable=false`**: solo para campos que se establecen
en el INSERT y nunca cambian (`created_at`, `created_by`). Para campos que
comienzan como NULL y se asignan en un UPDATE posterior (`approved_by`,
`received_by`, `cancelled_by`), NO usar `updatable=false` — el ORM
excluiría el campo del UPDATE y el valor nunca llegaría a la BD.

**Resolución del usuario autenticado** — patrón estándar:
```java
private User resolveAuthenticatedUser() {
    String username = SecurityContextHolder.getContext()
                          .getAuthentication().getName();
    return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException(
                "Usuario autenticado no encontrado."));
}
```

### Campos NOT NULL en entidades financieras

Campos usados en cálculos financieros (costos, precios, cantidades)
deben ser NOT NULL en la BD. La "captura progresiva" (permitir null
temporalmente hasta que el dato esté disponible) deteriora la integridad
de los cálculos y obliga a agregar filtros especiales en todas las queries.

**Corrección pre-módulo**: si un campo financial es nullable y se va a
implementar un módulo que calcula sobre él, aplicar `ALTER TABLE SET NOT NULL`
y `@NotNull` en el DTO antes de iniciar el módulo, no durante.

### Optimistic Locking para concurrencia

En entidades que pueden ser modificadas concurrentemente por múltiples
usuarios (stock de productos, saldos de cuentas), usar un campo de versión
que el ORM incrementa automáticamente en cada UPDATE.

Si dos transacciones intentan modificar la misma entidad:
- La primera actualiza la versión 0 → 1
- La segunda intenta actualizar con versión 0, el ORM detecta que la BD
  tiene versión 1 → lanza excepción de concurrencia

**Importante**: usar `saveAndFlush()` (o equivalente) en lugar de `save()`
cuando se necesita capturar la excepción de versión dentro de un try-catch.
`save()` encola el UPDATE para el commit — la excepción ocurre fuera del
try-catch. `saveAndFlush()` fuerza el SQL inmediatamente.

---

## U6. Patrones de seguridad (Auth, RBAC)

### JWT stateless

- Tokens autofirmados con clave secreta (HMAC-SHA256 o RSA)
- Claims mínimos: `sub` (username/id), `roles`, `iat`, `exp`
- Sin estado en servidor — cada request se autentica con el token
- Tiempo de vida corto (2h típico) para limitar el impacto de un token robado
- La clave secreta va en variable de entorno, nunca en el código

### Filtro JWT

El filtro de autenticación se ejecuta antes de cualquier lógica de negocio:

```
Request → JWT Filter → Validar token → Cargar usuario/roles en contexto
       → Security Rules → Controller → Service → Repository
```

El filtro debe manejar tokens malformados con try-catch. Sin manejo de
excepción, un token inválido puede causar un 500 en lugar del 403 esperado.

### CORS

CORS debe configurarse en la capa de seguridad, no en los controladores
individuales. Si CORS se aplica después del filtro de autenticación, el
preflight `OPTIONS` del browser (sin `Authorization` header) es rechazado
con 403 antes de que el browser envíe la petición real.

**Por qué `allowedOriginPatterns("*")` y no `allowedOrigins("*")`**: cuando
`allowCredentials = true`, la especificación CORS prohíbe el wildcard literal.
`allowedOriginPatterns("*")` es el equivalente seguro.

### RBAC — Control de acceso basado en roles

Definir los roles y la matriz de acceso antes de implementar los endpoints:

| Rol | Descripción |
|---|---|
| `ADMIN` | Acceso total incluyendo gestión de usuarios |
| `MANAGER` | Acceso operativo completo, sin gestión de usuarios |
| `[ROL_3]` | [Descripción] |
| `[ROL_4]` | [Descripción] |

Las reglas de autorización por URL van en la configuración de seguridad,
no en anotaciones dispersas en los controladores. Esto permite auditar
toda la política de acceso en un solo lugar.

**Orden de las reglas**: las más específicas van antes que las generales.
Una regla general que precede a una específica anula la específica.

### Bootstrap del primer usuario admin

Sin un usuario admin inicial, no hay forma de crear usuarios (circular).
Solución: `DataInitializer` que crea el admin al arrancar si la tabla está vacía.

```
Al arrancar:
  IF users.count() == 0 THEN
    crear admin con credenciales iniciales documentadas
  END
```

Las credenciales iniciales deben ser documentadas y cambiadas en el primer
inicio de sesión en producción.

---

## U7. Taxonomía de tests

El proyecto usa cinco tipos de test automatizados más validación E2E manual:

| Tipo | Tecnología | BD | Seguridad | Velocidad | Detecta |
|---|---|---|---|---|---|
| **A** | Unit test puro (Mockito, Jest, pytest) | No | No | Muy rápida | Lógica de negocio |
| **B** | Test de capa web/handler (sin filtros de seguridad) | No | No (deshabilitado) | Rápida | HTTP status, validación input, JSON |
| **B\*** | Test de capa web con seguridad activa | No | **Sí** | Rápida | Reglas RBAC, 403s, JWT |
| **C** | Test de integración completo (BD real) | **Sí** | **Sí** | Lenta | Hibernate/ORM, FK, auditoría en BD |
| **D** | Test de repositorio/queries (BD real) | **Sí** | No | Media | Queries SQL/JPQL, constraints de BD |
| **E** | Scripts curl / Postman (fuera del pipeline) | **Sí** | **Sí** | Manual | Flujo completo de negocio |

**Regla de elección**:
- B* sustituye a B cuando se necesita verificar que la seguridad no bloquea
  rutas públicas o sí bloquea rutas protegidas
- C se usa para verificar comportamientos que los mocks no detectan:
  persistencia real en BD, `updatable=false` incorrecto, mappers mal configurados
- D se usa para queries complejas que no pueden expresarse con query methods
  derivados y que el mock siempre retorna lo que se configura (no la query real)

**Qué detecta cada tipo**:

| Tipo de bug | A | B | B* | C | D |
|---|---|---|---|---|---|
| Lógica de negocio del servicio | ✓ | ~ | ~ | ✓ | ✗ |
| Validaciones de input (@Valid) | ✗ | ✓ | ✓ | ✓ | ✗ |
| Seguridad (403, JWT, RBAC) | ✗ | ✗ | ✓ | ✓ | ✗ |
| Mapeos ORM faltantes | ✗ | ✗ | ✗ | ✓ | ✗ |
| updatable=false incorrecto | ✗ | ✗ | ✗ | ✓ | ✗ |
| FK constraints (NOT NULL en BD) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Queries SQL/JPQL (sintaxis, dialecto) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Auditoría persistida en BD real | ✗ | ✗ | ✗ | ✓ | ✗ |
| Concurrencia / Optimistic Locking | ✗ | ✗ | ✗ | ✓ | ✗ |
| Flujo completo de negocio | ✗ | ✗ | ✗ | ✓ | ✗ |

**Umbrales de cobertura recomendados**:
- Líneas: ≥ 70% por paquete (excluir DTOs, modelos, mappers generados)
- Métodos: ≥ 75%
- Ramas: sin umbral fijo — documentar estado actual como referencia

---

## U8. Patrones de tests

### Tests unitarios de servicios (Tipo A)

- Sin contexto del framework — instanciar solo la clase bajo prueba
- Mockear todas las dependencias externas (repositorios, otros servicios)
- `@BeforeEach` reinicia los datos en cada test para garantizar independencia
- Patrón AAA: **Arrange** → **Act** → **Assert**
- Cubrir siempre: happy path + entidad no encontrada + reglas de negocio

**Regla crítica — dirty-checking vs save()**: los servicios `@Transactional`
que no llaman `save()` explícitamente (usan dirty-checking del ORM) deben
verificarse con aserciones sobre el estado del objeto, NO con
`verify(repository.save(...))`. En tests unitarios no hay ORM real —
`save()` nunca se llama aunque el comportamiento en producción sea correcto.

```
// INCORRECTO para servicios con dirty-checking:
verify(repository).save(entity);  // falla: save() no se invoca

// CORRECTO:
assertDoesNotThrow(() -> service.updateEntity(1L, dto));
assertNotNull(entity.getUpdatedAt());
assertEquals(expectedUser, entity.getUpdatedBy());
```

**Servicios con SecurityContextHolder** (o equivalente de contexto de seguridad):

```java
@BeforeEach
void setUp() {
    // Mockear el contexto de seguridad
    Authentication auth = mock(Authentication.class);
    lenient().when(auth.getName()).thenReturn("usuario_test");
    SecurityContext ctx = mock(SecurityContext.class);
    lenient().when(ctx.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(ctx);
}

@AfterEach
void tearDown() {
    // OBLIGATORIO: SecurityContextHolder es estado estático global.
    // Sin limpiar, se filtra entre tests y produce resultados no deterministas.
    SecurityContextHolder.clearContext();
}
```

**Stubs como `lenient()`**: cuando el stub no es utilizado por todos los tests
de la clase (ej. el contexto de seguridad no se usa en tests de solo lectura),
declararlo como lenient para evitar `UnnecessaryStubbingException`.

### Tests de capa web (Tipo B)

- Solo cargar el contexto de la capa web (sin BD, sin servicios reales)
- Mockear el servicio completo
- Siempre incluir el mock de `JwtUtils` (o el componente de validación
  de tokens) — la cadena de filtros de seguridad lo necesita incluso con
  los filtros deshabilitados
- Verificar validaciones de input con bodies inválidos → confirmar `@Valid`
  activo en el controller
- Verificar serialización JSON de la respuesta

### Tests de seguridad (Tipo B*)

```java
// Los filtros de seguridad están ACTIVOS (NO deshabilitar)
// Mockear JwtUtils para controlar qué tokens se consideran válidos

private void autenticarConRol(String... roles) {
    when(jwtUtils.extractUsername(TOKEN)).thenReturn("usuario_test");
    when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
    when(jwtUtils.extractRoles(TOKEN)).thenReturn(List.of(roles));
    // mockear los TRES métodos — el filtro llama a todos
}
```

La aserción para "no debe ser 403" (acceso permitido) usa verificación directa
porque los frameworks de test no suelen tener `status().isNot(403)`:

```java
.andExpect(result -> assertNotEquals(HttpStatus.FORBIDDEN.value(),
    result.getResponse().getStatus(), "mensaje explicativo del caso"))
```

### Tests de repositorio con BD real (Tipo D)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional  // rollback automático después de cada test
class ProductRepositoryTest { ... }
```

- `replace=NONE` es **obligatorio** si el schema usa características específicas
  del motor de BD (PostgreSQL, MySQL, etc.) que la BD en memoria no soporta
- `@Transactional` heredado hace rollback automático — los datos no persisten
  entre tests ni contaminan la BD de desarrollo
- Setup en `@BeforeEach`: crear el grafo mínimo de entidades con sufijos
  únicos (`System.currentTimeMillis()`) para evitar colisiones entre ejecuciones

### Tests de integración con BD real (Tipo C)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditIntegrationTest {
    @Autowired TestRestTemplate restTemplate;
}
```

- Puerto aleatorio → sin conflicto con la app corriendo en el puerto principal
- `@TestMethodOrder` + `@Order(n)` para tests que comparten estado (IDs creados)
- `TestRestTemplate` / cliente HTTP real → Spring Security activo, ORM activo
- **Patrón de doble verificación**: verificar el campo tanto en la respuesta
  inmediata (memoria) como en un GET posterior (BD persistida)

```java
// Verificación en memoria (inmediata):
assertNotNull(approveResp.getBody().get("approvedById"));

// Verificación en BD (GET posterior):
ResponseEntity<Map> getResp = restTemplate.exchange(base + "/orders/" + id, ...);
assertNotNull(getResp.getBody().get("approvedById"),
    "approvedById debe persistir en BD — verifica updatable=false");
```

### Casos edge obligatorios en cualquier módulo

- Entidad no encontrada por ID → lanzar excepción, no retornar null
- Colección vacía → retornar lista vacía `[]`, no null
- División por cero en porcentajes → retornar null o 0, documentar semántica
- Período sin datos → retornar estructura vacía, no lanzar excepción
- ID inexistente que retorna `[]` con HTTP 200 es indistinguible de una
  colección vacía → validar existencia del ID antes de consultar la colección
- `verify(repository, never()).save(any())` para confirmar que operaciones
  costosas no se ejecutan cuando la validación previa falla

---

## U9. Documentación de bugs (Lecciones aprendidas)

Los bugs detectados en pruebas E2E o de integración que los tests unitarios
con mocks no detectaron se documentan con el siguiente patrón:

```
### Bug N: [título descriptivo del bug]

**Síntoma**: [qué se observa en producción/E2E — mensaje de error, comportamiento]

**Causa**: [qué parte del código lo provoca — clase, método, decisión de diseño]

**Por qué los tests no lo detectaron**: [qué aspecto del mock ocultó el bug]

**Corrección**: [qué se cambió y el patrón general que se deriva]
```

**Bugs recurrentes conocidos** (aprendidos en proyectos anteriores):

- **Relación @ManyToOne no resuelta en el servicio**: el mock devuelve el
  objeto completo pero en producción el ORM lanza NOT NULL constraint.
  Patrón: toda relación que viene del DTO como `Long xxxId` debe ser
  resuelta por el servicio antes de `save()`.

- **`updatable=false` en campos que comienzan null**: el campo se ve correcto
  en memoria (la respuesta inmediata tiene valor) pero un GET posterior muestra
  null porque el ORM excluyó el campo del UPDATE statement. Solo usar
  `updatable=false` en campos que se escriben en el INSERT y nunca cambian.

- **Filtro JWT sin try-catch**: un token malformado causa 500 en lugar de 403.
  El filtro debe capturar la excepción de parsing y dejar pasar la request
  sin autenticar — Spring Security la rechaza con 403.

- **Campo financiero nullable en módulo de reportes**: el módulo de reportes
  requiere filtros `IS NOT NULL` en todas las queries de cálculo. Corrección:
  aplicar `NOT NULL` en BD y `@NotNull` en DTO antes de implementar el módulo.

- **`save()` vs `saveAndFlush()` con Optimistic Locking**: la excepción de
  versión ocurre al commit, fuera del try-catch que rodea al `save()`.
  Usar `saveAndFlush()` para capturar la excepción dentro del try-catch.

- **Contrato de API asumido sin verificar (frontend/cliente)**: el endpoint se
  codifica basado en la propuesta del módulo en lugar de la especificación OpenAPI
  real. Si el endpoint no existe, devuelve 403/404 en runtime. Si el response type
  es incorrecto (array vs paginado, DTO vs void), falla silenciosamente o con
  TypeError. Corrección: siempre consultar el OpenAPI/Swagger antes de escribir
  el cliente HTTP. Ver sección U10 para el protocolo completo.

- **Callbacks de error HTTP fuera del contexto reactivo (Angular con OnPush)**:
  tras un error HTTP en Angular 21+, los cambios de estado dentro del callback
  `error` no disparan la detección de cambios automáticamente. El spinner/estado
  de carga permanece activo en la vista. Corrección: llamar
  `ChangeDetectorRef.detectChanges()` inmediatamente después de mutar el estado
  dentro del callback de error. Alternativa: usar Angular Signals.

- **HTTP 500 genérico para errores de negocio**: todos los servicios lanzan
  `RuntimeException` para cualquier condición de error. El `GlobalExceptionHandler`
  los mapea todos a HTTP 500 — el cliente no puede distinguir entre 404, 409 y 422.
  Corrección: diseñar la jerarquía de excepciones tipadas desde el primer módulo:
  `ResourceNotFoundException` → 404, `DuplicateResourceException` → 409,
  `BusinessRuleException` → 422. Registrar un `@ExceptionHandler` específico por
  clase antes del handler genérico de `RuntimeException`. Solo usar `RuntimeException`
  genérica para errores de infraestructura genuinos (BD caída, usuario JWT no en tabla).

---

## U10. Protocolo pre-código — Verificación de contratos API (proyectos cliente-servidor)

**Aplica cuando**: el proyecto consume una API REST externa o de otro repositorio
(frontend consumiendo backend, microservicio consumiendo otro servicio, etc.).

**Regla**: antes de escribir cualquier servicio, cliente HTTP, modelo o interfaz
que consuma una API, verificar los contratos reales. No hacerlo causa bugs de
integración que los tests unitarios (con mocks) no detectan y solo aparecen en
el browser/runtime con datos reales.

### Pasos obligatorios

**1. Obtener la especificación de la API** (en orden de preferencia):
   - Swagger UI o equivalente: `http://[HOST]/swagger-ui/index.html`
   - OpenAPI/JSON spec: `http://[HOST]/v3/api-docs` (o `/openapi.json`, `/api-docs`)
   - Memoria técnica del módulo (sección de contratos, si ya existe)
   - Si la API no está corriendo: leer el código fuente del controlador directamente

**2. Verificar para CADA endpoint que se va a consumir:**

| Dato | Por qué verificarlo | Error típico si se omite |
|---|---|---|
| Ruta exacta (método + path) | Puede no existir o diferir del nombre esperado | 404 en runtime |
| HTTP status code de respuesta | 204 significa sin body — no hay JSON que parsear | Null pointer en runtime |
| Nombres exactos de campos | El productor y el consumidor usan convenciones distintas | Campos `undefined`/null silenciosos |
| Formato del response body | `PageResponse<T>` vs objeto simple vs `void` | `TypeError: is not iterable` |
| Campos obligatorios del request | Opcional vs requerido puede no ser evidente | 400 Bad Request |

**3. Documentar los contratos verificados** en la Sección 4 de la memoria técnica
del módulo ANTES de escribir código. Una propuesta con contratos incorrectos
propaga el error al código.

**4. Reglas de oro:**
- NUNCA asumir que un endpoint de colección retorna un array plano `[...]`.
  La mayoría de APIs paginadas retornan `{content: [...], totalPages: ...}`.
- NUNCA asumir nombres de campos — leer el JSON real o el DTO del productor.
- Un endpoint de creación (`POST`) puede retornar 201 con body, 200 con body, o 204 sin body.
  Verificar cuál es antes de tiparlo.
- Si un endpoint devuelve 204, el observable/promise debe ser `Observable<void>` / `Promise<void>`.
  Tiparlo como `Observable<AlgunDTO>` causa errores silenciosos al parsear `null`.

### Checklist pre-código

```
[ ] Consulté la especificación OpenAPI/Swagger del servicio que voy a consumir
[ ] Para cada endpoint: verifiqué ruta exacta, método HTTP y HTTP status code
[ ] Para cada response: verifiqué si es colección paginada, objeto simple o void
[ ] Para cada DTO: verifiqué nombres exactos de campos (no asumí ninguno)
[ ] Documenté contratos verificados en la Sección 4 de la memoria técnica
```

### Bugs recurrentes conocidos en integración API

- **Colección paginada asumida como array**: `this.items = response` cuando `response`
  es `{content: [...], totalPages: ...}`. El `@for` / `.map()` sobre el objeto lanza
  `TypeError: is not iterable`. Corrección: siempre extraer `.content`.

- **Nombre de campo del DTO incorrecto**: el backend usa `companyName`, el frontend
  usa `name`. La propiedad aparece como `undefined` en todos los registros.
  El dropdown/tabla muestra etiquetas vacías sin mensaje de error.

- **POST que retorna 204 tipado como DTO**: el parser intenta parsear `null` como JSON.
  Si el error handler no tiene try-catch, puede crashear silenciosamente.

- **Endpoint asumido que no existe**: la request llega al servidor y retorna 403/404.
  Los tests con mocks nunca fallan porque nunca llegan a la red.
  Solo se detecta en el browser con backend real.

---

# ═══════════════════════════════════════════════════════════════
# BLOQUE TÉCNICO
# Stack de referencia: Spring Boot + Java + PostgreSQL + Maven
# Reemplazar por equivalentes del stack del nuevo proyecto
# ═══════════════════════════════════════════════════════════════

---

## T1. Descripción del proyecto

[DESCRIPCIÓN_DEL_PROYECTO]

**Stack tecnológico:**
- Backend: [FRAMEWORK] [VERSION] con [LENGUAJE] [VERSION]
- Build tool: [BUILD_TOOL] ([mvn] / [gradle] / [npm] / [pip])
- Base de datos: [DB_ENGINE] [VERSION]
- ORM / acceso a datos: [ORM_LIBRARY]
- Autenticación: [AUTH_LIBRARY]
- Validación: [VALIDATION_LIBRARY]
- Mapper / transformación: [MAPPER_LIBRARY]
- Tests: [TEST_FRAMEWORK]
- Cobertura: [COVERAGE_TOOL]

**Módulos principales:**
- `[modulo_1]` — [descripción]
- `[modulo_2]` — [descripción]

---

## T2. Comandos comunes

```bash
# Compilar
[BUILD_COMMAND]

# Ejecutar la aplicación
[RUN_COMMAND]

# Ejecutar todos los tests
[TEST_COMMAND]

# Ejecutar un test específico
[TEST_COMMAND] -Dtest=[NombreClase]   # Maven
[TEST_COMMAND] --filter [NombreClase] # pytest / jest

# Compilar sin ejecutar tests
[BUILD_COMMAND_SKIP_TESTS]

# Ver reporte de cobertura
[COVERAGE_REPORT_COMMAND]
```

---

## T3. Configuración de base de datos

[DB_ENGINE] requerido antes de levantar la aplicación:
- Host: `[HOST]:[PORT]`
- Base de datos: `[DB_NAME]`
- Usuario: `[DB_USER]`
- Contraseña: variable de entorno `DB_PASSWORD`

La configuración está en `[CONFIG_FILE]`.

**Schema-first**: [ORM_LIBRARY] usa `ddl-auto: validate` — el schema debe
existir previamente. No se crea automáticamente.

---

## T4. Dependencias clave

- **[ORM_LIBRARY]** — Mapeo objeto-relacional. [NOTAS_ESPECÍFICAS]
- **[VALIDATION_LIBRARY]** — Validación de DTOs de entrada.
  El orden de los procesadores de anotaciones importa: [ORDEN]
- **[AUTH_LIBRARY]** — Generación y validación de tokens JWT.
- **[MAPPER_LIBRARY]** — Conversión entre entidades y DTOs.
  Los mappers se generan en tiempo de compilación.
- **[LOMBOK_OR_EQUIVALENT]** — Reducción de boilerplate en modelos y DTOs.

---

## T5. Estándares técnicos del stack

### Inyección de dependencias

```java
// Spring Boot
@Service
@RequiredArgsConstructor
public class [Nombre]ServiceImpl implements [Nombre]Service {
    private final [Nombre]Repository [nombre]Repository;
}
```

```typescript
// NestJS
@Injectable()
export class [Nombre]Service {
    constructor(
        @InjectRepository([Nombre]Entity)
        private readonly [nombre]Repository: Repository<[Nombre]Entity>,
    ) {}
}
```

### Transacciones

```java
// Spring Boot
@Service
@Transactional                    // escritura — nivel de clase
public class [Nombre]ServiceImpl {
    @Transactional(readOnly = true)  // lectura — nivel de método
    public List<...> getAll() { ... }
}
```

### Mappers ([MAPPER_LIBRARY])

```java
// MapStruct (Spring Boot)
@Mapper(componentModel = "spring")
public interface [Nombre]Mapper {

    // Ignorar siempre en toEntity(): id, auditoría, relaciones @ManyToOne
    @Mapping(target = "id",       ignore = true)
    @Mapping(target = "createdAt",ignore = true)
    @Mapping(target = "supplier", ignore = true)  // relación — resuelta en servicio
    [Entidad] toEntity([Nombre]RequestDTO dto);

    // Aplanar relaciones en toResponseDTO()
    @Mapping(source = "supplier.id",        target = "supplierId")
    @Mapping(source = "supplier.name",      target = "supplierName")
    [Nombre]ResponseDTO toResponseDTO([Entidad] entity);

    // Siempre declarar el método de lista para evitar streams en servicios
    List<[Nombre]ResponseDTO> toResponseDTOList(List<[Entidad]> entities);
}
```

**Regla crítica**: si una entidad tiene `@ManyToOne Supplier supplier` y el DTO
tiene `Long supplierId`, el mapper no puede convertir automáticamente. Sin
`@Mapping(target = "supplier", ignore = true)`, el mapper falla silenciosamente
(deja null) y el ORM lanza constraint NOT NULL en el INSERT. El servicio resuelve
la relación via `repository.findById()`.

### DTOs

- **DTOs de request** (entrada): validaciones del framework (`@NotBlank`,
  `@NotNull`, `@Min`, `@DecimalMin`, `@Size`)
- **DTOs de response** (salida): sin validaciones — son solo de salida
- Regla `@NotBlank` vs `@NotNull`:
  - `String` obligatorio → `@NotBlank` (rechaza null, `""` y `"   "`)
  - `BigDecimal`, `Long`, `Integer` obligatorios → `@NotNull`
  - Primitivos (`int`, `boolean`) → no necesitan anotación (nunca son null)
- Campos financieros: `BigDecimal`, nunca `float` o `double`
- Relaciones `@ManyToOne` se **aplanan** en el DTO de respuesta:
  `category` → `categoryId + categoryName` en lugar de objeto anidado

### Repositorios

- Query methods derivados para consultas simples: `findByActiveTrue()`, `existsBySku()`
- `@Query` con JPQL/SQL solo cuando la condición no es expresable con métodos derivados
- Incluir `AndActiveTrue` en métodos de búsqueda filtrada para excluir datos de baja:
  `findByCategoryIdAndActiveTrue(Long categoryId)`

### Tests con [TEST_FRAMEWORK]

**Tipo A — Tests unitarios de servicios:**
```java
@ExtendWith(MockitoExtension.class)
class [Nombre]ServiceImplTest {
    @Mock [Nombre]Repository [nombre]Repository;
    @InjectMocks [Nombre]ServiceImpl [nombre]Service;
}
```

**Tipo B — Tests de controladores:**
```java
@WebMvcTest([Nombre]Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class [Nombre]ControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean [Nombre]Service [nombre]Service;
    @MockBean JwtUtils jwtUtils;  // siempre requerido
}
```

**Tipo D — Tests de repositorio:**
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class [Nombre]RepositoryTest { ... }
```

### JaCoCo — cobertura de código

```bash
# Generar reporte
./mvnw test

# Verificar umbrales
./mvnw verify

# Ver reporte (macOS)
open target/site/jacoco/index.html
```

Umbrales configurados: 70% de líneas por paquete.
Exclusiones: paquetes `dto`, `model`, `mapper` (sin lógica de negocio).

### Nota sobre el compilador del IDE

El compilador incremental puede sobreescribir clases generadas por [MAPPER_LIBRARY].
Siempre ejecutar con el build tool completo:

```bash
[BUILD_COMMAND_SKIP_TESTS] && [RUN_COMMAND]
```

---

## T6. Estándares de módulos implementados

*(Completar con los módulos específicos del proyecto al ir desarrollándolos)*

### Módulo `[nombre]` — [estado: en desarrollo / completo]

- Entidades: [lista]
- Repositorios: [lista]
- DTOs: [lista]
- Mappers: [lista]
- Servicios: [lista]
- Controladores: [lista con endpoints y conteo]
- Tests Tipo A: [clase (N tests)]
- Tests Tipo B: [clase (N tests)]
- Tests Tipo C/D: [clase (N tests)]

---

## T7. Estado actual del proyecto

### Suite de tests actual: [N] tests — 0 fallos

```
Tipo A (unit):          [N] tests
Tipo B (web layer):     [N] tests
Tipo B* (security):     [N] tests
Tipo C (integration):   [N] tests
Tipo D (repository):    [N] tests
──────────────────────────────────
TOTAL:                  [N] tests
Tests E2E (manual):     [N] tests
```

Cobertura: **[N]% líneas · [N]% métodos · [N]% ramas**

Rama activa de desarrollo: `feature/[nombre]`

---

# ═══════════════════════════════════════════════════════════════
# LECCIONES APRENDIDAS — BUGS DETECTADOS EN PRUEBAS E2E
# (Completar con los bugs específicos del proyecto)
# ═══════════════════════════════════════════════════════════════

Durante las pruebas end-to-end se descubrieron bugs que los tests unitarios
con mocks no detectaron. Se documentan aquí como guía para evitar patrones
similares. Ver sección U9 para el patrón de documentación.

*(Agregar bugs específicos del proyecto conforme se descubran)*

---

*CLAUDE_TEMPLATE.md — Generado a partir del proyecto Sistema de Gestión de Almacenes*
*Actualizar este archivo cuando se agreguen estándares relevantes al CLAUDE.md del proyecto de almacenes*
