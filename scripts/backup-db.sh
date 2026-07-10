#!/bin/bash
# ============================================================
# BACKUP-DB — RESPALDO DE LA BASE DE DATOS  (Brecha 6 — Day-2 ops)
# Sistema : Almacenes — codigoCodigoEnter
# Versión : 1.0 (2026-07-10)
#
# Respaldo lógico de PostgreSQL (pg_dump) con:
#   - compresión (gzip)
#   - cifrado OPCIONAL (gpg) — RECOMENDADO para toda copia off-site
#   - copia off-site parametrizable
#   - rotación de copias locales (retención configurable)
#
# ⚠ REGLA DE ORO: un backup solo es ÚTIL si la RESTAURACIÓN está PROBADA.
#   El procedimiento de restore + el drill obligatorio están al pie de este
#   script y en el runbook de operación (sección "Backup y restauración").
#
# Uso (manual o vía cron):
#   bash backup-db.sh
#
# Variables (en $DEPLOY_DIR/.env o exportadas antes de invocar):
#   POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD  (ya vienen en el .env del deploy)
#   BACKUP_DIR             dir local de backups        (def: /opt/almacenes/backups)
#   BACKUP_RETENTION_DAYS  retención local en días      (def: 14 — cumple RPO 24h)
#   GPG_RECIPIENT          id/email de la clave GPG para cifrar (vacío = NO cifra + WARN)
#   OFFSITE_DEST           destino off-site (path/host/bucket)  (vacío = solo local + WARN)
#   OFFSITE_CMD            comando de copia off-site   (def: "cp"); p. ej.:
#                          "rsync -a" · "scp" · "aws s3 cp" · "rclone copyto"
#
# Cron sugerido (ACTIVACIÓN EN DEPLOY — documentado en el runbook):
#   0 3 * * *  cd /opt/almacenes && bash /opt/almacenes/backend/scripts/backup-db.sh \
#              >> /var/log/almacenes/backup.log 2>&1
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

DEPLOY_DIR="${DEPLOY_DIR:-/opt/almacenes}"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE="$DEPLOY_DIR/docker-compose.yml"

# Cargar variables del .env (POSTGRES_*)
[[ ! -f "$ENV_FILE" ]] && err "No se encontró $ENV_FILE. Ejecuta primero 03-deploy.sh"
# shellcheck disable=SC1090
source "$ENV_FILE"

BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_DIR/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
GPG_RECIPIENT="${GPG_RECIPIENT:-}"
OFFSITE_DEST="${OFFSITE_DEST:-}"
OFFSITE_CMD="${OFFSITE_CMD:-cp}"

STAMP="$(date +%Y-%m-%d_%H%M%S)"
BASENAME="almacenes_${POSTGRES_DB}_${STAMP}.sql.gz"
mkdir -p "$BACKUP_DIR"

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  BACKUP DE BASE DE DATOS — Almacenes                       ${NC}"
echo -e "${BLUE}============================================================${NC}"

# ============================================================
# PASO 1 — Verificar contenedor db
# ============================================================
step "1/4 Verificando contenedor de base de datos..."
[[ -z "$(docker compose -f "$COMPOSE" ps -q db 2>/dev/null || echo "")" ]] \
    && err "El contenedor 'db' no está corriendo."
ok "Contenedor 'db' activo"

# ============================================================
# PASO 2 — Dump lógico + compresión
# ============================================================
step "2/4 Generando dump ($BASENAME)..."
docker compose -f "$COMPOSE" exec -T db \
    pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" | gzip > "$BACKUP_DIR/$BASENAME"
[[ ! -s "$BACKUP_DIR/$BASENAME" ]] && err "El dump quedó vacío — revisar credenciales/estado de la BD."
ok "Dump generado: $(du -h "$BACKUP_DIR/$BASENAME" | cut -f1)"

