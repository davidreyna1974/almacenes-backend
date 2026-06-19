#!/bin/bash
# ============================================================
# SCRIPT 02 — OBTENCIÓN DE CERTIFICADO SSL
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : almacenes.codigo2enter.com
# Versión  : 1.0 (2026-06-18)
#
# Descripción:
#   Instala certbot y obtiene el certificado SSL/TLS gratuito
#   de Let's Encrypt para el dominio de producción.
#   Configura la renovación automática via cron.
#
# Prerequisitos OBLIGATORIOS antes de ejecutar:
#   1. El registro DNS del dominio debe apuntar a la IP de
#      este servidor (verificar con: nslookup <dominio>)
#   2. El puerto 80 debe estar libre (certbot lo usa para el
#      challenge HTTP-01 de verificación de dominio)
#   3. Script 01 ejecutado exitosamente
#
# Cómo ejecutar:
#   sudo bash 02-ssl.sh almacenes.codigo2enter.com
#
# Qué hace paso a paso:
#   1. Verifica privilegios de root
#   2. Valida que se pasó el dominio como argumento
#   3. Verifica que el puerto 80 está libre
#   4. Verifica que el DNS resuelve al servidor
#   5. Instala certbot
#   6. Obtiene el certificado SSL (challenge HTTP-01)
#   7. Verifica que los archivos del certificado existen
#   8. Configura renovación automática via cron
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  SCRIPT 02 — OBTENCIÓN DE CERTIFICADO SSL${NC}"
echo -e "${BLUE}  Sistema: Almacenes — codigoCodigoEnter${NC}"
echo -e "${BLUE}============================================================${NC}"

# ============================================================
# PASO 1 — Verificar privilegios de root
# ============================================================
step "1/8 Verificando privilegios de root..."
[[ $EUID -ne 0 ]] && err "Ejecutar con sudo:\n     sudo bash $0 <dominio>"
ok "Ejecutando como root"

