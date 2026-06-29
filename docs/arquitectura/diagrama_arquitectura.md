# Diagrama de arquitectura — Backend

> Diagramas en [Mermaid](https://mermaid.js.org/) (se renderizan automáticamente en GitHub).

## Vista de capas (sistema completo)

```mermaid
flowchart TB
    subgraph Cliente["🖥️ Frontend — Angular 21"]
        UI["Componentes + Servicios HTTP (RxJS)"]
    end
    subgraph API["⚙️ Backend — Spring Boot 3 (este repo)"]
        CTRL["Controllers REST<br/>/api/v1"]
        SEC["Spring Security<br/>JWT + RBAC + redacción por rol"]
        BIZ["Services<br/>(lógica de negocio)"]
        REPO["Repositories<br/>(JPA + queries nativas)"]
        CTRL --> SEC --> BIZ --> REPO
    end
    DB[("🗄️ PostgreSQL<br/>+ extensión unaccent")]
    UI -- "HTTPS + JWT" --> CTRL
    REPO --> DB
```

## Estructura interna del backend

```mermaid
flowchart LR
    subgraph core
        SECCFG["security<br/>JwtFilter · EntryPoint · AccessDeniedHandler<br/>LoginAttemptService"]
        EXC["exception<br/>GlobalExceptionHandler"]
        CFG["config<br/>SecurityConfig · OpenAPI"]
    end
    subgraph modules
        M1["auth<br/>(login · usuarios · perfil)"]
        M2["inventory"]
        M3["purchases"]
        M4["sales"]
        M5["reports"]
    end
    modules --> core
    M1 & M2 & M3 & M4 & M5 -.->|controller→service→repository→dto→entity→mapper| core
```

## Flujo de autenticación y autorización

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant F as JwtFilter
    participant S as Spring Security
    participant C as Controller
    FE->>C: POST /auth/login (usuario/contraseña)
    C-->>FE: 200 JWT (con roles)
    FE->>F: GET /reports/... (Bearer JWT)
    F->>F: valida firma + expiración
    alt token válido
        F->>S: autentica + roles
        alt rol autorizado
            S->>C: ejecuta endpoint
            C-->>FE: 200 (campos sensibles redactados por rol)
        else rol sin permiso
            S-->>FE: 403 (JwtAccessDeniedHandler)
        end
    else token inválido/manipulado/ausente
        F-->>FE: 401 (JwtAuthenticationEntryPoint)
    end
```

## Máquinas de estado (negocio)

```mermaid
flowchart LR
    subgraph Compras
        P1[PENDING] --> P2[APPROVED] --> P3[RECEIVED]
        P1 --> P4[CANCELLED]
        P2 --> P4
    end
    subgraph Ventas
        S1[PENDING] --> S2[APPROVED] --> S3[DELIVERED]
        S1 --> S4[CANCELLED]
        S2 --> S4
    end
```