# ============================================================
# PASO 3 — Cifrado (opcional pero recomendado) + off-site
# ============================================================
ARTIFACT="$BACKUP_DIR/$BASENAME"
if [[ -n "$GPG_RECIPIENT" ]]; then
    step "3/4 Cifrando con GPG ($GPG_RECIPIENT)..."
    gpg --yes --batch --encrypt --recipient "$GPG_RECIPIENT" "$ARTIFACT"
    rm -f "$ARTIFACT"
    ARTIFACT="${ARTIFACT}.gpg"
    ok "Artefacto cifrado: $(basename "$ARTIFACT")"
else
    warn "GPG_RECIPIENT vacío → el backup NO se cifra."
    warn "NO copies un backup sin cifrar a un destino off-site no confiable."
fi

if [[ -n "$OFFSITE_DEST" ]]; then
    step "    Copia off-site → $OFFSITE_DEST"
    # OFFSITE_CMD acepta comandos con flags (rsync -a, aws s3 cp, rclone copyto, scp)
    # shellcheck disable=SC2086
    $OFFSITE_CMD "$ARTIFACT" "$OFFSITE_DEST" && ok "Copia off-site realizada" \
        || err "Falló la copia off-site (revisar OFFSITE_CMD/OFFSITE_DEST)."
else
    warn "OFFSITE_DEST vacío → el backup queda SOLO en el host (riesgo: fallo del host)."
    warn "Define OFFSITE_DEST para cumplir el mínimo (regla 3-2-1)."
fi

# ============================================================
# PASO 4 — Rotación de copias locales
# ============================================================
step "4/4 Rotando copias locales (> ${BACKUP_RETENTION_DAYS} días)..."
find "$BACKUP_DIR" -maxdepth 1 -type f -name "almacenes_${POSTGRES_DB}_*" \
    -mtime +"$BACKUP_RETENTION_DAYS" -print -delete | sed 's/^/  purgado: /' || true
ok "Retención aplicada (${BACKUP_RETENTION_DAYS} días)"

echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  BACKUP COMPLETADO — $(basename "$ARTIFACT")               ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo -e "${YELLOW}  RECORDATORIO:${NC} un backup no probado no cuenta. Ejecuta el DRILL"
echo    "  de restauración periódicamente (ver el bloque de abajo y el runbook)."
echo ""

# ============================================================
# PROCEDIMIENTO DE RESTAURACIÓN  (referencia — ejecutar en el DRILL / ante desastre)
# ============================================================
# RTO objetivo: < 1 h.  Pasos (sobre una BD limpia para el drill, o la real ante desastre):
#
#   # 1) (si está cifrado) descifrar:
#   gpg --decrypt almacenes_..._.sql.gz.gpg > almacenes_..._.sql.gz
#
#   # 2) restaurar en una BD de prueba (drill) — crear una BD limpia primero:
#   docker compose -f /opt/almacenes/docker-compose.yml exec -T db \
#       psql -U "$POSTGRES_USER" -c "CREATE DATABASE almacenes_restore_test;"
#   gunzip -c almacenes_..._.sql.gz | \
#     docker compose -f /opt/almacenes/docker-compose.yml exec -T db \
#       psql -U "$POSTGRES_USER" -d almacenes_restore_test
#
#   # 3) verificar conteos/tablas clave contra lo esperado y MEDIR el tiempo (RTO real):
#   docker compose ... exec -T db psql -U "$POSTGRES_USER" -d almacenes_restore_test \
#       -c "SELECT count(*) FROM products; SELECT count(*) FROM sale_orders;"
#
#   # 4) limpiar la BD de prueba:
#   docker compose ... exec -T db psql -U "$POSTGRES_USER" -c "DROP DATABASE almacenes_restore_test;"
#
# Ante DESASTRE real: restaurar sobre $POSTGRES_DB (con el stack detenido para el backend),
# reejecutar maint-db.sh (extensión/función/índices) si aplica, y levantar el backend.
# ============================================================
