# Almacenes — Backend (API REST)

> API REST para la **gestión de almacenes**: inventario, compras, ventas, reportes y administración de usuarios, con autenticación JWT y control de acceso por rol (RBAC) de extremo a extremo.

![versión](https://img.shields.io/badge/versión-1.0.0-6B3C6B)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Java](https://img.shields.io/badge/Java-17-007396)
![tests](https://img.shields.io/badge/tests-408%20passing-2E7D32)
![QA](https://img.shields.io/badge/QA-certificado%20(4%20fases)-1565C0)
![licencia](https://img.shields.io/badge/licencia-MIT-757575)

Este repositorio contiene el **backend Spring Boot**. Es parte de un sistema de dos capas:

| Capa | Repositorio | Tecnología |
|---|---|---|
| Frontend (cliente web) | [`almacenes-frontend`](https://github.com/davidreyna1974/almacenes-frontend) | Angular 21 + Angular Material |
| **Backend** (este repo) | `almacenes-backend` | Spring Boot 3 + PostgreSQL |
| Visión del sistema completo | [`almacenes`](https://github.com/davidreyna1974/almacenes) | Documentación / portafolio |

---

## 📋 Tabla de contenidos
- [Descripción](#-descripción)
- [Stack tecnológico](#-stack-tecnológico)
- [Arquitectura](#-arquitectura)
- [Módulos y API](#-módulos-y-api)
- [Seguridad (JWT + RBAC)](#-seguridad-jwt--rbac)
- [Cómo ejecutar](#-cómo-ejecutar)
- [Calidad y QA](#-calidad-y-qa)
- [Despliegue](#-despliegue)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [Documentación](#-documentación)
- [Licencia](#-licencia)

---

## 🎯 Descripción

**Almacenes** es un sistema de gestión de inventario de escala media diseñado para operar un almacén
real. Este backend expone una **API REST** bajo `/api/v1` que registra productos y categorías, controla
el stock mediante movimientos (Kardex), gestiona órdenes de compra y de venta con sus máquinas de estado,
y genera reportes ejecutivos, analíticos y operativos. El acceso está segregado por **4 roles**
(ADMIN, MANAGER, WAREHOUSEMAN, SALES) y los campos sensibles (costos, márgenes, límites de crédito)
se **redactan server-side** según el rol del solicitante.

- **Base URL:** `http://localhost:8080/api/v1`
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI:** `http://localhost:8080/v3/api-docs`

---

## 🛠️ Stack tecnológico

- **Framework:** Spring Boot 3.5 (Web, Data JPA, Validation)
- **Lenguaje:** Java 17
- **Base de datos:** PostgreSQL (con extensión `unaccent` para búsqueda insensible a acentos)
- **Seguridad:** Spring Security + JWT (`jjwt`) — autenticación stateless y autorización por rol
- **Mapeo DTO:** MapStruct
- **Documentación API:** springdoc-openapi (Swagger UI)
- **Tests:** JUnit 5 + Spring Security Test + JaCoCo (cobertura)
- **Build:** Maven (`mvnw` incluido)
- **Contenedor:** Dockerfile incluido

---

## 🏗️ Arquitectura

Arquitectura en capas (controller → service → repository) con módulos de negocio independientes y un
núcleo transversal (seguridad, manejo global de excepciones, configuración). Diagrama y decisiones
detalladas en [`docs/arquitectura/`](docs/arquitectura/) — ver el
[diagrama de arquitectura](docs/arquitectura/diagrama_arquitectura.md).

```
com.codigo2enter.almacenes/
├── core/         seguridad (JWT, RBAC), manejo global de excepciones, configuración
└── modules/      auth · inventory · purchases · sales · reports
                  (cada uno: controller · service · repository · dto · entity · mapper)
```

---

## 🧩 Módulos y API

| Módulo | Responsabilidad | Ejemplos de endpoints |
|---|---|---|
| **auth** | Login, gestión de usuarios, perfil, cambio de contraseña | `POST /auth/login`, `POST /auth/users` |
| **inventory** | Categorías, productos, movimientos de stock (Kardex) | `GET /inventory/products`, `POST /inventory/movements` |
| **purchases** | Proveedores y órdenes de compra (`PENDING → APPROVED → RECEIVED / CANCELLED`) | `GET /purchases/orders`, `PATCH /purchases/orders/{id}/approve` |
| **sales** | Clientes, órdenes de venta y reservaciones (`PENDING → APPROVED → DELIVERED / CANCELLED`) | `GET /sales/orders`, `PATCH /sales/orders/{id}/deliver` |
| **reports** | Dashboard ejecutivo, reportes analíticos y operativos | `GET /reports/dashboard/executive`, `GET /reports/operations/pending` |

Contrato completo de cada endpoint en Swagger UI y en la
[memoria técnica global](docs/arquitectura/memoria_tecnica_global_proyecto.md) (sección de contratos).

---

## 🔐 Seguridad (JWT + RBAC)

- **Autenticación JWT** stateless: `POST /auth/login` devuelve un token con los roles; expira en 2 h.
- **Autorización por rol** en cada endpoint (Spring Security) — la matriz RBAC completa está en la memoria global.
- **Redacción de campos sensibles server-side** (L29): costos, márgenes y límites de crédito se
  devuelven `null` para los roles no autorizados — no solo se ocultan en el cliente.
- **Errores coherentes:** `401` (no autenticado / token inválido o manipulado) vs `403` (autenticado sin permiso),
  mediante `JwtAuthenticationEntryPoint` / `JwtAccessDeniedHandler`.
- **Rate limiting / lockout de login** (`LoginAttemptService`) — bloqueo tras intentos fallidos repetidos.
- **Manejo global de excepciones** (`GlobalExceptionHandler`): parámetros malformados → `400` sin filtrar
  tipos internos; validaciones → `400`/`422` con mensaje útil.
- **Búsqueda insensible a acentos** vía función `f_unaccent()` de PostgreSQL (nativeQuery).

---

## 🚀 Cómo ejecutar

### Requisitos
- Java 17+
- PostgreSQL (base de datos creada y configurada)
- Maven (o usar el wrapper `./mvnw`)

### Pasos
```bash
# 1. Configurar la conexión a PostgreSQL
#    (variables de entorno o src/main/resources/application.properties)

# 2. Ejecutar en desarrollo (http://localhost:8080)
./mvnw spring-boot:run

# 3. Tests
./mvnw test

# 4. Tests con cobertura (JaCoCo)
./mvnw clean test
#    informe: target/site/jacoco/index.html

# 5. Empaquetar
./mvnw clean package
```

**Usuarios de prueba** (con datos seed):

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `Admin123!` | ADMIN |
| `qa_manager` | `QaManager123!` | MANAGER |
| `qa_warehouse` | `QaWarehouse123!` | WAREHOUSEMAN |
| `qa_sales` | `QaSales123!` | SALES |

---

## ✅ Calidad y QA

Este proyecto se sometió a una **campaña de QA formal bajo un Protocolo de 4 fases** (congelamiento de
código, corrección con gatekeeper, re-ejecución y certificación), coordinada con el frontend.

- **408 tests** (JUnit 5) · 0 fallos · BUILD SUCCESS
- Verificación de seguridad server-side con `curl` + JWT por rol (enforcement RBAC + redacción de campos)
- Parte de los **704 casos de prueba** del sistema; 5 módulos certificados, 0 bugs funcionales

📄 **Reporte de QA del sistema:** [`almacenes` (repo paraguas)](https://github.com/davidreyna1974/almacenes) ·
**Protocolo:** ver memoria técnica global, sección de QA.

---

## 📦 Despliegue

El repositorio incluye scripts e instructivo de puesta en producción. El despliegue
es **agnóstico del dominio**: el dominio se pasa como argumento a `02-ssl.sh`/`03-deploy.sh`
(no se edita ningún archivo).

- `scripts/` — scripts de despliegue a producción `01`–`05` (preparación de servidor, SSL, deploy, firewall, verificación) + `maint-db.sh`.
- `scripts/INSTRUCTIVO_puesta_produccion_almacenes.md` — procedimiento paso a paso de puesta en producción (servidor físico o VM).
- `scripts/guia_implementacion_vm_gcp_almacenes.txt` — guía de despliegue en una VM de Google Cloud (modo producción o modo prueba con limpieza de recursos).
- `Dockerfile` — imagen del backend.

---

## 📁 Estructura del proyecto

```
almacenes-backend/
├── src/main/java/com/codigo2enter/almacenes/
│   ├── core/            seguridad, excepciones, configuración
│   └── modules/         auth · inventory · purchases · sales · reports
├── src/test/            suites JUnit (408 tests)
├── docs/                documentación (ver docs/README.md)
│   ├── arquitectura/    memoria técnica global, diagrama
│   └── modulos/         memoria técnica por módulo
├── scripts/             despliegue a producción + instructivo
├── Dockerfile
├── CLAUDE.md            guía para asistentes de IA / convenciones del repo
├── CHANGELOG.md         historial de versiones
└── README.md           este archivo
```

---

## 📚 Documentación

Toda la documentación está indexada en **[`docs/README.md`](docs/README.md)**. Destacados:

- [Memoria técnica global](docs/arquitectura/memoria_tecnica_global_proyecto.md) — visión, decisiones, contratos de integración, RBAC, lecciones.
- [Diagrama de arquitectura](docs/arquitectura/diagrama_arquitectura.md) — diagramas Mermaid.
- [Memorias técnicas por módulo](docs/modulos/) — decisiones de diseño y bugs por módulo.
- [Instructivo de puesta en producción](scripts/INSTRUCTIVO_puesta_produccion_almacenes.md).

---

## 📄 Licencia

Distribuido bajo licencia **MIT**. Ver [`LICENSE`](LICENSE).

---

<sub>Proyecto de portafolio — David Reyna Pineda · 2026</sub>
