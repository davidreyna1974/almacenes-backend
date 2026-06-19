# Instructivo de puesta en producción
## Sistema Almacenes — codigoCodigoEnter
## Dominio: https://almacenes.codigo2enter.com

---

## Resumen ejecutivo

Este instructivo describe el proceso completo para poner el sistema Almacenes en
producción por primera vez. Incluye los 5 scripts de automatización más los 4 pasos
manuales que los scripts no cubren: mergeo de ramas, carga de datos iniciales, cambio
de contraseñas por defecto y configuración de backup automático.

**Los pasos se ejecutan UNA SOLA VEZ, en el orden que se indica.**
Para actualizaciones posteriores, ver la sección "Actualizaciones de la aplicación".

**Tiempo estimado total del primer despliegue:** 45–90 minutos (incluyendo build Docker
y propagación DNS, que pueden variar).

---

## Arquitectura del sistema (resumen)

```
Internet → nginx (443/HTTPS) → Angular SPA  (frontend)
                             → /api/v1/*     (proxy → Spring Boot :8080)
                                                 ↕
                                           PostgreSQL :5432
                                     (red interna Docker — no expuesto)
```

Tres contenedores Docker orquestados con `docker compose`:

| Servicio    | Puerto interno | Puerto público | Imagen base          |
|-------------|---------------|----------------|----------------------|
| `db`        | 5432          | ninguno        | postgres:16-alpine   |
| `backend`   | 8080          | ninguno        | eclipse-temurin:21   |
| `frontend`  | 80 / 443      | 80, 443        | nginx:alpine         |

---

## Secuencia completa de puesta en producción

```
PREREQUISITOS
    [A] DNS apuntando al servidor
    [B] Mergeo develop → main en ambos repos
    [C] Copiar scripts al servidor
        │
        ▼
[01-prepare-server.sh]   → Docker instalado, /opt/almacenes/ creado
        │
        │  ← Cerrar y reabrir sesión SSH (obligatorio)
        │
        ▼
[02-ssl.sh dominio]      → Certificado SSL en /etc/letsencrypt/
        │
        │  ← Clonar repos en /opt/almacenes/ (si no existen)
        │
        ▼
[03-deploy.sh]           → .env creado, imágenes construidas, contenedores activos
        │
        ▼
[04-init-db.sh]          → unaccent, f_unaccent, 10 índices creados
        │
        ▼
[05-firewall.sh]         → ufw activo: 80/443 ALLOW · 8080/5432 DENY
        │
        ▼
[PASO MANUAL — OPS-B3]   → Carga de datos iniciales en la BD
        │
        ▼
[PASO MANUAL — OPS-B5]   → Cambio de contraseña del administrador
        │
        ▼
[PASO MANUAL — OPS-B1/B2] → Configuración de backup automático diario
        │
        ▼
[verify.sh]              → 8/8 smoke tests PASS → PRODUCCIÓN ACTIVA
        │
        ▼
https://almacenes.codigo2enter.com
```

---

## PREREQUISITOS (antes de ejecutar cualquier script)

### [A] Registro DNS configurado

El dominio `almacenes.codigo2enter.com` debe tener un registro A que apunte a la
IP pública del servidor de producción antes de ejecutar el script 02.

**Cómo verificar que el DNS está propagado:**

```bash
# Desde cualquier máquina con acceso a internet
nslookup almacenes.codigo2enter.com

# Resultado esperado (la IP debe ser la de tu servidor):
# Address: 203.0.113.X
```

> La propagación DNS puede tomar entre 5 minutos y 24 horas según el registrador
> de dominio. Configura el DNS primero y ejecuta el script 02 cuando el `nslookup`
> retorne la IP correcta.

---

### [B] Mergeo de ramas develop → main en ambos repositorios

Todo el desarrollo se realizó en la rama `develop`. Los scripts asumen que la rama
`main` contiene el código final listo para producción. Este paso es **obligatorio**
antes de clonar los repositorios en el servidor.

**Ejecutar en la máquina de desarrollo (no en el servidor):**

