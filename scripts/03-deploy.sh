#!/bin/bash
# ============================================================
# SCRIPT 03 — DESPLIEGUE DE SERVICIOS DOCKER
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : (se pasa como argumento — agnóstico del dominio)
# Versión  : 1.1 (2026-07-06)
#
# Descripción:
#   - Solicita interactivamente las variables de entorno de
#     producción y crea el archivo .env en /opt/almacenes/.
#   - Clona (o actualiza) los repositorios backend y frontend
#     en /opt/almacenes/backend y /opt/almacenes/frontend.
#   - Construye las imágenes Docker y levanta los contenedores.
#   - Espera a que el backend esté disponible antes de salir.
#
# Prerequisitos:
#   - Scripts 01 y 02 ejecutados exitosamente
#   - Certificado SSL presente en /etc/letsencrypt/live/<dominio>/
#   - Sesión SSH reiniciada (para que el grupo docker surta efecto)
#   - environment.prod.ts usa apiUrl relativo ('/api/v1') — el frontend
#     es agnóstico del dominio; nginx resuelve el dominio vía ${DOMAIN}
#
# Cómo ejecutar (el dominio es OBLIGATORIO):
#   bash 03-deploy.sh <dominio>
#   bash 03-deploy.sh almacenes.codigo2enter.com       (producción)
#   bash 03-deploy.sh mi-subdominio.duckdns.org        (modo prueba)
#   (sin sudo — el usuario ya pertenece al grupo docker)
#
# Qué hace paso a paso:
#   1. Verifica prerequisitos (Docker corriendo, certificado SSL)
#   2. Solicita variables de entorno de producción
#   3. Genera JWT_SECRET aleatorio con openssl
#   4. Crea /opt/almacenes/.env con las variables
#   5. Clona o actualiza los repositorios en /opt/almacenes/
#   6. Construye las imágenes Docker (docker compose build)
#   7. Levanta DB, inicializa esquema (1er despliegue) y crea índices
#   8. Levanta backend y frontend
#   9. Espera a que el backend responda en /actuator/health
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

DEPLOY_DIR="/opt/almacenes"
ENV_FILE="$DEPLOY_DIR/.env"

