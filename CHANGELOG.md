# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto se adhiere a [Versionado Semántico](https://semver.org/lang/es/).

## [No publicado]

## [1.1.1] — 2026-07-10

Documentación, organización de repositorios y gobernanza de seguridad (sin cambios funcionales).

### Añadido
- **Gobernanza de seguridad (Brecha 4):** `SECURITY.md` (canal *GitHub Private Vulnerability
  Reporting*), Dependabot (`maven` + `github-actions`, agrupado minor/patch) y workflow `security.yml`
  con **OWASP dependency-check** (schedule semanal + manual, `failBuildOnCVSS=7`).

### Cambiado
- **Organización documental:** los archivos `CLAUDE*.md` dejan de versionarse (gitignorados; la
  plantilla vive solo en el directorio de plantillas). La documentación general del sistema (memoria
  técnica global y planes de implementación) se centraliza en el repo paraguas `almacenes`; este
  repositorio conserva solo su documentación propia. Enlaces de README e índices de `docs/` actualizados.
- Actualización de dependencias minor/patch (Dependabot).

## [1.1.0] — 2026-07-08

Pipeline de CI/CD, corrección de raíz del sembrado de roles y repositorio público.

### Añadido
- **CI/CD (GitHub Actions):** `ci.yml` (service PostgreSQL + `schema.sql` + `./mvnw verify`,
  **412 tests** + JaCoCo) y `cd.yml` (publica la imagen Docker en **GHCR**, tag SHA + `latest`).
  Badges de CI/CD en el README.
- **`RoleInitializer`:** siembra automática e idempotente de los 4 roles de referencia al arrancar
  (`@Order(1)`, antes de `DataInitializer`), eliminando la inserción manual de roles. +4 tests (408 → 412).

### Corregido
- Prueba de despliegue en GCP (10 hallazgos): scripts `02-ssl`/`03-deploy`/`05-verify` (dominio como
  argumento obligatorio, `certbot.timer` + renewal-hooks, sondeo con `wget`, verificación de cert con
  `sudo`) y documentación (INSTRUCTIVO, guía GCP).

### Cambiado
- Repositorio **público**; branch protection activa (require PR + check de CI). Despliegue agnóstico
  del dominio (se pasa como argumento a los scripts).

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
- **Observabilidad:** Spring Boot Actuator con `/actuator/health` público (BACK-I8),
  para smoke tests post-deploy y probes de balanceadores.

### Corregido
- `MethodArgumentTypeMismatchException` → HTTP **400** (antes 500), sin filtrar el tipo interno
  (`LocalDate`) en el cuerpo de la respuesta. Manejado en `GlobalExceptionHandler`.
- `Dockerfile`: se crea `/var/log/almacenes` con el propietario correcto para el usuario no-root,
  de modo que logback (perfil prod) pueda escribir el log con rotación.

### Calidad
- **408 tests** (JUnit 5) · 0 fallos · BUILD SUCCESS. _(La campaña de QA de 4 fases certificó
  406; el mantenimiento post-certificación de Actuator añadió 2 tests → 408.)_
- Campaña de QA de 4 fases coordinada con el frontend: 704 casos de prueba, 5 módulos certificados,
  0 bugs funcionales, 0 regresiones.
- Verificación de seguridad server-side con `curl` + JWT por rol (enforcement RBAC + redacción de campos).

[No publicado]: https://github.com/davidreyna1974/almacenes-backend/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/davidreyna1974/almacenes-backend/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/davidreyna1974/almacenes-backend/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/davidreyna1974/almacenes-backend/releases/tag/v1.0.0
