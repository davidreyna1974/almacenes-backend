# Memoria Técnica Global — Sistema de Gestión de Almacenes

**Versión**: 1.0  
**Fecha de creación**: 2026-06-04  
**Estado**: Activo — se actualiza al finalizar cada módulo  
**Repositorios**:
- Backend: `github.com/davidreyna1974/almacenes-backend`
- Frontend: `github.com/davidreyna1974/almacenes-frontend`

---

## Índice

1. Visión del sistema
2. Stack tecnológico y decisiones arquitectónicas
3. Contratos de integración frontend ↔ backend
4. RBAC transversal (4 roles de extremo a extremo)
5. Guía de configuración del entorno local
6. Cronología de decisiones importantes
7. Estándares transversales (aplican a ambas capas)
8. Estado actual del proyecto
9. Lecciones aprendidas globales
10. Roadmap

---

## 1. Visión del sistema

### Qué hace

Sistema de gestión de almacenes (WMS — Warehouse Management System) que permite
a una empresa administrar su inventario, compras, ventas y generar reportes
financieros y operativos. El sistema diferencia el acceso y las capacidades
según el rol del usuario dentro de la organización.

### Quiénes lo usan

| Audiencia | Rol en el sistema | Frecuencia de uso |
|---|---|---|
| Dueño / dirección general | ADMIN | Semanal — reportes ejecutivos, visión global |
| Gerencia operativa | MANAGER | Diario — catálogos, órdenes, reportes tácticos |
| Personal de almacén | WAREHOUSEMAN | Varias veces al día — recepciones, entregas, Kardex |
| Personal de ventas | SALES | Diario — órdenes de venta, seguimiento a clientes |

### Por qué existe

El sistema reemplaza el uso de hojas de cálculo y procesos manuales para el
control de inventario, compras y ventas. Centraliza la información en una base
de datos única con auditoría completa (quién hizo qué y cuándo), estado de
órdenes en tiempo real y análisis financiero (margen bruto, COGS, ABC).

---

## 2. Stack tecnológico y decisiones arquitectónicas

### Backend

| Tecnología | Versión | Justificación |
|---|---|---|
| Spring Boot | 3.5.14 | Ecosistema maduro para REST APIs empresariales. Auto-configuración reduce boilerplate. |
| Java | 17 LTS | Versión con soporte extendido hasta 2029. Records, pattern matching, mejores GC. |
| PostgreSQL | (última estable) | ACID completo, soporte nativo de JSON, CHECK constraints, rendimiento en queries analíticas. |
| Hibernate / JPA | (incluido en SB) | ORM estándar. `ddl-auto: validate` — schema-first, no auto-generación de tablas. |
| Spring Security + JWT | JJWT 0.12.6 | Autenticación stateless. Sin sesiones de servidor — escalable horizontalmente. |
| MapStruct | 1.5.5 | Conversión entidad↔DTO en tiempo de compilación. Sin reflection en runtime. |
| Lombok | (incluido en SB) | Eliminación de boilerplate (@Builder, @Data, @RequiredArgsConstructor). |
| springdoc-openapi | 2.7.0 | Generación automática de Swagger UI desde los controladores. Versión ≥ 2.7.0 requerida para Spring Framework 6.2.x. |
| Maven | (incluido en SB) | Build tool estándar del ecosistema Spring. |

### Frontend

| Tecnología | Versión | Justificación |
|---|---|---|
| Angular | Última estable | Framework completo (routing, forms, HTTP, DI). Ideal para SPAs empresariales complejas. |
| Angular Material | Coincide con Angular | Componentes UI que siguen Material Design. Accesibilidad incluida, theming via SCSS. |
| TypeScript | (incluido en Angular) | Tipado estático que previene errores en tiempo de desarrollo y documenta los contratos de la API. |
| SCSS | — | Superconjunto de CSS con variables, nesting y mixins. Necesario para el theming de Angular Material. |
| RxJS | (incluido en Angular) | Manejo de asincronía reactiva. BehaviorSubject para estado en servicios. |

### Por qué arquitectura separada (backend / frontend)

Repositorios y procesos de build independientes porque:
- **Ciclos de despliegue distintos**: el frontend puede actualizarse sin tocar el backend
- **Tecnologías incompatibles en el mismo proceso**: JVM vs. Node.js
- **Equipos separables**: un desarrollador puede trabajar en el frontend sin conocer Java
- **Escalamiento independiente**: frontend en CDN, backend en servidor de aplicaciones