# El dominio es OBLIGATORIO — no se asume ningún dominio por defecto (el
# despliegue es agnóstico del dominio). Antes usaba ${1:-almacenes.codigo2enter.com}:
# ejecutar sin argumento asumía el dominio corporativo y fallaba confusamente
# ("Certificado SSL no encontrado en .../almacenes.codigo2enter.com"). (finding #7 prueba GCP)
[[ $# -lt 1 ]] && err "Falta el dominio. Uso: bash 03-deploy.sh <dominio>  (ej. mi-subdominio.duckdns.org)"
DOMAIN="$1"

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  SCRIPT 03 — DESPLIEGUE DE SERVICIOS DOCKER${NC}"
echo -e "${BLUE}  Sistema: Almacenes — codigoCodigoEnter${NC}"
echo -e "${BLUE}============================================================${NC}"

# ============================================================
# PASO 1 — Verificar prerequisitos
# ============================================================
# Verificar que Docker está corriendo, que el usuario tiene
# acceso al socket Docker (grupo docker), y que el certificado
# SSL existe (generado en el script 02).
step "1/9 Verificando prerequisitos..."

# ¿Docker está corriendo?
docker info &>/dev/null \
    || err "Docker no está corriendo o el usuario no tiene permisos.\n     Asegúrate de:\n     1. systemctl start docker\n     2. Haber cerrado y reabierto la sesión SSH (para el grupo docker)"
ok "Docker disponible"

# ¿Existe el directorio de despliegue?
[[ ! -d "$DEPLOY_DIR" ]] && \
    err "No se encontró $DEPLOY_DIR. Ejecuta primero 01-prepare-server.sh"
ok "Directorio $DEPLOY_DIR existe"

# ¿Existe el certificado SSL?
# Este script corre SIN sudo (el usuario está en el grupo docker), pero
# Let's Encrypt deja /etc/letsencrypt/live y /archive accesibles solo por root.
# Un `[[ -f ]]` como usuario no-root daba falso (no puede atravesar el dir) →
# error engañoso "Certificado SSL no encontrado" aunque el cert existiera.
# Usamos `sudo test -f` para verificar como root. (finding #8 prueba GCP)
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
if ! sudo test -f "$CERT_PATH/fullchain.pem" || ! sudo test -f "$CERT_PATH/privkey.pem"; then
    err "Certificado SSL no encontrado en $CERT_PATH.\n     Ejecuta primero: sudo bash 02-ssl.sh $DOMAIN"
fi
ok "Certificado SSL presente en $CERT_PATH"

# ============================================================
# PASO 2 — Solicitar variables de entorno de producción
# ============================================================
# El archivo .env contiene las credenciales de producción.
# NUNCA se commitea al repositorio (está en .gitignore).
# Se solicitan interactivamente para que el operador no tenga
# que editar archivos de configuración manualmente.
step "2/9 Configurando variables de entorno de producción..."
echo ""

if [[ -f "$ENV_FILE" ]]; then
    warn "Ya existe un archivo .env en $ENV_FILE"
    read -rp "  ¿Sobreescribir el archivo .env existente? (s/N): " OVERWRITE
    [[ "${OVERWRITE,,}" != "s" ]] && { warn "Se conserva el .env existente."; }
fi

if [[ ! -f "$ENV_FILE" ]] || [[ "${OVERWRITE,,}" == "s" ]]; then
    # Solicitar contraseña de la base de datos
    echo -e "  ${YELLOW}Ingresa la contraseña para la base de datos PostgreSQL:${NC}"
    echo -e "  ${YELLOW}(recomendado: mínimo 20 caracteres, letras, números y símbolos)${NC}"
    read -rsp "  DB_PASSWORD: " DB_PASSWORD
    echo ""
    [[ -z "$DB_PASSWORD" ]] && err "La contraseña de la base de datos no puede estar vacía."
    [[ ${#DB_PASSWORD} -lt 12 ]] && warn "La contraseña es corta. Se recomienda mínimo 20 caracteres."

    # Solicitar rama del backend a desplegar
    echo -e "  ${YELLOW}Rama del backend a desplegar [main]:${NC}"
    read -rp "  BACKEND_BRANCH: " BACKEND_BRANCH
    BACKEND_BRANCH="${BACKEND_BRANCH:-main}"

    # Solicitar rama del frontend a desplegar
    echo -e "  ${YELLOW}Rama del frontend a desplegar [main]:${NC}"
    read -rp "  FRONTEND_BRANCH: " FRONTEND_BRANCH
    FRONTEND_BRANCH="${FRONTEND_BRANCH:-main}"

    # ============================================================
    # PASO 3 — Generar JWT_SECRET aleatorio con openssl
    # ============================================================
    # openssl rand genera bytes aleatorios criptográficamente seguros.
    # -base64 64 produce 64 bytes (512 bits) codificados en base64.
    # tr -d '\n=' elimina saltos de línea y signos = del padding.
    # 512 bits cumple ampliamente con NIST SP 800-132 para HMAC-SHA256/512.
    step "3/9 Generando JWT_SECRET aleatorio..."
    JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n=')
    ok "JWT_SECRET generado (${#JWT_SECRET} caracteres)"

    # ============================================================
    # PASO 4 — Crear archivo .env en /opt/almacenes/
    # ============================================================
    # El .env es leído por docker-compose.yml como variables de
    # entorno para los contenedores. Formato: CLAVE=valor
    step "4/9 Creando $ENV_FILE..."
    cat > "$ENV_FILE" <<EOF
# ============================================================
# VARIABLES DE PRODUCCIÓN — Almacenes — codigoCodigoEnter
# Generado: $(date '+%Y-%m-%d %H:%M:%S')
# ⚠ NO COMMITEAR ESTE ARCHIVO AL REPOSITORIO
# ============================================================

# Base de datos
POSTGRES_DB=almacenes_db
POSTGRES_USER=almacenes_user
POSTGRES_PASSWORD=${DB_PASSWORD}

# Backend Spring Boot
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/almacenes_db
SPRING_DATASOURCE_USERNAME=almacenes_user
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
SPRING_PROFILES_ACTIVE=prod
CORS_ALLOWED_ORIGINS=https://${DOMAIN}

# Dominio
DOMAIN=${DOMAIN}

# Ramas desplegadas
BACKEND_BRANCH=${BACKEND_BRANCH}
FRONTEND_BRANCH=${FRONTEND_BRANCH}
EOF

    chmod 600 "$ENV_FILE"
    ok ".env creado con permisos 600 (solo el propietario puede leer)"
fi

# ============================================================
# PASO 4b — Generar docker-compose.yml
# ============================================================
# Genera el archivo de orquestación Docker en /opt/almacenes/.
# Solo se crea si no existe (idempotente).
# Estructura de 3 servicios:
#   db       : PostgreSQL 16 — datos en volumen persistente
#   backend  : Spring Boot (Dockerfile del repo backend) — red interna
#   frontend : nginx con Angular + SSL (Dockerfile del repo frontend)
#              monta /etc/letsencrypt como volumen read-only para TLS
# ============================================================
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
if [[ ! -f "$COMPOSE_FILE" ]]; then
    step "5/9 Generando docker-compose.yml..."
    # Leer el dominio del .env que se acaba de crear
    source "$ENV_FILE"
    cat > "$COMPOSE_FILE" <<'COMPOSE_EOF'
services:

  db:
    image: postgres:16-alpine
    container_name: almacenes-db
    restart: unless-stopped
    environment:
      POSTGRES_DB:       ${POSTGRES_DB}
      POSTGRES_USER:     ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    expose:
      - "5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: almacenes-backend
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE:    ${SPRING_PROFILES_ACTIVE}
      SPRING_DATASOURCE_URL:     ${SPRING_DATASOURCE_URL}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
      JWT_SECRET:                ${JWT_SECRET}
      CORS_ALLOWED_ORIGINS:      ${CORS_ALLOWED_ORIGINS}
    expose:
      - "8080"

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: almacenes-frontend
    restart: unless-stopped
    depends_on:
      - backend
    environment:
      # nginx (template del image) sustituye DOMAIN en la ruta del certificado al
      # arrancar. El filtro NGINX_ENVSUBST_FILTER=DOMAIN ya va horneado en el
      # Dockerfile del frontend (solo sustituye la variable DOMAIN).
      DOMAIN: ${DOMAIN}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      # Certificados Let's Encrypt montados como read-only
      - /etc/letsencrypt:/etc/letsencrypt:ro

volumes:
  postgres_data:

networks:
  default:
    name: almacenes-network
COMPOSE_EOF
    ok "docker-compose.yml generado en $COMPOSE_FILE"
else
    warn "docker-compose.yml ya existe — se conserva el existente."
fi

# ============================================================
# PASO 5 — Clonar o actualizar los repositorios
# ============================================================
# Los repositorios se clonan en /opt/almacenes/backend y
# /opt/almacenes/frontend. Si ya existen, se actualiza al
# branch configurado en .env.
step "6/9 Actualizando repositorios en $DEPLOY_DIR..."

# Cargar variables del .env para obtener las ramas
source "$ENV_FILE"

# Backend
if [[ -d "$DEPLOY_DIR/backend/.git" ]]; then
    warn "Repositorio backend ya existe. Actualizando..."
    git -C "$DEPLOY_DIR/backend" fetch origin
    git -C "$DEPLOY_DIR/backend" checkout "$BACKEND_BRANCH"
    git -C "$DEPLOY_DIR/backend" pull origin "$BACKEND_BRANCH"
    ok "Backend actualizado a rama '$BACKEND_BRANCH'"
else
    warn "El directorio backend está vacío."
    warn "Clona manualmente el repositorio backend:"
    warn "  cd $DEPLOY_DIR/backend"
    warn "  git clone https://github.com/davidreyna1974/almacenes-backend.git ."
    warn "  git checkout $BACKEND_BRANCH"
    read -rp "  Presiona Enter cuando el backend esté clonado..." _
fi

# Frontend
if [[ -d "$DEPLOY_DIR/frontend/.git" ]]; then
    warn "Repositorio frontend ya existe. Actualizando..."
    git -C "$DEPLOY_DIR/frontend" fetch origin
    git -C "$DEPLOY_DIR/frontend" checkout "$FRONTEND_BRANCH"
    git -C "$DEPLOY_DIR/frontend" pull origin "$FRONTEND_BRANCH"
    ok "Frontend actualizado a rama '$FRONTEND_BRANCH'"
else
    warn "El directorio frontend está vacío."
    warn "Clona manualmente el repositorio frontend:"
    warn "  cd $DEPLOY_DIR/frontend"
    warn "  git clone <url-del-repo-frontend> ."
    warn "  git checkout $FRONTEND_BRANCH"
    read -rp "  Presiona Enter cuando el frontend esté clonado..." _
fi

# Verificar que environment.prod.ts usa la URL RELATIVA (agnóstica del dominio).
# Con nginx sirviendo la SPA y proxeando /api/ en el mismo origen, el frontend NO
# necesita el dominio: apiUrl debe ser '/api/v1'. Así el mismo build sirve para
# cualquier dominio (producción, otro cliente, o una prueba con DuckDNS).
PROD_ENV="$DEPLOY_DIR/frontend/src/environments/environment.prod.ts"
if [[ -f "$PROD_ENV" ]]; then
    if grep -q "apiUrl: '/api/v1'" "$PROD_ENV"; then
        ok "environment.prod.ts usa apiUrl relativo '/api/v1' (agnóstico del dominio)"
    else
        warn "⚠ environment.prod.ts no usa apiUrl relativo '/api/v1'"
        warn "  Debe ser  apiUrl: '/api/v1'  (nginx proxea /api/ en el mismo origen)."
    fi
fi

# ============================================================
# PASO 6 — Construir imágenes Docker
# ============================================================
# docker compose build construye las imágenes de backend y
# frontend a partir de sus Dockerfiles respectivos.
# --no-cache: garantiza que el build usa el código actual
#             (sin capas cacheadas obsoletas).
# Este paso puede tomar 3-8 minutos dependiendo del servidor.
step "7/9 Construyendo imágenes Docker..."
echo "  (puede tardar 3-8 minutos — compilando Java y Angular)"
docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$ENV_FILE" build --no-cache
ok "Imágenes construidas"

# ============================================================
# PASO 8 — Levantar la base de datos e inicializarla
# ============================================================
# El backend usa ddl-auto: validate — Hibernate NO crea tablas.
# Hay que garantizar que el esquema existe ANTES de levantar
# el backend. Este paso lo hace todo automáticamente:
#   a) Levanta el contenedor db
#   b) Espera a que PostgreSQL acepte conexiones
#   c) Instala la extensión unaccent (búsquedas accent-insensitive)
#   d) PRIMER DESPLIEGUE: carga schema.sql (que ya incluye f_unaccent) + inserta roles
#   e) Garantiza f_unaccent con CREATE OR REPLACE (idempotente post-carga)
#   f) Crea los índices de rendimiento (IF NOT EXISTS — idempotente)
#
# ORDEN IMPORTANTE: f_unaccent se crea con OR REPLACE *después* de cargar
# schema.sql, no antes. schema.sql usa CREATE FUNCTION (sin OR REPLACE), así
# que si f_unaccent ya existiera al cargar el esquema daría ERROR. El orden
# correcto es: schema.sql → OR REPLACE (garantía idempotente para re-despliegues).
step "8/9 Levantando base de datos e inicializando esquema..."

# Helper: ejecutar SQL en el contenedor db sin interacción
db_exec() {
    docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"
}

# Levantar el contenedor db
docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d db
ok "Contenedor db iniciado"

# Esperar a que PostgreSQL esté listo para aceptar conexiones
echo "  Esperando a que PostgreSQL esté listo..."
MAX_PG_WAIT=60
PG_ELAPSED=0
until docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
    pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" &>/dev/null; do
    echo -n "  ."
    sleep 3
    PG_ELAPSED=$((PG_ELAPSED + 3))
    [[ $PG_ELAPSED -ge $MAX_PG_WAIT ]] && err "PostgreSQL no respondió en ${MAX_PG_WAIT}s"
done
echo ""
ok "PostgreSQL listo"

# Extensión unaccent — idempotente (IF NOT EXISTS)
# Se instala aquí porque schema.sql también la necesita al cargarse.
db_exec "CREATE EXTENSION IF NOT EXISTS unaccent;" >/dev/null
ok "Extensión unaccent instalada"

# Detectar si es primer despliegue (tablas aún no existen)
TABLE_COUNT=$(db_exec "SELECT count(*) FROM information_schema.tables
    WHERE table_schema='public' AND table_type='BASE TABLE';" \
    2>/dev/null | grep -oP '\d+' | head -1 || echo "0")

if [[ "${TABLE_COUNT:-0}" -lt 5 ]]; then
    # ── PRIMER DESPLIEGUE: cargar esquema completo ────────────
    # schema.sql incluye CREATE FUNCTION f_unaccent (sin OR REPLACE).
    # Por eso f_unaccent NO debe existir antes de cargarlo.
    # Después de la carga usamos CREATE OR REPLACE para garantizar
    # idempotencia en re-despliegues futuros.
    warn "Primer despliegue — BD vacía, cargando schema.sql..."
    SCHEMA_SQL="$DEPLOY_DIR/backend/src/main/resources/schema.sql"
    [[ ! -f "$SCHEMA_SQL" ]] && \
        err "No se encontró $SCHEMA_SQL\n     Asegúrate de que el repositorio backend está clonado en $DEPLOY_DIR/backend/"
    docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
        -v ON_ERROR_STOP=1 < "$SCHEMA_SQL"
    ok "Esquema cargado (12 tablas, secuencias, constraints, índices)"

    # Insertar los 4 roles del sistema (requeridos por DataInitializer)
    db_exec "
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'), ('ROLE_MANAGER'), ('ROLE_WAREHOUSEMAN'), ('ROLE_SALES')
ON CONFLICT (name) DO NOTHING;" >/dev/null
    ok "Roles del sistema insertados"
else
    ok "Re-despliegue — esquema existente (${TABLE_COUNT} tablas encontradas)"
fi

# Garantizar f_unaccent con CREATE OR REPLACE — idempotente en todos los casos:
#   - Primer despliegue: schema.sql ya la creó; OR REPLACE la reemplaza sin error
#   - Re-despliegue: ya existe de despliegues anteriores; OR REPLACE la actualiza
# La definición usa public.unaccent('public.unaccent', $1) igual que schema.sql
# para consistencia con search_path vacío en el contexto de pg_dump.
db_exec "
CREATE OR REPLACE FUNCTION public.f_unaccent(text)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS
\$\$ SELECT public.unaccent('public.unaccent', \$1) \$\$;" >/dev/null
ok "Función f_unaccent(text) garantizada (CREATE OR REPLACE)"

# Índices de rendimiento — todos IF NOT EXISTS (idempotentes)
echo "  Creando índices de rendimiento..."
db_exec "CREATE INDEX IF NOT EXISTS idx_product_name_unaccent     ON products(f_unaccent(lower(name)));" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_product_category           ON products(category_id);" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_product_active             ON products(active);" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_supplier_company_unaccent  ON suppliers(f_unaccent(lower(company_name)));" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_client_name_unaccent       ON clients(f_unaccent(lower(name)));" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_purchase_order_status      ON purchase_orders(status);" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_sale_order_status          ON sale_orders(status);" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_stock_movement_product     ON stock_movements(product_id);" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_purchase_order_created_at  ON purchase_orders(created_at DESC);" >/dev/null
db_exec "CREATE INDEX IF NOT EXISTS idx_sale_order_created_at      ON sale_orders(created_at DESC);" >/dev/null
ok "10 índices de rendimiento verificados"

# Verificación rápida de accent-insensitive
FUNC_OK=$(db_exec "SELECT f_unaccent('Galón');" 2>/dev/null | grep -c "Galon" || true)
[[ "${FUNC_OK:-0}" -ge 1 ]] \
    && ok "Prueba f_unaccent('Galón') = 'Galon': PASS" \
    || warn "⚠ f_unaccent no funciona — verificar extensión unaccent"

# ============================================================
# PASO 8 — Levantar backend y frontend
# ============================================================
step "9/9 Levantando backend y frontend..."
docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d
ok "Contenedores iniciados"
docker compose -f "$DEPLOY_DIR/docker-compose.yml" ps

# ============================================================
# PASO 9 — Esperar a que el backend esté disponible
# ============================================================
# Spring Boot tarda 30-90 s en inicializarse (carga del contexto
# Spring, conexión a BD, validación Hibernate del esquema).
# /actuator/health retorna {"status":"UP"} cuando está listo.
echo "  Esperando a que el backend esté disponible..."
# MAX_WAIT configurable (env). 240s por defecto: en VM chica (e2-medium, 2 vCPU)
# el arranque de Spring Boot + validación Hibernate puede pasar de 120s. (finding #9 prueba GCP)
MAX_WAIT="${BACKEND_MAX_WAIT:-240}"
ELAPSED=0
INTERVAL=5

echo "  (timeout: ${MAX_WAIT}s — configurable con BACKEND_MAX_WAIT)"
# La imagen del backend (eclipse-temurin:17-jre-alpine) NO trae curl — solo el
# wget de busybox. Usar `curl` aquí daba SIEMPRE timeout ("exec: curl: not found")
# aunque el backend estuviera UP → el despliegue parecía fallar sin fallar. (finding #9 prueba GCP)
until docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T backend \
    wget -q -O- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; do
    echo -n "  ."
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
    if [[ $ELAPSED -ge $MAX_WAIT ]]; then
        echo ""
        err "El backend no respondió en ${MAX_WAIT}s.\n     Logs: docker compose -f $DEPLOY_DIR/docker-compose.yml exec backend tail -100 /var/log/almacenes/app.log"
    fi
done

echo ""
ok "Backend disponible en /actuator/health"

# ============================================================
# RESUMEN FINAL
# ============================================================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  SCRIPT 03 COMPLETADO EXITOSAMENTE                         ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "  Contenedores activos:"
docker compose -f "$DEPLOY_DIR/docker-compose.yml" ps --format "    {{.Name}} — {{.Status}}"
echo ""
echo "  .env: $ENV_FILE (permisos 600)"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO:${NC}"
echo "  sudo bash 04-firewall.sh"
echo ""
