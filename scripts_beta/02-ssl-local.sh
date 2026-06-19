#!/bin/bash
# ============================================================
# 02-SSL-LOCAL — CERTIFICADO AUTOFIRMADO PARA BETA LOCAL
# Reemplaza 02-ssl.sh (Let's Encrypt) en el entorno beta.
# Genera el cert en el mismo path que espera nginx.conf.
#
# Cómo ejecutar:
#   sudo bash 02-ssl-local.sh
#   sudo bash 02-ssl-local.sh almacenes.codigo2enter.com   (dominio custom)
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

DOMAIN="${1:-almacenes.codigo2enter.com}"
CERT_DIR="/etc/letsencrypt/live/$DOMAIN"

[[ $EUID -ne 0 ]] && err "Ejecutar con sudo: sudo bash $0"
command -v openssl &>/dev/null || err "openssl no encontrado. Instalar: sudo apt-get install -y openssl"

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  02-SSL-LOCAL — CERTIFICADO AUTOFIRMADO${NC}"
echo -e "${BLUE}  Dominio: $DOMAIN${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

mkdir -p "$CERT_DIR"

openssl req -x509 -newkey rsa:2048 -days 365 -nodes \
    -keyout "$CERT_DIR/privkey.pem" \
    -out    "$CERT_DIR/fullchain.pem" \
    -subj   "/CN=$DOMAIN" \
    -addext "subjectAltName=DNS:$DOMAIN,IP:127.0.0.1" \
    2>/dev/null

chmod 600 "$CERT_DIR/privkey.pem"
chmod 644 "$CERT_DIR/fullchain.pem"

EXPIRY=$(openssl x509 -noout -enddate -in "$CERT_DIR/fullchain.pem" | cut -d= -f2)

ok "fullchain.pem : $CERT_DIR/fullchain.pem"
ok "privkey.pem   : $CERT_DIR/privkey.pem"
ok "Vencimiento   : $EXPIRY"

echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  CERTIFICADO AUTOFIRMADO CREADO EXITOSAMENTE               ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO:${NC}"
echo "  echo \"127.0.0.1  $DOMAIN\" | sudo tee -a /etc/hosts"
echo "  bash 03-deploy.sh"
echo ""