### Por qué no microservicios

El sistema es de escala media (un almacén, < 100 usuarios concurrentes). Los
microservicios añaden complejidad de red, discovery y orquestación que no se
justifica. Un monolito bien estructurado por módulos es más mantenible a esta escala.

---

## 3. Contratos de integración frontend ↔ backend

### Autenticación JWT — flujo completo

```
[Angular] → POST /api/v1/auth/login  { username, password }
         ← { token: "eyJ..." }          200 OK

[Angular] almacena el token en localStorage

[Angular] → GET /api/v1/inventory/products
            Header: Authorization: Bearer eyJ...
[JwtAuthenticationFilter]
  1. Extrae el token del header
  2. Llama jwtUtils.extractUsername(token) → "admin"
  3. Llama jwtUtils.validateToken(token) → true
  4. Llama jwtUtils.extractRoles(token) → ["ROLE_ADMIN","ROLE_WAREHOUSEMAN"]
  5. Carga SimpleGrantedAuthority en el SecurityContext
[SecurityConfig]
  6. Evalúa la regla: hasAnyRole("ADMIN","MANAGER","WAREHOUSEMAN","SALES")
  7. Permite el request
         ← { content: [...], totalPages: 3, ... }  200 OK
```

**Token expirado (2 horas)**:
```
[Angular] → GET /api/v1/inventory/products
            Header: Authorization: Bearer eyJ...expirado
         ← 403 Forbidden
[error.interceptor.ts] detecta 403 → redirige a /login con mensaje
```

### Endpoint de búsqueda de productos (catálogo principal)

```
GET /api/v1/inventory/products
    ?search=taladro        (opcional) búsqueda parcial en sku y name, case-insensitive
    &categoryId=1          (opcional) filtra por categoría exacta
    &status=AVAILABLE      (opcional) filtra por estado: AVAILABLE | DISCONTINUED | OUT_OF_STOCK
    &supplierId=2          (opcional) filtra por proveedor exacto
    &page=0 &size=20       (opcional, defaults 0/20)

← 200 OK  PageResponseDTO<ProductResponseDTO>

Comportamiento:
  - Siempre filtra active = true (soft-delete excluido implícitamente).
  - Sin parámetros → todos los productos activos paginados.
  - search busca en SKU (LIKE) y nombre (LIKE) simultáneamente con OR.
  - Los demás filtros se combinan con AND entre sí y con search.
  - Ordenado por name ASC.
  - JPQL usa CAST(:search AS string) para evitar el error lower(bytea)
    de PostgreSQL cuando search es null (Hibernate 6 + PostgreSQL 15).

Campos del ProductResponseDTO (campos clave para el frontend):
  id, sku, name, description, price, unitCost
  currentStock, minimumStock, reservedStock, availableStock
  status            → "AVAILABLE" | "DISCONTINUED" | "OUT_OF_STOCK"
  active            → siempre true en este endpoint (filtrado implícito)
  categoryId, categoryName
  supplierId, supplierName   ← supplierName agregado 2026-06-06 (antes solo supplierId)
  createdAt, createdByUsername, updatedAt, updatedByUsername, updatedById

Implementación frontend: ProductService.search(params) en
  src/app/modules/inventory/services/product.service.ts
```

⚠️ **Nota histórica**: antes de esta implementación (2026-06-06) no existía ningún
endpoint GET para el catálogo de productos con búsqueda de texto. El endpoint
`GET /products/sku/{sku}` solo hace lookup exacto (1 resultado o 404).

### Formato de errores del backend

Todos los errores pasan por `GlobalExceptionHandler` (`core/exception/`).
Formato JSON unificado para todos los casos:

```json
{
  "timestamp": "2026-06-07T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Producto con id 99 no encontrado."
}
```

**Tabla de códigos HTTP por tipo de error** (actualizada 2026-06-07):