```bash
# ── BACKEND ──────────────────────────────────────────────────────────
cd /ruta/local/almacenes-backend

# Asegurarse de estar en develop y que está al día
git checkout develop
git pull origin develop

# Cambiar a main y mergear
git checkout main
git merge --no-ff develop -m "chore: merge develop → main — puesta en producción v1.0"
git push origin main

# Volver a develop para no trabajar en main
git checkout develop

# ── FRONTEND ─────────────────────────────────────────────────────────
cd /ruta/local/almacenes-frontend

git checkout develop
git pull origin develop
git checkout main
git merge --no-ff develop -m "chore: merge develop → main — puesta en producción v1.0"
git push origin main
git checkout develop
```

**Verificar que `environment.prod.ts` tiene la URL correcta** (debe estar así desde
la brecha FRONT-B1 ya resuelta):

```typescript
// src/environments/environment.prod.ts
export const environment = {
  production: true,
  apiUrl: 'https://almacenes.codigo2enter.com/api/v1',
  sentryDsn: ''   // opcional: reemplazar con DSN real de Sentry si se usa monitoreo
};
```

---

### [C] Requisitos del servidor

El servidor debe ser:
- **Ubuntu 22.04 LTS** o Ubuntu 24.04 LTS (también funciona en Debian 12)
- Mínimo recomendado: **2 vCPU, 4 GB RAM, 20 GB disco**
- Acceso SSH con usuario que tenga permisos sudo

**Copiar los scripts al servidor:**

```bash
# Desde la máquina de desarrollo
scp -r scripts/ usuario@IP-DEL-SERVIDOR:~/scripts-almacenes/

# Conectarse al servidor
ssh usuario@IP-DEL-SERVIDOR
cd ~/scripts-almacenes/
chmod +x *.sh
```

---

## Script 01 — Preparación del servidor

**Ejecutar como:** `sudo bash 01-prepare-server.sh`

**Qué hace:**
- Instala Docker Engine desde el repositorio oficial de Docker (no el `docker.io` de Ubuntu)
- Instala Docker Compose v2 como plugin (`docker compose`, con espacio)
- Agrega el usuario actual al grupo `docker`
- Habilita Docker para inicio automático con el servidor (`systemctl enable docker`)
- Crea la estructura `/opt/almacenes/backend` y `/opt/almacenes/frontend`

**Tiempo estimado:** 3–5 minutos

**Verificación manual después de ejecutar:**

```bash
docker --version          # Docker version 27.x.x, build ...
docker compose version    # Docker Compose version v2.x.x
ls /opt/almacenes/        # backend/  frontend/
```

> **ACCIÓN OBLIGATORIA después de este script:**
> Cerrar y reabrir la sesión SSH antes de continuar.
> Sin este paso, el comando `docker` fallará con "permission denied" en los scripts siguientes.
>
> ```bash
> exit          # cerrar sesión actual
> ssh usuario@IP-DEL-SERVIDOR   # nueva sesión
> cd ~/scripts-almacenes/
> ```

---

## Script 02 — Obtención del certificado SSL

**Ejecutar como:** `sudo bash 02-ssl.sh almacenes.codigo2enter.com`

