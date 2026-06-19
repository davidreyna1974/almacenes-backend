#!/bin/bash
# ============================================================
# SCRIPT 03 — DESPLIEGUE DE SERVICIOS DOCKER
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : almacenes.codigo2enter.com
# Versión  : 1.0 (2026-06-18)
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
#   - Scripots 01 y 02 ejecutados exitosamente
#   - Certificado SSL presente en /etc/letsencrypt/live/<dominio>/
#   - Sesión SSH reiniciada (para que el grupo docker surta efecto)
#   - environment.prod.ts en el repo frontend actualizado con
#     apiUrl: 'https://almacenes.codigo2enter.com/api/v1'
#
# Cómo ejecutar:
#   bash 03-deploy.sh
#   (sin sudo — el usuario ya pertenece al grupo docker)
#
# Qué hace paso a paso:
#   1. Verifica prerequisitos (Docker corriendo, certificado SSL)
#   2. Solicita variables de entorno de producción
#   3. Genera JWT_SECRET aleatorio con openssl
#   4. Crea /opt/almacenes/.env con las variables
#   5. Clona o actualiza los repositorios en /opt/almacenes/
#   6. Construye las imágenes Docker (docker compose build)
#   7. Levanta los contenedores (docker compose up -d)
#   8. Espera a que el backend responda en /actuator/health
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

DEPLOY_DIR="/opt/almacenes"
ENV_FILE="$DEPLOY_DIR/.env"
DOMAIN="${1:-almacenes.codigo2enter.com}"

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
step "1/8 Verificando prerequisitos..."

# ¿Docker está corriendo?
docker info &>/dev/null \
    || err "Docker no está corriendo o el usuario no tiene permisos.\n     Asegúrate de:\n     1. systemctl start docker\n     2. Haber cerrado y reabierto la sesión SSH (para el grupo docker)"
ok "Docker disponible"

# ¿Existe el directorio de despliegue?
[[ ! -d "$DEPLOY_DIR" ]] && \
    err "No se encontró $DEPLOY_DIR. Ejecuta primero 01-prepare-server.sh"
ok "Directorio $DEPLOY_DIR existe"

# ¿Existe el certificado SSL?
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
if [[ ! -f "$CERT_PATH/fullchain.pem" ]] || [[ ! -f "$CERT_PATH/privkey.pem" ]]; then
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
step "2/8 Configurando variables de entorno de producción..."
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
    step "3/8 Generando JWT_SECRET aleatorio..."
    JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n=')
    ok "JWT_SECRET generado (${#JWT_SECRET} caracteres)"

    # ============================================================
    # PASO 4 — Crear archivo .env en /opt/almacenes/
    # ============================================================
    # El .env es leído por docker-compose.yml como variables de
    # entorno para los contenedores. Formato: CLAVE=valor
    step "4/8 Creando $ENV_FILE..."
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
# PASO 5 — Clonar o actualizar los repositorios
# ============================================================
# Los repositorios se clonan en /opt/almacenes/backend y
# /opt/almacenes/frontend. Si ya existen, se actualiza al
# branch configurado en .env.
step "5/8 Actualizando repositorios en $DEPLOY_DIR..."

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

# Verificar que environment.prod.ts usa la URL correcta
PROD_ENV="$DEPLOY_DIR/frontend/src/environments/environment.prod.ts"
if [[ -f "$PROD_ENV" ]]; then
    if grep -q "almacenes.codigo2enter.com" "$PROD_ENV"; then
        ok "environment.prod.ts usa el dominio correcto"
    else
        warn "⚠ environment.prod.ts NO contiene 'almacenes.codigo2enter.com'"
        warn "  Verifica que apiUrl apunte a https://almacenes.codigo2enter.com/api/v1"
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
step "6/8 Construyendo imágenes Docker..."
echo "  (puede tardar 3-8 minutos — compilando Java y Angular)"
docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$ENV_FILE" build --no-cache
ok "Imágenes construidas"

# ============================================================
# PASO 7 — Levantar los contenedores
# ============================================================
# IMPORTANTE: El backend usa ddl-auto: validate en producción.
# Esto significa que Hibernate NO crea las tablas — solo las
# valida. Si las tablas no existen, el backend falla al arrancar.
#
# En el PRIMER despliegue (BD vacía) hay que:
#   1. Levantar solo el contenedor db
#   2. Cargar schema.sql con 04-init-db.sh --schema
#   3. Levantar backend y frontend
#
# En RE-despliegues las tablas ya existen — levantar todo junto.
step "7/8 Levantando contenedores..."

# Verificar si las tablas ya existen (re-despliegue vs primer despliegue)
DB_TABLES_EXIST=false
if docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d db 2>/dev/null; then
    # Esperar a que PostgreSQL esté listo
    for i in $(seq 1 20); do
        if docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
            pg_isready -U almacenes_user -d almacenes_db &>/dev/null; then
            break
        fi
        sleep 2
    done
    TABLE_COUNT=$(docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
        psql -U almacenes_user -d almacenes_db -tAc \
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';" 2>/dev/null | tr -d '[:space:]' || echo "0")
    [[ "$TABLE_COUNT" =~ ^[0-9]+$ ]] && [[ "$TABLE_COUNT" -ge 5 ]] && DB_TABLES_EXIST=true
fi

if [[ "$DB_TABLES_EXIST" == "false" ]]; then
    echo ""
    warn "════════════════════════════════════════════════════════"
    warn "  PRIMER DESPLIEGUE DETECTADO — BD vacía (0 tablas)"
    warn ""
    warn "  El backend usa ddl-auto: validate y NO crea tablas."
    warn "  Debes cargar el esquema ANTES de levantar el backend."
    warn ""
    warn "  Abre otra terminal en el servidor y ejecuta:"
    warn "  bash ~/scripts-almacenes/04-init-db.sh --schema \\"
    warn "    /opt/almacenes/backend/src/main/resources/schema.sql"
    warn ""
    warn "  El comando anterior crea las 12 tablas, índices y"
    warn "  la extensión unaccent en la BD."
    warn "════════════════════════════════════════════════════════"
    echo ""
    read -rp "  Presiona Enter cuando 04-init-db.sh haya completado..." _
fi

# Levantar backend y frontend (la DB ya está corriendo)
docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d
ok "Contenedores iniciados"

# Mostrar estado de los contenedores
docker compose -f "$DEPLOY_DIR/docker-compose.yml" ps

# ============================================================
# PASO 8 — Esperar a que el backend esté disponible
# ============================================================
# Spring Boot puede tardar 30-90 segundos en inicializarse
# (conexión a BD, Hibernate, carga de contexto Spring).
# El endpoint /actuator/health devuelve HTTP 200 cuando
# el backend está listo para recibir peticiones.
# Esperamos hasta 120 segundos antes de declarar fallo.
step "8/8 Esperando a que el backend esté disponible..."
MAX_WAIT=120
ELAPSED=0
INTERVAL=5

echo "  (timeout: ${MAX_WAIT}s)"
until docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T backend \
    curl -sf http://localhost:8080/actuator/health &>/dev/null; do
    echo -n "  ."
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
    if [[ $ELAPSED -ge $MAX_WAIT ]]; then
        echo ""
        err "El backend no respondió en ${MAX_WAIT}s.\n     Si es el primer despliegue: verifica que 04-init-db.sh --schema se ejecutó correctamente.\n     Logs: docker compose -f $DEPLOY_DIR/docker-compose.yml logs backend"
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
echo "  bash 04-init-db.sh"
echo ""