| Condición | Clase Java | HTTP |
|---|---|---|
| Entidad no encontrada (ID, SKU, nombre) | `ResourceNotFoundException` | **404** |
| Duplicado (SKU, nombre de categoría) | `DuplicateResourceException` | **409** |
| Regla de negocio violada (stock insuficiente, categoría con productos activos, tipo de movimiento inválido) | `BusinessRuleException` | **422** |
| Validación de campos del DTO (`@Valid`, `@NotBlank`, `@Min`) | `MethodArgumentNotValidException` | **400** |
| Contención de Optimistic Locking | `ObjectOptimisticLockingFailureException` | **409** |
| Error real de infraestructura (BD, usuario JWT inexistente) | `RuntimeException` | **500** |
| Sin autorización (JWT ausente o inválido) | Spring Security | **403** (sin body JSON) |

> ⚠️ **Credenciales incorrectas en login**: el backend devuelve **500** para credenciales
> incorrectas (no 401). El frontend lee `err.error?.message` del body independientemente
> del status code. Esto aplica **solo al endpoint `/auth/login`** — para el resto de los
> errores de negocio el código HTTP es semánticamente correcto desde 2026-06-07.

El frontend **no debe basar ninguna lógica en el status code de errores de negocio**;
debe siempre leer `err.error?.message` del body. El `error.interceptor.ts` solo actúa
sobre 401 (sesión expirada → redirect) y 403 (acceso denegado → snackbar); el resto
pasa al `catchError` del componente.

### Formato de paginación (PageResponseDTO)

Todos los endpoints GET de colección retornan este contrato:

```json
{
  "content":      [...],
  "currentPage":  0,
  "totalPages":   5,
  "totalElements": 96,
  "size":         20,
  "first":        true,
  "last":         false
}
```

Parámetros de request: `?page=0&size=20` (defaults aplicados en el backend).

El frontend usa este contrato desde el primer componente de lista. Nunca
asumir que un endpoint retorna un array plano `[...]`.

### CORS

Configurado en `SecurityConfig.java` con `allowedOriginPatterns("*")` para
desarrollo. En producción se reemplaza por el dominio del frontend.

**Por qué CORS va en SecurityConfig y no en `@CrossOrigin`**: el filtro CORS
debe ejecutarse antes que el filtro JWT. Si se aplica después, el preflight
`OPTIONS` del browser llega sin `Authorization` header, Spring Security lo
rechaza con 403 y el browser nunca envía la petición real.

### Rutas públicas (sin JWT)

```
POST /api/v1/auth/login      → login
GET  /swagger-ui/**          → Swagger UI
GET  /swagger-ui.html
GET  /v3/api-docs
GET  /v3/api-docs/**
```

Todo lo demás requiere `Authorization: Bearer <token>`.

### Documentación de la API

Swagger UI disponible en: `http://localhost:8080/swagger-ui/index.html`  
Especificación OpenAPI 3.0: `http://localhost:8080/v3/api-docs`

El frontend consume esta especificación como contrato de referencia.
Antes de implementar cualquier llamada HTTP en Angular, verificar el
contrato en Swagger.

---

## 4. RBAC transversal (4 roles de extremo a extremo)

### Los 4 roles

| Rol | Descripción | Quién lo tiene |
|---|---|---|
| `ROLE_ADMIN` | Acceso total — gestión de usuarios y todos los módulos | Dueño, administrador del sistema |
| `ROLE_MANAGER` | Todos los módulos excepto gestión de usuarios | Gerencia operativa |
| `ROLE_WAREHOUSEMAN` | Lectura de inventario, recepción de compras, entrega de ventas, reportes operativos | Personal de almacén |
| `ROLE_SALES` | Crear/gestionar órdenes de venta, operaciones pendientes | Personal de ventas |

### Cómo fluyen los roles

```
1. Backend: los roles se almacenan en la tabla user_roles (BD)
2. Backend: al hacer login, UserServiceImpl carga los roles del usuario
3. Backend: JwtUtils.generateToken() incluye los roles en el claim "roles" del JWT
4. Frontend: jwt.interceptor.ts agrega el token a cada request
5. Backend: JwtAuthenticationFilter extrae los roles y crea SimpleGrantedAuthority
6. Backend: SecurityConfig evalúa hasRole() / hasAnyRole() por URL
7. Frontend: AuthGuard lee el rol del token (decodificado en Angular) y
             activa/desactiva rutas y elementos del sidebar
```

### Matriz de acceso por módulo