# ============================================================
# PASO 2 — Validar argumento de dominio
# ============================================================
# El dominio se pasa como primer argumento al script.
# Es el nombre DNS por el que será accesible la aplicación.
step "2/8 Validando argumento de dominio..."
[[ $# -lt 1 ]] && err "Falta el dominio.\n     Uso: sudo bash $0 <dominio>\n     Ej:  sudo bash $0 almacenes.codigo2enter.com"
DOMAIN="$1"
# Validar que el dominio tiene formato básico correcto (contiene al menos un punto)
[[ "$DOMAIN" != *.* ]] && err "El dominio '$DOMAIN' no parece válido. Ejemplo: almacenes.codigo2enter.com"
ok "Dominio: $DOMAIN"

# ============================================================
# PASO 3 — Verificar que el puerto 80 está libre
# ============================================================
# certbot usa el modo --standalone para obtener el certificado:
# levanta temporalmente un servidor web en el puerto 80 para
# responder al challenge HTTP-01 de Let's Encrypt.
# Si el puerto 80 ya está ocupado (por nginx, apache, etc.),
# certbot fallará con "Address already in use".
step "3/8 Verificando que el puerto 80 está libre..."
if ss -tlnp 2>/dev/null | grep -q ':80 '; then
    PROCESO=$(ss -tlnp | grep ':80 ' | awk '{print $NF}' | head -1)
    err "El puerto 80 está ocupado por: $PROCESO\n     Detén ese proceso antes de continuar."
fi
ok "Puerto 80 libre"

# ============================================================
# PASO 4 — Verificar resolución DNS del dominio
# ============================================================
# Let's Encrypt verifica la propiedad del dominio enviando
# una petición HTTP al dominio. Si el DNS no apunta a este
# servidor, la verificación fallará.
step "4/8 Verificando resolución DNS de '$DOMAIN'..."
# Obtener la IP pública de este servidor
SERVER_IP=$(curl -s --max-time 10 ifconfig.me 2>/dev/null \
         || curl -s --max-time 10 icanhazip.com 2>/dev/null \
         || echo "desconocida")
# Obtener la IP a la que resuelve el dominio
DOMAIN_IP=$(getent hosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | head -1 || echo "")

warn "IP pública de este servidor : ${SERVER_IP}"
warn "IP a la que resuelve $DOMAIN: ${DOMAIN_IP:-'(no resuelve — DNS no configurado)'}"

if [[ -z "$DOMAIN_IP" ]]; then
    err "El dominio '$DOMAIN' no resuelve a ninguna IP.\n     Configura el registro A en tu proveedor DNS antes de continuar."
fi

if [[ "$SERVER_IP" != "$DOMAIN_IP" ]]; then
    warn "Las IPs no coinciden. Let's Encrypt fallará si el DNS no apunta aquí."
    read -rp "  ¿Continuar de todas formas? (s/N): " CONT
    [[ "${CONT,,}" != "s" ]] && err "Operación cancelada. Corrige el DNS antes de continuar."
else
    ok "DNS verificado: $DOMAIN → $DOMAIN_IP"
fi

# ============================================================
# PASO 5 — Instalar certbot
# ============================================================
# certbot es la herramienta oficial de la Electronic Frontier
# Foundation (EFF) para obtener y renovar certificados de
# Let's Encrypt de forma automatizada.
step "5/8 Instalando certbot..."
if command -v certbot &>/dev/null; then
    warn "certbot ya está instalado: $(certbot --version 2>&1)"
else
    apt-get install -y -qq certbot
    ok "certbot instalado: $(certbot --version 2>&1)"
fi

# ============================================================
# PASO 6 — Obtener el certificado SSL
# ============================================================
# Flags usados:
#   --standalone      : certbot levanta un servidor en el puerto 80
#   --non-interactive : no hace preguntas (usa los valores dados)
#   --agree-tos       : acepta los términos de servicio de Let's Encrypt
#   --register-unsafely-without-email : omite el email de recuperación
#                       (para producción real se recomienda usar --email)
#   -d $DOMAIN        : el dominio para el que se emite el certificado
#
# Los certificados se guardan en:
#   /etc/letsencrypt/live/$DOMAIN/fullchain.pem  → certificado + cadena
#   /etc/letsencrypt/live/$DOMAIN/privkey.pem    → clave privada
#
# NOTA: Let's Encrypt tiene un límite de 5 certificados por dominio
# por semana. No ejecutes este paso repetidamente en pruebas.
step "6/8 Obteniendo certificado SSL para '$DOMAIN'..."
echo "  (esto puede tardar 30-60 segundos)"
certbot certonly \
    --standalone \
    --non-interactive \
    --agree-tos \
    --register-unsafely-without-email \
    -d "$DOMAIN"
ok "Certificado obtenido"

# ============================================================
# PASO 7 — Verificar que los archivos del certificado existen
# ============================================================
# Confirmar que certbot generó los archivos esperados antes
# de continuar con el despliegue Docker.
step "7/8 Verificando archivos del certificado..."
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
[[ ! -f "$CERT_PATH/fullchain.pem" ]] && \
    err "No se encontró $CERT_PATH/fullchain.pem"
[[ ! -f "$CERT_PATH/privkey.pem" ]] && \
    err "No se encontró $CERT_PATH/privkey.pem"

# Mostrar fecha de vencimiento del certificado
EXPIRY=$(openssl x509 -noout -enddate -in "$CERT_PATH/fullchain.pem" | cut -d= -f2)
ok "fullchain.pem existe"
ok "privkey.pem existe"
ok "Vencimiento: $EXPIRY"

# ============================================================
# PASO 8 — Configurar renovación automática via cron
# ============================================================
# Let's Encrypt emite certificados válidos por 90 días.
# certbot renew verifica si el certificado vence en menos de
# 30 días y lo renueva si es necesario.
#
# El cron se ejecuta diariamente a las 3:00 AM.
# pre-hook : detiene el contenedor frontend para liberar el
#            puerto 443 (nginx dentro del contenedor lo usa).
# post-hook: reinicia el contenedor frontend con el nuevo
#            certificado (que está montado como volumen :ro).
step "8/8 Configurando renovación automática del certificado..."
CRON_JOB="0 3 * * * certbot renew --quiet --pre-hook \"docker compose -f /opt/almacenes/docker-compose.yml stop frontend\" --post-hook \"docker compose -f /opt/almacenes/docker-compose.yml start frontend\""

if crontab -l 2>/dev/null | grep -q "certbot renew"; then
    warn "Ya existe una tarea cron para renovación de certificado. Se omite."
else
    (crontab -l 2>/dev/null; echo "$CRON_JOB") | crontab -
    ok "Cron configurado: renovación diaria a las 3:00 AM"
fi

# Verificar que el cron quedó registrado
ok "Cron activo: $(crontab -l | grep certbot)"

# ============================================================
# RESUMEN FINAL
# ============================================================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  SCRIPT 02 COMPLETADO EXITOSAMENTE                         ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "  Dominio    : $DOMAIN"
echo "  Certificado: $CERT_PATH/fullchain.pem"
echo "  Clave      : $CERT_PATH/privkey.pem"
echo "  Vencimiento: $EXPIRY"
echo "  Renovación : automática (cron diario 3:00 AM)"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO:${NC}"
echo "  bash 03-deploy.sh"
echo ""
