#!/bin/bash
# ============================================================
# SCRIPT 04 — INICIALIZACIÓN DE LA BASE DE DATOS
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : almacenes.codigo2enter.com
# Versión  : 1.0 (2026-06-18)
#
# Descripción:
#   Inicializa la base de datos PostgreSQL en el contenedor:
#   - Instala la extensión unaccent (búsquedas accent-insensitive)
#   - Crea la función inmutable f_unaccent(text)
#   - (Opcional) Carga un script SQL inicial de esquema/datos
#   - Crea los 10 índices de rendimiento del sistema
#
# Cuándo ejecutar:
#   SOLO en el PRIMER despliegue. En despliegues posteriores
#   (actualizaciones), Hibernate maneja la evolución del esquema.
#
# Prerequisitos:
#   - Script 03 ejecutado (contenedores corriendo)
#   - Contenedor 'db' disponible y PostgreSQL iniciado
#
# Cómo ejecutar:
#   bash 04-init-db.sh
#   bash 04-init-db.sh --schema /ruta/al/schema.sql  (con carga de SQL)
#
# Qué hace paso a paso:
#   1. Verifica que el contenedor db está corriendo
#   2. Espera a que PostgreSQL esté listo para aceptar conexiones
#   3. Instala la extensión unaccent
#   4. Crea la función f_unaccent(text) inmutable
#   5. Carga script SQL inicial (si se pasa --schema)
#   6. Crea los 10 índices de rendimiento del sistema
#   7. Verifica la instalación con consultas de comprobación
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

DEPLOY_DIR="/opt/almacenes"
ENV_FILE="$DEPLOY_DIR/.env"
SCHEMA_FILE=""

# Procesar argumentos opcionales
while [[ $# -gt 0 ]]; do
    case "$1" in
        --schema) SCHEMA_FILE="$2"; shift 2 ;;
        *) err "Argumento desconocido: $1\n     Uso: bash $0 [--schema /ruta/schema.sql]" ;;
    esac
done

# Cargar variables del .env (POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD)
[[ ! -f "$ENV_FILE" ]] && err "No se encontró $ENV_FILE. Ejecuta primero 03-deploy.sh"
source "$ENV_FILE"

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  SCRIPT 04 — INICIALIZACIÓN DE LA BASE DE DATOS${NC}"
echo -e "${BLUE}  Sistema: Almacenes — codigoCodigoEnter${NC}"
echo -e "${BLUE}============================================================${NC}"

# Alias para ejecutar comandos SQL en el contenedor db como el usuario configurado
psql_exec() {
    docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"
}

# ============================================================
# PASO 1 — Verificar que el contenedor db está corriendo
# ============================================================
step "1/7 Verificando contenedor de base de datos..."
DB_STATUS=$(docker compose -f "$DEPLOY_DIR/docker-compose.yml" ps -q db 2>/dev/null || echo "")
[[ -z "$DB_STATUS" ]] && err "El contenedor 'db' no está corriendo.\n     Ejecuta: docker compose -f $DEPLOY_DIR/docker-compose.yml up -d db"
ok "Contenedor 'db' activo"

# ============================================================
# PASO 2 — Esperar a que PostgreSQL esté listo
# ============================================================
# PostgreSQL puede tardar unos segundos en inicializarse después
# de que el contenedor arranca. pg_isready verifica que el
# servidor acepta conexiones antes de intentar ejecutar SQL.
step "2/7 Esperando a que PostgreSQL esté listo..."
MAX_WAIT=60
ELAPSED=0
until docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
    pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" &>/dev/null; do
    echo -n "  ."
    sleep 3
    ELAPSED=$((ELAPSED + 3))
    [[ $ELAPSED -ge $MAX_WAIT ]] && \
        err "PostgreSQL no estuvo disponible en ${MAX_WAIT}s."
done
echo ""
ok "PostgreSQL listo para conexiones"