| Módulo / Operación | ADMIN | MANAGER | WAREHOUSEMAN | SALES |
|---|:---:|:---:|:---:|:---:|
| Gestión de usuarios | ✓ | — | — | — |
| Inventory: escritura (categorías, productos) | ✓ | ✓ | — | — |
| Inventory: lectura | ✓ | ✓ | ✓ | ✓ |
| Inventory: registrar movimiento de stock | ✓ | ✓ | ✓ | — |
| Inventory: eliminar (desactivar) producto | ✓ | — | — | — |
| Purchases: CRUD proveedores y órdenes | ✓ | ✓ | — | — |
| Purchases: recibir orden aprobada | ✓ | ✓ | ✓ | — |
| Sales: crear y gestionar órdenes | ✓ | ✓ | — | ✓ |
| Sales: aprobar orden de venta | ✓ | ✓ | — | — |
| Sales: entregar orden aprobada | ✓ | ✓ | ✓ | — |
| Sales: eliminar (desactivar) cliente | ✓ | ✓ | — | — |
| Reports: dashboard ejecutivo | ✓ | — | — | — |
| Reports: analíticos (ABC, top products, etc.) | ✓ | ✓ | — | — |
| Reports: operativos (low-stock, Kardex, movements) | ✓ | ✓ | ✓ | — |
| Reports: operaciones pendientes | ✓ | ✓ | ✓ | ✓ |

### DataInitializer — bootstrap del primer admin

`core/config/DataInitializer.java` crea el usuario `admin`/`Admin123!` con
`ROLE_ADMIN + ROLE_WAREHOUSEMAN` al arrancar si la tabla `users` está vacía.
Resuelve el problema chicken-and-egg de no poder crear el primer usuario sin
que exista un admin previo.

**En producción**: cambiar la contraseña del admin en el primer inicio de sesión.

---

## 5. Guía de configuración del entorno local

### Requisitos previos

- Java 17+ (`java -version`)
- Maven 3.8+ (o usar el wrapper `./mvnw`)
- Node.js 18+ y npm (`node -v`, `npm -v`)
- Angular CLI (`npm install -g @angular/cli`)
- PostgreSQL 14+ corriendo en `localhost:5432`

### Paso 1 — Configurar la base de datos

```sql
-- Conectar a PostgreSQL como superusuario
CREATE DATABASE almacen_db;
-- El schema se crea con los scripts SQL del proyecto (ddl-auto: validate)
-- Ejecutar los scripts de creación de tablas antes de iniciar la app
```

**Variables de entorno requeridas**:
```bash
export DB_PASSWORD=tu_contraseña_postgres
export JWT_SECRET=$(openssl rand -hex 32)   # o usar el default de desarrollo
```

### Paso 2 — Instalar el hook de protección Git (ambos repos)

```bash
# Backend
cd .../backend/almacenes
cp hooks/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit

# Frontend
cd .../frontend/almacenes
cp hooks/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
```

### Paso 3 — Levantar el backend

```bash
cd .../backend/almacenes
./mvnw clean package -DskipTests && java -jar target/almacenes-0.0.1-SNAPSHOT.jar
# App disponible en http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui/index.html
```

### Paso 4 — Levantar el frontend

```bash
cd .../frontend/almacenes
npm install
ng serve
# App disponible en http://localhost:4200
```

### Paso 5 — Verificar la integración

1. Abrir `http://localhost:4200`
2. Hacer login con `admin` / `Admin123!`
3. Verificar que el sidebar muestra todos los módulos
4. Verificar que `http://localhost:8080/swagger-ui/index.html` está accesible

### Credenciales por defecto (solo desarrollo)

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `Admin123!` | ADMIN + WAREHOUSEMAN |

Usuarios adicionales se crean desde la interfaz de gestión de usuarios (solo ADMIN).

---

## 6. Cronología de decisiones importantes