**Qué hace:**
- Verifica que el registro DNS del dominio resuelve a este servidor
- Verifica que el puerto 80 está libre (lo necesita certbot para el challenge)
- Instala `certbot` (herramienta oficial de Let's Encrypt)
- Obtiene el certificado SSL/TLS gratuito (válido 90 días)
- Configura renovación automática via cron (`0 3 * * * certbot renew --quiet`)

**Tiempo estimado:** 2–3 minutos

**Prerequisito crítico:**
> El DNS debe apuntar al servidor ANTES de este paso.
> Let's Encrypt verifica que el dominio apunte al servidor haciendo una petición
> HTTP en el puerto 80. Si el DNS no resuelve correctamente, certbot falla.
> Let's Encrypt tiene un límite de **5 certificados por dominio por semana**.
> No ejecutes este script repetidamente en pruebas.

**Dónde quedan los certificados:**

```
/etc/letsencrypt/live/almacenes.codigo2enter.com/
├── fullchain.pem   ← certificado + cadena de confianza (para nginx)
└── privkey.pem     ← clave privada (protegida, solo lectura de root)
```

**Verificación manual después de ejecutar:**

```bash
ls -la /etc/letsencrypt/live/almacenes.codigo2enter.com/
# Debe mostrar fullchain.pem y privkey.pem

# Verificar configuración de renovación automática
sudo crontab -l | grep certbot
# Debe mostrar algo como: 0 3 * * * certbot renew --quiet
```

---

## Paso previo al script 03 — Clonar repositorios

Antes de ejecutar el script 03, los repositorios deben estar en el servidor.
El script 03 los detecta y ofrece la opción de clonarlos si no existen, pero
hacerlo manualmente antes evita interrupciones.

```bash
# Backend
cd /opt/almacenes/backend
git clone https://github.com/davidreyna1974/almacenes-backend.git .
git checkout main

# Frontend
cd /opt/almacenes/frontend
git clone https://github.com/davidreyna1974/almacenes-frontend.git .
git checkout main

# Verificar
ls /opt/almacenes/backend/src/    # debe mostrar main/, test/
ls /opt/almacenes/frontend/src/   # debe mostrar app/, environments/
```

---

## Script 03 — Despliegue de servicios Docker

**Ejecutar como:** `bash 03-deploy.sh`
*(sin sudo — el usuario ya está en el grupo docker tras reabrir la sesión)*

**Qué hace:**
- Si ya existe un `.env`, pregunta si sobreescribir (responde `N` si es re-despliegue)
- Solicita interactivamente la contraseña de PostgreSQL
- Genera un `JWT_SECRET` criptográficamente seguro (512 bits, `openssl rand`)
- Crea `/opt/almacenes/.env` con permisos `600` (solo el propietario puede leer)
- Construye las imágenes Docker (`docker compose build --no-cache`) — compila Java + Angular
- Levanta los 3 contenedores en background (`docker compose up -d`)
- Espera hasta 120 segundos a que el backend responda en `/actuator/health`

**Tiempo estimado:** 5–10 minutos (compilación Java + build Angular son lo más lento)

**Durante la ejecución, el script solicita:**

```
DB_PASSWORD: <contraseña para PostgreSQL>
  → Mínimo 20 caracteres, mezcla de letras, números y símbolos
  → Ejemplo de contraseña segura: Alm@c3n3s#Prod!2026
  → GUARDAR esta contraseña en un lugar seguro fuera del servidor

BACKEND_BRANCH: main      (presionar Enter para aceptar el default)
FRONTEND_BRANCH: main     (presionar Enter para aceptar el default)
```

**El archivo `.env` generado** queda en `/opt/almacenes/.env` con este contenido:

```env
POSTGRES_DB=almacenes_db
POSTGRES_USER=almacenes_user
POSTGRES_PASSWORD=<la que ingresaste>
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/almacenes_db
SPRING_DATASOURCE_USERNAME=almacenes_user
SPRING_DATASOURCE_PASSWORD=<la que ingresaste>
JWT_SECRET=<generado automáticamente — 512 bits>
SPRING_PROFILES_ACTIVE=prod
DOMAIN=almacenes.codigo2enter.com
```

> ⚠ **El archivo `.env` NUNCA debe subirse al repositorio.** Está en `.gitignore`.
> Guarda la contraseña de la BD y el `JWT_SECRET` fuera del servidor
> (gestor de contraseñas de la empresa, LastPass, 1Password, etc.).

**Verificación manual después de ejecutar:**

```bash
# Los tres contenedores deben estar corriendo
docker compose -f /opt/almacenes/docker-compose.yml ps

# Resultado esperado:
# NAME                STATUS
# almacenes-db-1      Up X minutes (healthy)
# almacenes-backend-1 Up X minutes
# almacenes-frontend-1 Up X minutes

# El backend debe responder
docker compose -f /opt/almacenes/docker-compose.yml exec backend \
  curl -sf http://localhost:8080/actuator/health
# Resultado esperado: {"status":"UP","components":{"db":{"status":"UP"},...}}
```

---

## Script 04 — Inicialización de la base de datos

**Ejecutar como:** `bash 04-init-db.sh`

**Qué hace:**
- Espera a que PostgreSQL esté listo para conexiones (`pg_isready`)
- Instala la extensión `unaccent` (búsquedas accent-insensitive: "galon" encuentra "Galón")
- Crea la función inmutable `f_unaccent(text)` necesaria para índices funcionales
- Crea los 10 índices de rendimiento del sistema (búsqueda, filtrado, FK, auditoría)
- Verifica la instalación con pruebas internas

**Tiempo estimado:** 1–2 minutos

> Este script es seguro de ejecutar múltiples veces (usa `IF NOT EXISTS`).
> En re-despliegues posteriores no hace daño ejecutarlo de nuevo.

**Verificación manual después de ejecutar:**

```bash
docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -d almacenes_db \
  -c "SELECT extname FROM pg_extension WHERE extname='unaccent';"
# Resultado esperado: unaccent

docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -d almacenes_db \
  -c "SELECT f_unaccent('Galón');"
# Resultado esperado: Galon

docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -d almacenes_db \
  -c "SELECT count(*) FROM pg_indexes WHERE indexname LIKE 'idx_%';"
# Resultado esperado: 10
```

---

## Script 05 — Configuración del firewall

**Ejecutar como:** `sudo bash 05-firewall.sh`

**Qué hace:**
- Permite puertos 22 (SSH), 80 (HTTP) y 443 (HTTPS)
- Deniega puertos 8080 (backend) y 5432 (PostgreSQL)
- Activa `ufw` con confirmación explícita del operador

**Tiempo estimado:** menos de 1 minuto

> **⚠ Antes de ejecutar:** Asegúrate de tener una sesión SSH de respaldo abierta.
> El script permite el puerto 22 ANTES de activar el firewall, pero si algo falla
> durante la activación, perderías el acceso.

**Verificación manual después de ejecutar:**

```bash
sudo ufw status numbered
# Resultado esperado:
# [ 1] 22/tcp  ALLOW IN  Anywhere
# [ 2] 80/tcp  ALLOW IN  Anywhere
# [ 3] 443/tcp ALLOW IN  Anywhere
# [ 4] 8080/tcp DENY IN  Anywhere
# [ 5] 5432/tcp DENY IN  Anywhere
```

---

## PASO MANUAL — [OPS-B3] Carga de datos iniciales

Una vez que el sistema está levantado y el firewall activo, el backend ha creado el
esquema de la BD automáticamente (via Hibernate `ddl-auto: update`). La BD existe pero
está vacía (solo el usuario `admin`). Hay dos opciones para poblarla:

### Opción A — Entrada manual por la UI (recomendada si los datos son pocos)

1. Abrir `https://almacenes.codigo2enter.com` en el navegador.
2. Iniciar sesión con `admin` / `Admin123!` (ver sección siguiente para cambiar contraseña).
3. Ir a **Inventario → Categorías** y crear las categorías de la empresa.
4. Ir a **Compras → Proveedores** y registrar los proveedores.
5. Ir a **Inventario → Productos** y crear los productos con su stock inicial.
6. Ir a **Ventas → Clientes** y registrar la cartera de clientes.

### Opción B — Carga de SQL (recomendada si ya existe un catálogo en otro sistema)

Si tienes datos exportados de un sistema anterior en formato SQL:

```bash
# Copiar el archivo SQL al servidor
scp /ruta/local/datos_produccion.sql usuario@IP-DEL-SERVIDOR:~/

# Ejecutar la carga
bash ~/scripts-almacenes/04-init-db.sh --schema ~/datos_produccion.sql
```

El script `04-init-db.sh --schema` carga el archivo SQL dentro del contenedor
de PostgreSQL. El SQL debe ser compatible con la estructura del esquema de
Almacenes (tablas: `categories`, `suppliers`, `products`, `clients`, etc.).

> **Nota sobre `seed_data.sql`:**
> El archivo `seed_data.sql` incluido en este directorio contiene datos de **prueba**
> generados durante el desarrollo (categorías genéricas, proveedores ficticios,
> productos sin precios reales). **No cargar `seed_data.sql` en producción.**
> Es solo para ambientes de desarrollo/testing.

**Verificación:**

```bash
# Verificar que hay categorías en la BD
docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -d almacenes_db \
  -c "SELECT count(*) FROM categories WHERE active = true;"

# Verificar desde la UI
# https://almacenes.codigo2enter.com → Inventario → Productos
# La tabla debe mostrar los productos cargados
```

---

## PASO MANUAL — [OPS-B5] Cambio de contraseñas antes del go-live

El sistema se inicializa con un único usuario administrador con credenciales conocidas
públicamente. **Deben cambiarse ANTES de publicar la URL del sistema.**

### Usuarios que existen al primer arranque

| Usuario | Contraseña | Rol   | Acción requerida |
|---------|-----------|-------|------------------|
| `admin` | `Admin123!` | ADMIN | Cambiar contraseña inmediatamente |

### Paso 1 — Cambiar la contraseña del administrador

El cambio se hace a través de la API del backend (el backend no tiene la pantalla
de usuario disponible aún en el frontend):

```bash
# 1a. Obtener un token JWT del admin (con la contraseña actual)
TOKEN=$(curl -s -X POST https://almacenes.codigo2enter.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token obtenido: ${TOKEN:0:50}..."

# 1b. Cambiar la contraseña usando el token
curl -s -X PUT https://almacenes.codigo2enter.com/api/v1/auth/me/password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "currentPassword": "Admin123!",
    "newPassword": "<NUEVA-CONTRASEÑA-SEGURA>"
  }'
# Resultado esperado: HTTP 204 (sin body = éxito)
```

**Requisitos de la nueva contraseña** (validados por el backend):
- Mínimo 8 caracteres
- Al menos una mayúscula, una minúscula, un número y un símbolo
- Ejemplo: `AlmProduccion@2026!`

> **Guardar la nueva contraseña en el gestor de contraseñas de la empresa.**

### Paso 2 — Crear usuarios para el equipo

Crear un usuario para cada persona que usará el sistema. El rol define qué puede hacer:

| Rol            | Acceso |
|----------------|--------|
| `ROLE_ADMIN`   | Todo el sistema + gestión de usuarios |
| `ROLE_MANAGER` | Inventario (lectura/escritura), Compras, Ventas, Reportes |
| `ROLE_WAREHOUSEMAN` | Inventario (lectura), Compras (solo recepción), Ventas (solo entrega) |
| `ROLE_SALES`   | Inventario (lectura), Ventas (completo) |

```bash
# Ejemplo: crear usuario para el jefe de almacén
TOKEN=$(curl -s -X POST https://almacenes.codigo2enter.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<NUEVA-CONTRASEÑA>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -X POST https://almacenes.codigo2enter.com/api/v1/auth/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "username": "jefe.almacen",
    "password": "Almacen@2026!",
    "email":    "jefe.almacen@codigo2enter.com",
    "roles":    ["ROLE_MANAGER"]
  }'
```

Repetir este comando para cada miembro del equipo, ajustando `username`, `password`,
`email` y `roles` según el cargo.

### Paso 3 — Verificar acceso con cada usuario

```bash
# Verificar que el nuevo usuario puede hacer login
curl -s -X POST https://almacenes.codigo2enter.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"jefe.almacen","password":"Almacen@2026!"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('Rol:', d.get('roles'))"
```

---

## PASO MANUAL — [OPS-B1/B2] Configuración de backup automático diario

El backup protege la base de datos PostgreSQL ante fallos del servidor, errores
humanos o corrupción de datos. Se configura un `pg_dump` diario con retención de
7 días locales, y opcionalmente copia offsite.

### Paso 1 — Crear el script de backup

```bash
sudo nano /opt/almacenes/backup.sh
```

Pegar este contenido:

```bash
#!/bin/bash
# ============================================================
# Backup diario de PostgreSQL — Almacenes
# Ejecutado por cron todos los días a las 02:00 AM
# Retención: 7 días locales
# ============================================================

set -euo pipefail

DEPLOY_DIR="/opt/almacenes"
BACKUP_DIR="/opt/almacenes/backups"
DATE=$(date '+%Y-%m-%d_%H-%M')
BACKUP_FILE="$BACKUP_DIR/almacenes_db_$DATE.sql.gz"
RETENTION_DAYS=7

# Cargar variables de entorno del archivo .env
# (para obtener POSTGRES_USER, POSTGRES_DB, POSTGRES_PASSWORD)
source "$DEPLOY_DIR/.env"

# Crear directorio de backups si no existe
mkdir -p "$BACKUP_DIR"

# Ejecutar pg_dump dentro del contenedor de PostgreSQL
# | gzip comprime el volcado (un backup de 1 GB típicamente queda en ~50-100 MB)
docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-password \
  | gzip > "$BACKUP_FILE"

echo "$(date '+%Y-%m-%d %H:%M:%S') — Backup creado: $BACKUP_FILE ($(du -sh "$BACKUP_FILE" | cut -f1))"

# Eliminar backups con más de RETENTION_DAYS días
find "$BACKUP_DIR" -name "almacenes_db_*.sql.gz" -mtime "+$RETENTION_DAYS" -delete
echo "$(date '+%Y-%m-%d %H:%M:%S') — Backups de más de $RETENTION_DAYS días eliminados"
```

```bash
# Dar permisos de ejecución
sudo chmod +x /opt/almacenes/backup.sh

# Crear directorio de backups
sudo mkdir -p /opt/almacenes/backups
```

### Paso 2 — Ejecutar el primer backup manualmente para verificar que funciona

```bash
bash /opt/almacenes/backup.sh

# Verificar que el archivo se creó y no está vacío
ls -lh /opt/almacenes/backups/
# Resultado esperado: almacenes_db_YYYY-MM-DD_HH-MM.sql.gz  (varios KB o MB)

# Verificar que el archivo es un gzip válido
gzip -t /opt/almacenes/backups/almacenes_db_*.sql.gz && echo "Archivo válido"
```

### Paso 3 — Configurar el cron para backup diario automático

```bash
# Editar el crontab del usuario que administra el sistema
crontab -e
```

Agregar esta línea al final del archivo:

```
# Backup diario de Almacenes a las 02:00 AM, log en /var/log/almacenes-backup.log
0 2 * * * bash /opt/almacenes/backup.sh >> /var/log/almacenes-backup.log 2>&1
```

```bash
# Verificar que el cron quedó registrado
crontab -l | grep backup
# Resultado esperado: 0 2 * * * bash /opt/almacenes/backup.sh ...
```

### Paso 4 — Verificar el procedimiento de restauración

**Es obligatorio probar la restauración antes del go-live.**
Restaurar en la BD de producción borra todos los datos existentes;
solo hacerlo si se necesita recuperar ante un desastre real.

```bash
# Procedimiento de restauración (ante un desastre real):

# 1. Identificar el backup a restaurar
ls -lt /opt/almacenes/backups/ | head -5

# 2. Detener el backend para evitar escrituras durante la restauración
docker compose -f /opt/almacenes/docker-compose.yml stop backend

# 3. Restaurar el backup
# ADVERTENCIA: este comando borra los datos actuales de la BD
gunzip -c /opt/almacenes/backups/almacenes_db_YYYY-MM-DD_HH-MM.sql.gz \
  | docker compose -f /opt/almacenes/docker-compose.yml exec -T db \
    psql -U almacenes_user -d almacenes_db

# 4. Reiniciar el backend
docker compose -f /opt/almacenes/docker-compose.yml start backend

# 5. Verificar que el backend está saludable
docker compose -f /opt/almacenes/docker-compose.yml exec backend \
  curl -sf http://localhost:8080/actuator/health
```

**Para verificar la restauración sin tocar producción** (recomendado antes del go-live):

```bash
# Crear una BD de prueba temporal dentro del mismo contenedor
docker compose -f /opt/almacenes/docker-compose.yml exec -T db \
  psql -U almacenes_user -c "CREATE DATABASE almacenes_restore_test;"

# Restaurar el backup en la BD de prueba
gunzip -c /opt/almacenes/backups/almacenes_db_*.sql.gz | \
  docker compose -f /opt/almacenes/docker-compose.yml exec -T db \
    psql -U almacenes_user -d almacenes_restore_test

# Verificar que las tablas existen y tienen datos
docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -d almacenes_restore_test \
  -c "SELECT 'categories', count(*) FROM categories
      UNION ALL SELECT 'products', count(*) FROM products
      UNION ALL SELECT 'users', count(*) FROM users;"

# Limpiar la BD de prueba
docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -c "DROP DATABASE almacenes_restore_test;"
```

### Paso 5 (opcional) — Copia offsite de los backups

Los backups locales protegen contra fallos de la aplicación, pero no contra
fallos del servidor (disco, datacenter). Configurar una copia a un destino externo:

```bash
# Opción A — Copiar a otro servidor via rsync/SSH
# Agregar al script backup.sh (después del pg_dump):
rsync -az /opt/almacenes/backups/ usuario@servidor-backup:/backups/almacenes/

# Opción B — Subir a un bucket S3 (requiere aws-cli instalado)
aws s3 cp "$BACKUP_FILE" s3://nombre-del-bucket/almacenes/
```

---

## Script verify.sh — Verificación post-despliegue (smoke tests)

**Ejecutar como:** `bash verify.sh`
**Con dominio custom:** `bash verify.sh almacenes.codigo2enter.com`

**8 pruebas automatizadas:**

| # | Qué verifica | Resultado esperado |
|---|---|---|
| 1 | Contenedores db, backend, frontend activos | `Up X minutes` |
| 2 | Backend `/actuator/health` | `{"status":"UP"}` |
| 3 | HTTP → HTTPS redirección | `HTTP 301` |
| 4 | HTTPS responde | `HTTP 200` |
| 5 | Certificado SSL válido y no expirado | PASS con fecha de vencimiento |
| 6 | API `/api/v1/auth/login` accesible | `HTTP 400` o `401` |
| 7 | SPA routing `/login` devuelve index.html | `HTTP 200` |
| 8 | Puerto 8080 bloqueado desde exterior | Connection refused |

**Resultado esperado:**

```
8/8 PASS — SISTEMA EN PRODUCCIÓN — TODAS LAS PRUEBAS PASARON
Acceso: https://almacenes.codigo2enter.com
```

Si alguna prueba falla, el script muestra el detalle del error y sale con
código de salida 1. Revisar los logs de diagnóstico:

```bash
docker compose -f /opt/almacenes/docker-compose.yml logs backend
docker compose -f /opt/almacenes/docker-compose.yml logs frontend
sudo ufw status numbered
```

---

## Actualizaciones de la aplicación (post primer despliegue)

Para actualizar el backend o frontend después del despliegue inicial:

```bash
# 1. En la máquina de desarrollo: mergear develop → main como en el Prerequisito B
#    git checkout main && git merge --no-ff develop && git push origin main

# 2. En el servidor: actualizar el repositorio
cd /opt/almacenes/backend   # o /frontend
git pull origin main

# 3. Reconstruir solo el servicio afectado
docker compose -f /opt/almacenes/docker-compose.yml build backend   # o frontend

# 4. Reiniciar solo ese servicio (sin downtime del otro)
docker compose -f /opt/almacenes/docker-compose.yml up -d --no-deps backend   # o frontend

# 5. Verificar
bash ~/scripts-almacenes/verify.sh
```

---

## Comandos de diagnóstico frecuentes

```bash
# Ver logs en tiempo real
docker compose -f /opt/almacenes/docker-compose.yml logs -f backend
docker compose -f /opt/almacenes/docker-compose.yml logs -f frontend
docker compose -f /opt/almacenes/docker-compose.yml logs -f db

# Ver estado de los tres contenedores
docker compose -f /opt/almacenes/docker-compose.yml ps

# Reiniciar un servicio sin derribar los otros
docker compose -f /opt/almacenes/docker-compose.yml restart backend

# Ejecutar SQL en la BD de producción
docker compose -f /opt/almacenes/docker-compose.yml exec db \
  psql -U almacenes_user -d almacenes_db

# Ver reglas de firewall
sudo ufw status numbered

# Verificar renovación del certificado SSL (simulación)
sudo certbot renew --dry-run

# Renovar certificado manualmente si está próximo a vencer
sudo certbot renew --force-renewal

# Ver estado del cron de backup
crontab -l | grep backup

# Ver el log del último backup
tail -20 /var/log/almacenes-backup.log

# Ver cuánto espacio ocupan los backups
du -sh /opt/almacenes/backups/

# Ver cuánto espacio queda en disco
df -h /opt/almacenes/
```

---

## Solución de problemas comunes

| Problema | Causa probable | Solución |
|---|---|---|
| `permission denied` al usar docker | Usuario no está en grupo docker | Cerrar y reabrir sesión SSH |
| certbot falla con `Connection refused` | DNS no apunta al servidor, o nginx está corriendo en 80 | Verificar DNS con `nslookup` y que el puerto 80 esté libre |
| Backend no arranca (FAIL en health) | Error de BD o JWT_SECRET vacío en `.env` | `docker compose logs backend` — buscar `ERROR` |
| `/login` devuelve 404 | nginx sin `try_files` para SPA | Verificar `nginx.conf` en el Dockerfile del frontend |
| Certificado SSL vencido | Cron de renovación no funciona | `sudo certbot renew --force-renewal` |
| Puerto 8080 accesible desde internet | Docker bypass de iptables | Ejecutar `sudo bash 05-firewall.sh` de nuevo |
| `pg_dump` falla en el backup | Contenedor `db` detenido | `docker compose -f /opt/almacenes/docker-compose.yml start db` |
| Login con `admin`/`Admin123!` falla | Contraseña ya fue cambiada o BD vacía | Usar la nueva contraseña o verificar que el backend arrancó correctamente |

---

## Checklist de go-live

Marcar cada ítem antes de publicar la URL del sistema:

```
Infraestructura
[ ] Servidor Ubuntu 22.04 LTS / 24.04 LTS con 2 vCPU, 4 GB RAM, 20 GB disco
[ ] DNS: almacenes.codigo2enter.com → IP del servidor (verificado con nslookup)
[ ] Repositorios: develop mergeado a main en backend y frontend
[ ] Script 01: Docker instalado, sesión SSH reabierta
[ ] Script 02: Certificado SSL obtenido y cron de renovación configurado
[ ] Script 03: Tres contenedores corriendo (db, backend, frontend)
[ ] Script 04: unaccent, f_unaccent y 10 índices creados
[ ] Script 05: Firewall activo — 8080 y 5432 bloqueados desde exterior

Datos y acceso
[ ] Carga de datos iniciales completada (Opción A UI o Opción B SQL)
[ ] Contraseña de admin cambiada (ya no es Admin123!)
[ ] Usuarios del equipo creados con sus roles correctos
[ ] Verificado que cada usuario puede hacer login

Backup
[ ] Script /opt/almacenes/backup.sh creado y con permisos +x
[ ] Primer backup ejecutado manualmente y archivo .sql.gz válido
[ ] Cron configurado (0 2 * * * bash /opt/almacenes/backup.sh)
[ ] Restauración probada en BD de prueba temporal

Verificación final
[ ] bash verify.sh → 8/8 PASS
[ ] Acceso https://almacenes.codigo2enter.com desde navegador externo
[ ] Login con usuario real (no admin)
```

---

*Documento generado junto con el plan de producción `docs/global/plan_salida_produccion_v1_almacenes.txt`.*
*Última actualización: 2026-06-19*