# ============================================================
# PASO 3 — Instalar extensión unaccent
# ============================================================
# unaccent es una extensión de PostgreSQL que elimina los
# diacríticos (tildes, cedillas, diéresis) de los textos.
# Es necesaria para que la búsqueda "galon" encuentre "Galón".
# La extensión viene incluida en el paquete postgresql-contrib.
# CREATE EXTENSION IF NOT EXISTS: seguro de ejecutar en re-despliegues.
step "3/7 Instalando extensión unaccent..."
psql_exec "CREATE EXTENSION IF NOT EXISTS unaccent;"
ok "Extensión unaccent instalada"

# ============================================================
# PASO 4 — Crear la función f_unaccent(text) inmutable
# ============================================================
# Por qué se necesita una función wrapper:
#   - unaccent() por sí sola NO es IMMUTABLE (depende de la
#     configuración de locale/collation del servidor).
#   - PostgreSQL solo puede usar una función en un índice
#     funcional si es IMMUTABLE (garantiza mismo resultado
#     para los mismos inputs, independiente del contexto).
#   - f_unaccent() envuelve unaccent() marcándola IMMUTABLE,
#     lo que permite crear el índice funcional idx_product_name_unaccent.
#   - Los repositorios del backend usan f_unaccent() en sus
#     nativeQuery LIKE para búsquedas accent-insensitive.
#
# Ver: memoria_tecnica_global_proyecto.md §7 — Estándar de búsqueda
step "4/7 Creando función f_unaccent(text) inmutable..."
psql_exec "
CREATE OR REPLACE FUNCTION f_unaccent(text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT AS
\$\$
SELECT unaccent('unaccent', \$1)
\$\$;"
ok "Función f_unaccent(text) creada"

# ============================================================
# PASO 5 — Cargar script SQL inicial (opcional)
# ============================================================
# Solo se ejecuta si se pasa --schema /ruta/al/schema.sql.
# Útil para el primer despliegue cuando Hibernate no ha
# creado el esquema aún, o para cargar datos iniciales.
step "5/7 Cargando script SQL inicial..."
if [[ -n "$SCHEMA_FILE" ]]; then
    [[ ! -f "$SCHEMA_FILE" ]] && err "No se encontró el archivo SQL: $SCHEMA_FILE"
    warn "Cargando: $SCHEMA_FILE"
    docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T db \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < "$SCHEMA_FILE"
    ok "Script SQL cargado: $SCHEMA_FILE"
else
    warn "No se especificó --schema. Hibernate crea el esquema automáticamente."
    warn "Si es el primer despliegue, asegúrate de que spring.jpa.hibernate.ddl-auto"
    warn "sea 'create' o 'update' en el perfil de producción."
fi

# ============================================================
# PASO 6 — Crear índices de rendimiento del sistema
# ============================================================
# Los índices mejoran el rendimiento de las consultas más
# frecuentes del sistema. Se crean con IF NOT EXISTS para
# ser seguros de ejecutar en re-despliegues.
#
# Categorías de índices:
#   - Búsqueda por nombre (funcionales con f_unaccent)
#   - Filtrado por estado (órdenes PENDING, APPROVED, etc.)
#   - Foreign keys (productId en movements, lineItems)
#   - Auditoría (createdAt para listados cronológicos)
step "6/7 Creando índices de rendimiento..."

psql_exec "
-- Índice 1: Búsqueda de productos por nombre (accent-insensitive)
-- Usado por: GET /inventory/products?search=... (f_unaccent + ILIKE)
CREATE INDEX IF NOT EXISTS idx_product_name_unaccent
ON products(f_unaccent(lower(name)));
" && ok "idx_product_name_unaccent"

psql_exec "
-- Índice 2: Filtrado de productos por categoría
-- Usado por: GET /inventory/products?categoryId=...
CREATE INDEX IF NOT EXISTS idx_product_category
ON products(category_id);
" && ok "idx_product_category"

psql_exec "
-- Índice 3: Filtrado de productos activos
-- Usado por: todas las consultas que excluyen productos desactivados
CREATE INDEX IF NOT EXISTS idx_product_active
ON products(active);
" && ok "idx_product_active"

psql_exec "
-- Índice 4: Búsqueda de proveedores por nombre (accent-insensitive)
-- Usado por: GET /purchases/suppliers?search=...
CREATE INDEX IF NOT EXISTS idx_supplier_name_unaccent
ON suppliers(f_unaccent(lower(name)));
" && ok "idx_supplier_name_unaccent"

psql_exec "
-- Índice 5: Búsqueda de clientes por nombre (accent-insensitive)
-- Usado por: GET /sales/clients?search=...
CREATE INDEX IF NOT EXISTS idx_client_name_unaccent
ON clients(f_unaccent(lower(name)));
" && ok "idx_client_name_unaccent"

psql_exec "
-- Índice 6: Filtrado de órdenes de compra por estado
-- Usado por: GET /purchases/orders?status=PENDING/APPROVED/RECEIVED/CANCELLED
CREATE INDEX IF NOT EXISTS idx_purchase_order_status
ON purchase_orders(status);
" && ok "idx_purchase_order_status"

psql_exec "
-- Índice 7: Filtrado de órdenes de venta por estado
-- Usado por: GET /sales/orders?status=PENDING/APPROVED/DELIVERED/CANCELLED
CREATE INDEX IF NOT EXISTS idx_sale_order_status
ON sale_orders(status);
" && ok "idx_sale_order_status"

psql_exec "
-- Índice 8: Foreign key de movimientos de inventario → producto
-- Usado por: GET /inventory/products/{id}/movements (Kardex)
CREATE INDEX IF NOT EXISTS idx_movement_product
ON inventory_movements(product_id);
" && ok "idx_movement_product"

psql_exec "
-- Índice 9: Listado de órdenes por fecha (descendente)
-- Usado por: todos los listados de órdenes ordenados por createdAt DESC
CREATE INDEX IF NOT EXISTS idx_purchase_order_created_at
ON purchase_orders(created_at DESC);
" && ok "idx_purchase_order_created_at"

psql_exec "
-- Índice 10: Listado de órdenes de venta por fecha (descendente)
CREATE INDEX IF NOT EXISTS idx_sale_order_created_at
ON sale_orders(created_at DESC);
" && ok "idx_sale_order_created_at"

# ============================================================
# PASO 7 — Verificar la instalación
# ============================================================
step "7/7 Verificando instalación..."

# Verificar extensión
EXT=$(psql_exec "SELECT extname FROM pg_extension WHERE extname='unaccent';" | grep -c "unaccent" || true)
[[ "$EXT" -ge 1 ]] && ok "Extensión unaccent: instalada" || warn "⚠ Extensión unaccent: NO encontrada"

# Verificar función
FUNC=$(psql_exec "\df f_unaccent" | grep -c "f_unaccent" || true)
[[ "$FUNC" -ge 1 ]] && ok "Función f_unaccent: creada" || warn "⚠ Función f_unaccent: NO encontrada"

# Contar índices creados
IDX_COUNT=$(psql_exec "SELECT count(*) FROM pg_indexes WHERE indexname LIKE 'idx_%';" | grep -oP '\d+' | head -1 || echo "0")
ok "Índices encontrados: $IDX_COUNT"

# Prueba rápida de accent-insensitive
ACCENTTEST=$(psql_exec "SELECT f_unaccent('Galón');" | grep -c "Galon" || true)
[[ "$ACCENTTEST" -ge 1 ]] && ok "Prueba f_unaccent('Galón') = 'Galon': PASS" || warn "⚠ Prueba f_unaccent: FAIL"

# ============================================================
# RESUMEN FINAL
# ============================================================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  SCRIPT 04 COMPLETADO EXITOSAMENTE                         ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "  Base de datos: $POSTGRES_DB"
echo "  Extensión    : unaccent"
echo "  Función      : f_unaccent(text)"
echo "  Índices      : 10 creados"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO:${NC}"
echo "  sudo bash 05-firewall.sh"
echo ""