| Fecha | Decisión | Justificación |
|---|---|---|
| Inicio | Spring Boot 3.5 + Java 17 | LTS, ecosistema maduro, soporte hasta 2029 |
| Inicio | PostgreSQL sobre MySQL | ACID estricto, mejor soporte de constraints, queries analíticas |
| Inicio | JWT stateless | Sin sesiones de servidor, escalable, estándar de industria |
| Inicio | ddl-auto: validate | Schema-first — la BD es la fuente de verdad, no el ORM |
| Módulo inventory | unitCost NOT NULL | Eliminación de "captura progresiva" antes del módulo reports. Sin NOT NULL, las queries de COGS requieren filtros IS NOT NULL en todas las queries financieras |
| Módulo auth (RBAC) | 4 roles en lugar de permisos granulares | Los roles cubren el 100% de los casos de uso actuales con menor complejidad. Permisos granulares se agregan cuando el negocio lo requiera |
| Módulo auth (RBAC) | DataInitializer para bootstrap de admin | Sin él, el primer usuario no puede crearse (circular dependency: necesitas un admin para crear admins) |
| Post-reports | springdoc 2.7.0 (no 2.5.0) | Versión 2.5.0 incompatible con Spring Framework 6.2.x: NoSuchMethodError en ControllerAdviceBean |
| Post-reports | Swagger + paginación desde el inicio | Agregarlos al final obligó a actualizar 9 archivos de test y cambiar el contrato de respuesta. Lección: implementar desde el primer módulo |
| Post-reports | Hook pre-commit para ramas protegidas | Commits directos a develop ocurrieron en múltiples ocasiones. El hook elimina la dependencia de memoria |
| Frontend Módulo 0 | Angular 21 + Vitest como runner de tests | Angular CLI 21 usa Vitest (no Karma). Invocar siempre via `ng test`, nunca `npx vitest run` directamente |
| Frontend Módulo 0 | `@angular/animations` se instala por separado | `provideAnimationsAsync()` requiere el paquete `@angular/animations` aunque no venga con Angular core |
| Frontend Módulo 1 | Backend devuelve HTTP 500 para credenciales incorrectas | El frontend lee `err.error?.message` del body en vez de verificar el status code. Documentado en sección 3 |
| Frontend Módulo 1 | `getPrimaryRole()` usa jerarquía explícita | `ADMIN > MANAGER > WAREHOUSEMAN > SALES`. Evita que usuarios con múltiples roles muestren el rol equivocado según el orden de inserción del backend |
| Frontend Módulo 1 | Angular 21: callbacks HTTP pueden ejecutarse fuera de zone.js | Tras un error HTTP, los cambios de estado en el `error` callback no actualizan la vista automáticamente. Solución: `ChangeDetectorRef.detectChanges()` inmediatamente después del cambio de estado |
| Frontend Módulo 2 | Protocolo obligatorio pre-código en `CLAUDE.md` | Tres contratos de API incorrectos en la propuesta del módulo causaron bugs detectados solo en browser. Regla: verificar OpenAPI antes de escribir cualquier servicio o modelo. Ver L8 en sección 9. |
| Frontend Módulo 2 | `memoria_tecnica_global_proyecto.md` copiada localmente al frontend | La versión original está en el repo backend (no accesible directamente). Se mantiene una copia en `almacenes-frontend/` raíz. Sincronizar manualmente al finalizar cada módulo. |
| Frontend Módulo 2 — post | Corrección RBAC: botón "Editar" de categorías oculto para WAREHOUSEMAN/SALES | El botón existía sin guard `@if(canWrite())`. Corregido junto con el clic en fila y el cursor pointer. El backend ya rechazaba el guardado con 403 — era un gap de UX, no de seguridad. |
| Frontend Módulo 2 — post | HTTP 500 para errores de negocio reemplazado por 404/409/422 | `GlobalExceptionHandler` + tres clases custom en `core/exception/`. Mejora la observabilidad (logs) y la semántica REST. El frontend no requirió cambios. |

---

## 7. Estándares transversales

Estos estándares aplican a **ambas capas** (backend y frontend).

### Documentación de código

Explicar el **POR QUÉ**, no el qué. El código ya dice qué hace;
los comentarios documentan por qué se eligió ese enfoque, qué restricción
lo obliga, o qué comportamiento sorprendería a un lector.

### Convenciones de Git

- **Nunca** commitear directamente en `develop` o `main`
- Todo trabajo va en `feature/`, `fix/`, o `chore/` branches
- Merge siempre con `--no-ff` para preservar el historial
- Hook pre-commit instalado en ambos repositorios

### Documentación obligatoria por módulo

Por cada módulo nuevo (backend y frontend):
- `propuesta_modulo_<nombre>[_frontend].txt` — antes de escribir código
- `memoria_tecnica_modulo_<nombre>[_frontend].md` — actualizada por fase

