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
- **Spring Security** — Pendiente de configurar; el módulo `auth` es la base de implementación.

## Estado actual del proyecto

El proyecto está en fase inicial de andamiaje. Los directorios de módulos contienen `.gitkeep` como placeholders — la implementación real de controllers, services, repositories, models, DTOs y mappers aún está por desarrollar.

Rama activa de desarrollo: `feature/auth`
