#!/bin/bash
# ============================================================
# SCRIPT 02 — OBTENCIÓN DE CERTIFICADO SSL
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : (se pasa como argumento — agnóstico del dominio)
# Versión  : 1.1 (2026-07-06)
#
# Descripción:
#   Instala certbot y obtiene el certificado SSL/TLS gratuito
#   de Let's Encrypt para el dominio de producción.
#   Configura la renovación automática vía el timer nativo de
#   certbot (certbot.timer) + renewal-hooks que reinician nginx.
#
# Prerequisitos OBLIGATORIOS antes de ejecutar:
#   1. El registro DNS del dominio debe apuntar a la IP de
#      este servidor (verificar con: nslookup <dominio>)
#   2. El puerto 80 debe estar libre (certbot lo usa para el
#      challenge HTTP-01 de verificación de dominio)
#   3. Script 01 ejecutado exitosamente
#
# Cómo ejecutar (el dominio es OBLIGATORIO):
#   sudo bash 02-ssl.sh almacenes.codigo2enter.com     (producción)
#   sudo bash 02-ssl.sh mi-subdominio.duckdns.org      (modo prueba)
#
# Qué hace paso a paso:
#   1. Verifica privilegios de root
#   2. Valida que se pasó el dominio como argumento
#   3. Verifica que el puerto 80 está libre
#   4. Verifica que el DNS resuelve al servidor
#   5. Instala certbot
#   6. Obtiene el certificado SSL (challenge HTTP-01)
#   7. Verifica que los archivos del certificado existen
#   8. Configura la renovación automática (certbot.timer + renewal-hooks)
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
info() { echo -e "  ${YELLOW}ℹ${NC}      $1"; }
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
# PASO 8 — Configurar renovación automática (certbot.timer + hooks)
# ============================================================
# Let's Encrypt emite certificados válidos por 90 días. El paquete
# apt de certbot YA instala y habilita `certbot.timer` (systemd), que
# corre `certbot renew` dos veces al día. Renovar via un cron manual
# propio es redundante y frágil (en la prueba GCP el cron ni siquiera
# quedó registrado). Por eso NO agregamos cron: usamos el timer nativo
# + renewal-hooks. (finding #6 prueba GCP)
#
# El problema que resuelven los hooks:
#   - `certbot renew` usa --standalone (como en la emisión) → necesita
#     el puerto 80 libre, pero el contenedor frontend (nginx) lo ocupa.
#   - Aunque el ARCHIVO del cert se renueve, nginx sigue sirviendo el
#     cert VIEJO hasta que el contenedor frontend se reinicia.
#
# Solución con renewal-hooks (se ejecutan SOLO cuando hay renovación real):
#   pre/    → detiene frontend (libera el puerto 80 para el challenge)
#   deploy/ → (solo si renovó con éxito) reinicia frontend + deja bitácora
#   post/   → arranca frontend siempre (red de seguridad ante fallos)
step "8/8 Configurando renovación automática del certificado..."

HOOKS_DIR="/etc/letsencrypt/renewal-hooks"
COMPOSE="/opt/almacenes/docker-compose.yml"
RENEW_LOG="/var/log/almacenes-cert-renew.log"
mkdir -p "$HOOKS_DIR/pre" "$HOOKS_DIR/deploy" "$HOOKS_DIR/post"

# pre-hook: liberar el puerto 80 deteniendo el frontend
cat > "$HOOKS_DIR/pre/stop-frontend.sh" <<EOF
#!/bin/bash
# Detiene el contenedor frontend para liberar el puerto 80 durante el
# challenge HTTP-01 de la renovación. Generado por 02-ssl.sh.
docker compose -f "$COMPOSE" stop frontend || true
EOF

# deploy-hook: solo corre si el certificado se renovó con éxito
cat > "$HOOKS_DIR/deploy/reload-frontend.sh" <<EOF
#!/bin/bash
# Reinicia el frontend para que nginx cargue el certificado renovado, y
# deja evidencia en la bitácora. Solo se ejecuta cuando certbot renueva
# de verdad (o con 'certbot renew --run-deploy-hooks'). Generado por 02-ssl.sh.
echo "\$(date '+%Y-%m-%d %H:%M:%S') — cert renovado; reiniciando frontend (deploy-hook)" >> "$RENEW_LOG"
docker compose -f "$COMPOSE" restart frontend
EOF

# post-hook: garantizar que el frontend quede arriba pase lo que pase
cat > "$HOOKS_DIR/post/start-frontend.sh" <<EOF
#!/bin/bash
# Red de seguridad: arranca el frontend tras cada intento de renovación
# (aunque haya fallado, para no dejar el sitio caído). Generado por 02-ssl.sh.
docker compose -f "$COMPOSE" start frontend || true
EOF

chmod +x "$HOOKS_DIR/pre/stop-frontend.sh" \
         "$HOOKS_DIR/deploy/reload-frontend.sh" \
         "$HOOKS_DIR/post/start-frontend.sh"
ok "Renewal-hooks creados (pre/deploy/post) en $HOOKS_DIR"

# Asegurar que el timer nativo de certbot está habilitado y activo
if systemctl list-unit-files 2>/dev/null | grep -q '^certbot.timer'; then
    systemctl enable --now certbot.timer 2>/dev/null || true
    TIMER_STATE=$(systemctl is-active certbot.timer 2>/dev/null || echo "inactivo")
    ok "certbot.timer: $TIMER_STATE"
else
    warn "No se encontró certbot.timer. Verifica la instalación de certbot (apt)."
fi

# Limpiar un posible cron manual heredado de versiones anteriores del script
if crontab -l 2>/dev/null | grep -q "certbot renew"; then
    crontab -l 2>/dev/null | grep -v "certbot renew" | crontab - || true
    warn "Se eliminó un cron manual 'certbot renew' heredado (ahora lo maneja certbot.timer)."
fi

echo ""
info "Para probar los hooks SIN esperar 90 días ni gastar el rate limit:"
info "  sudo certbot renew --run-deploy-hooks   (dispara el deploy-hook y verás la bitácora en $RENEW_LOG)"

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
echo "  Renovación : automática (certbot.timer + renewal-hooks)"
echo "  Bitácora   : $RENEW_LOG (se escribe al renovar/reiniciar frontend)"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO (pasa el mismo dominio):${NC}"
echo "  bash 03-deploy.sh $DOMAIN"
echo ""