La Sección 7 de cada memoria técnica documenta:
- Resultado de tests por clase (comando + resultado)
- Suite consolidada (antes y después del módulo)
- Cobertura de código (JaCoCo en backend, Istanbul en frontend)
- Validación E2E (curl o Cypress)
- Regresiones en módulos anteriores

### Manejo de errores

- **Backend**: `GlobalExceptionHandler` (`@RestControllerAdvice`) maneja:
  - `ResourceNotFoundException` → **404** (entidad no encontrada)
  - `DuplicateResourceException` → **409** (unicidad violada)
  - `BusinessRuleException` → **422** (regla de negocio violada)
  - `MethodArgumentNotValidException` → **400** (validación de campos)
  - `ObjectOptimisticLockingFailureException` → **409** (concurrencia)
  - `RuntimeException` genérica → **500** (errores reales de infraestructura)
- **Frontend**: `error.interceptor.ts` actúa sobre:
  - **401** → redirige a `/login` con mensaje "sesión expirada"
  - **403** → muestra snackbar "Sin permiso"
  - **Resto** → `throwError()` — el componente lo maneja en su `catchError`
- **Regla**: los componentes siempre leen `err.error?.message` del body,
  independientemente del status code.

### Seguridad

- Contraseñas: BCrypt (backend) — nunca almacenar en texto plano
- Tokens: JWT en `Authorization: Bearer` header — nunca en query params o cookies sin `HttpOnly`
- Secretos: variables de entorno (`JWT_SECRET`, `DB_PASSWORD`) — nunca en código fuente
- RBAC verificado en dos puntos: SecurityConfig (backend) + AuthGuard (frontend)
- Rutas de Swagger y login son las únicas rutas públicas

### Variables de entorno

```bash
# Backend
DB_PASSWORD=...      # contraseña de PostgreSQL
JWT_SECRET=...       # mínimo 64 caracteres hex (openssl rand -hex 32)

# Frontend
# environment.ts / environment.prod.ts
# apiUrl: 'http://localhost:8080/api/v1'  (dev)
# apiUrl: 'https://api.tudominio.com/api/v1'  (prod)
```

---

## 8. Estado actual del proyecto

### Backend (`almacenes-backend`) — Completo

| Módulo | Estado | Tests | Notas |
|---|---|---|---|
| `auth` (RBAC) | ✓ Completo | 45 B* + 20 A + 12 B + 17 C | 4 roles, 9 endpoints, DataInitializer |
| `inventory` | ✓ Completo | 29 A + 16 B + 4 D | unitCost NOT NULL |
| `purchases` | ✓ Completo | 43 A + 25 B | Máquina de estados PENDING→APPROVED→RECEIVED |
| `sales` | ✓ Completo | 47 A + 25 B + 3 C + 5 D | Optimistic Locking, reservas |
| `reports` | ✓ Completo | 40 A + 14 B + 7 D | 12 endpoints, 3 audiencias |
| Swagger/OpenAPI | ✓ Completo | — | 62 paths documentados |
| Paginación | ✓ Completo | — | 9 endpoints paginados |

**Suite total backend**: 365 tests — 0 fallos — BUILD SUCCESS  
**Cobertura**: 84.6% líneas · 87.5% métodos · 61.6% ramas

### Frontend (`almacenes-frontend`) — En desarrollo

| Módulo | Estado | Tests | Notas |
|---|---|---|---|
| Módulo 0: Infra-base + Layout | ✓ Completo | 26 specs, 0 fallos | Angular 21, Material M2, sidebar+topbar+main-layout, tema #6B3C6B |
| Módulo 1: Auth + RBAC | ✓ Completo | 43 specs, 0 fallos | AuthService, JWT interceptor, error interceptor, authGuard, LoginComponent, filtrado sidebar por rol |
| Módulo 2: Inventory | ✓ Completo — ⬜ Specs unitarios pendientes | 15/15 browser + 4 roles RBAC + 17 seguridad backend | Mergeado a develop. RBAC 4 roles verificado en browser y backend. HTTP 404/409/422 corregidos. |
| Módulo 3: Purchases | ⬜ Pendiente | | |
| Módulo 4: Sales | ⬜ Pendiente | | |
| Módulo 5: Reports | ⬜ Pendiente | | |

