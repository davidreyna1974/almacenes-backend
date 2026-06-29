# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto se adhiere a [Versionado Semántico](https://semver.org/lang/es/).

## [1.0.0] — 2026-06-28

Primera versión estable. Sistema certificado bajo el Protocolo de QA de 4 fases.

### Añadido
- **Autenticación y usuarios:** login JWT stateless, gestión de usuarios, perfil, cambio de
  contraseña, rate limiting/lockout de login (`LoginAttemptService`).
- **Inventario:** categorías, productos, movimientos de stock (Kardex) con `availableStock`,
  alertas de stock bajo. Búsqueda insensible a acentos vía `f_unaccent()`.
- **Compras:** proveedores y órdenes con máquina de estados `PENDING → APPROVED → RECEIVED / CANCELLED`.
- **Ventas:** clientes, órdenes y reservaciones con control de stock disponible.
- **Reportes:** dashboard ejecutivo, reportes analíticos (rentabilidad, tendencia, top productos,
  ABC, por proveedor) y operativos (stock bajo, Kardex, movimientos, rotación).
- **Seguridad transversal:** RBAC por endpoint, redacción de campos sensibles server-side por rol (L29),
  `JwtAuthenticationEntryPoint` / `JwtAccessDeniedHandler` (401 vs 403).
- **Documentación API:** springdoc-openapi (Swagger UI).
- **Despliegue:** `Dockerfile`, scripts de puesta en producción e instructivo.

### Corregido
- `MethodArgumentTypeMismatchException` → HTTP **400** (antes 500), sin filtrar el tipo interno
  (`LocalDate`) en el cuerpo de la respuesta. Manejado en `GlobalExceptionHandler`.

### Calidad
- **406 tests** (JUnit 5) · 0 fallos · BUILD SUCCESS.
- Campaña de QA de 4 fases coordinada con el frontend: 704 casos de prueba, 5 módulos certificados,
  0 bugs funcionales, 0 regresiones.
- Verificación de seguridad server-side con `curl` + JWT por rol (enforcement RBAC + redacción de campos).

[1.0.0]: https://github.com/davidreyna1974/almacenes-backend/releases/tag/v1.0.0