**Suite total frontend (Módulos 0-1)**: 43 specs — 0 fallos — cobertura 98.09% statements, 100% funciones

---

## 9. Lecciones aprendidas globales

Estas son lecciones que trascienden un módulo específico y aplican al proyecto completo.

### L1: Implementar Swagger y paginación desde el primer módulo

**Problema**: se implementaron al final del backend. Esto obligó a actualizar
9 archivos de test y cambiar el contrato de respuesta de los endpoints paginados.

**Regla**: Swagger + paginación se configuran en el setup inicial del proyecto,
antes del primer módulo de negocio. La paginación cambia el formato de respuesta
de `[...]` a `{content: [...], totalPages: ...}` — si el frontend ya fue diseñado
para el primero, hay que rediseñarlo.

### L2: Los mocks ocultan bugs de integración

**Problema**: varios bugs (supplierId null en BD, approvedById null, regla de
SecurityConfig demasiado permisiva) solo se detectaron en pruebas E2E con datos
reales, no en los tests automatizados con mocks.

**Regla**: los tests con mocks verifican la lógica interna. Para verificar que
el sistema funciona de extremo a extremo, son necesarios los tests `@SpringBootTest`
(Tipo C) y la validación E2E con curl o Cypress. No dar un módulo por terminado
sin al menos uno de los dos.

### L3: El orden de las reglas en SecurityConfig es crítico

**Problema**: una regla general `GET /reports/inventory/**` con
`hasAnyRole("ADMIN","MANAGER","WAREHOUSEMAN")` permitía acceso a WAREHOUSEMAN
a endpoints analíticos que debían ser solo ADMIN+MANAGER.

**Regla**: las reglas más específicas van antes que las generales. Siempre
agregar tests Tipo B* en `SecurityFilterTest` para cada nueva regla de
`SecurityConfig`. Los tests Tipo B con `addFilters=false` no detectan este tipo
de error.

### L4: `updatable=false` solo para campos inmutables desde el INSERT

**Problema**: `PurchaseOrder.approvedBy` tenía `updatable=false`. El valor
se seteaba en memoria pero nunca llegaba a la BD. La respuesta inmediata
mostraba el valor correcto (en memoria); un GET posterior retornaba null (de BD).

**Regla**: `updatable=false` solo para `created_at` y `created_by`.
Campos como `approved_by`, `received_by`, `cancelled_by` — que empiezan
null y se asignan en un UPDATE posterior — **nunca** deben tener `updatable=false`.

### L5: unitCost nullable rompe la integridad financiera

**Problema**: `Product.unitCost` y `SaleOrderDetail.unitCost` eran nullable
por "captura progresiva". Cuando se implementó el módulo `reports`, todas
las queries de COGS requerían `WHERE unitCost IS NOT NULL`.

**Regla**: los campos usados en cálculos financieros deben ser NOT NULL desde
el diseño del schema. Si el dato no está disponible al crear el registro, el
registro no debe crearse hasta que esté.

### L7: Angular 21 — los callbacks de error HTTP no siempre disparan detección de cambios

**Problema**: tras un error de credenciales en el login, el botón "Iniciar sesión" permanecía
en estado spinner hasta que el usuario hacía clic en otro elemento. `this.loading = false`
se ejecutaba correctamente pero Angular no propagaba el cambio a la vista.

**Causa**: en Angular 21, los callbacks del observable en el `error` handler del
`HttpClient` pueden ejecutarse fuera del contexto de zone.js, lo que impide que
el motor de detección de cambios por defecto recoja la mutación de estado.

**Regla**: en componentes que mutan estado dentro de callbacks de error de HTTP,
llamar `ChangeDetectorRef.detectChanges()` inmediatamente después del cambio.
Alternativa más moderna: usar señales Angular (`signal()`) que actualizan la vista
de forma reactiva sin depender de zone.js.

### L8: Verificar contratos de API contra OpenAPI ANTES de escribir código frontend

**Problema (Frontend Módulo 2 — Inventory)**:
- El endpoint `GET /api/v1/inventory/products` fue listado en la propuesta como existente → no existía al implementar el frontend. **Implementado el 2026-06-06** con búsqueda combinada por sku/name y filtros opcionales (categoryId, status, supplierId).
- `POST /movement` fue documentado como retornando `StockMovementResponseDTO` → retorna 204 void.
- El campo del nombre del proveedor fue asumido como `name` → el backend usa `companyName`.
- Los tres errores se propagaron al código y solo se detectaron en el browser con datos reales.

**Regla**: antes de escribir cualquier servicio o interfaz TypeScript que consuma el backend,
verificar contra el OpenAPI real (`http://localhost:8080/v3/api-docs` o Swagger UI):
1. Que el endpoint existe con la ruta y método HTTP exactos.
2. El HTTP status code de la respuesta (200, 201, 204, etc.).
3. Los nombres exactos de los campos en el request body y en el response.
4. Si la respuesta es `PageResponse<T>`, objeto simple o void.

Documentar los contratos verificados en la Sección 4 de la memoria técnica del módulo
ANTES de escribir código. Una propuesta con contratos incorrectos propaga el error al código.

**Impacto**: bugs de integración que pasan desapercibidos en tests (que usan mocks) y
solo se manifiestan en el browser con datos reales, requiriendo debugging costoso.

### L9: Usar excepciones tipadas para errores de negocio — nunca RuntimeException genérica

**Problema (Frontend Módulo 2 — post-desarrollo)**: todos los errores de negocio del backend
(entidad no encontrada, SKU duplicado, stock insuficiente) retornaban HTTP 500, igual que
un crash del servidor. Esto hacía imposible distinguir errores esperados de errores reales
en los logs de producción, y violaba la semántica REST.

**Causa**: `GlobalExceptionHandler` mapeaba toda `RuntimeException` a 500. Los servicios
no diferenciaban tipos de error.

**Regla**: crear una jerarquía mínima de excepciones de negocio desde el inicio de cada
módulo. El patrón estándar para este proyecto:
- `ResourceNotFoundException` → HTTP 404 (no encontrado)
- `DuplicateResourceException` → HTTP 409 (unicidad violada)
- `BusinessRuleException` → HTTP 422 (regla de negocio violada)
- `RuntimeException` genérica → HTTP 500 (solo para errores reales de infraestructura)

Esta jerarquía ya está implementada en `core/exception/` desde el **2026-06-07**.
Todo módulo nuevo debe usar estas clases en sus servicios desde el primer commit.

### L6: Los secretos en el código fuente son permanentes

**Problema**: la clave JWT fue hardcodeada en `JwtUtils.java` en el primer
commit y queda en el historial de git para siempre.

**Regla**: las variables sensibles van en `application.yaml` como
`${VARIABLE_ENTORNO:valor_dev}` desde el primer commit. Una vez que un
secreto entra al historial de git, debe considerarse comprometido.

---

## 10. Roadmap

### Backend — pendiente

| Item | Prioridad | Notas |
|---|---|---|
| Módulo `returns` (devoluciones) | Media | Depende del flujo de UX del frontend para definir estados |
| Tests B* en `SecurityFilterTest` para Swagger | Baja | Swagger usa sus propios endpoints — requiere `@SpringBootTest` |
| Paginación en endpoints de `reports` | Baja | Reports son analíticos — datos ya agregados, volumen bajo |

### Frontend — en curso

| Módulo | Estado | Dependencias |
|---|---|---|
| ~~Módulo 0: Setup + Layout~~ | ✓ Completo | — |
| ~~Módulo 1: Auth + RBAC~~ | ✓ Completo | Módulo 0 |
| ~~Módulo 2: Inventory~~ | ✓ Completo (specs unitarios pendientes) | Módulo 1 |
| Módulo 3: Purchases | ⬜ Siguiente | Módulo 2 |
| Módulo 4: Sales | ⬜ Pendiente | Módulo 3 |
| Módulo 5: Reports | ⬜ Pendiente | Módulo 4 |

### Módulos de negocio futuros (ambas capas)

| Módulo | Descripción |
|---|---|
| `returns` | Devoluciones de clientes y a proveedores. Requiere definir UX primero |
| `notifications` | Alertas automáticas por stock bajo, órdenes pendientes. Canal: email / websocket |
| `locations` | Gestión de ubicaciones físicas (zona → pasillo → estante → posición) |
| `shipping` | Gestión de transportistas, guías de envío, tracking |

---

*Memoria técnica global — Sistema de Gestión de Almacenes*  
*Actualizar al finalizar cada módulo del frontend si hay nuevas decisiones transversales*
