#!/bin/bash
# ============================================================
# SCRIPT 04 — CONFIGURACIÓN DEL FIREWALL (ufw)
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : (agnóstico del dominio — el firewall no usa el dominio)
# Versión  : 1.1 (2026-07-06)
#
# Descripción:
#   Configura el firewall ufw (Uncomplicated Firewall) del
#   servidor con las reglas de seguridad requeridas:
#   - Permite SSH (22), HTTP (80) y HTTPS (443)
#   - Deniega acceso externo al backend (8080) y BD (5432)
#   - Activa el firewall
#
# Por qué es necesario:
#   Los puertos 8080 (Spring Boot) y 5432 (PostgreSQL) están
#   en la red interna de Docker ('expose' no 'ports' en compose),
#   pero sin firewall, un atacante con acceso a la red del
#   servidor podría llegar a ellos si Docker redirigiera el
#   tráfico. El firewall es una segunda capa de defensa.
#
# Prerequisitos:
#   - Scripts 01-03 ejecutados exitosamente
#   - ufw disponible (preinstalado en Ubuntu)
#
# Cómo ejecutar:
#   sudo bash 04-firewall.sh
#
# ⚠ ADVERTENCIA DE SSH:
#   Si estás conectado por SSH, el paso que activa el firewall
#   solicitará confirmación EXPLÍCITA antes de habilitarlo.
#   El puerto 22 (SSH) siempre se permite ANTES de activar.
#   Aún así: asegúrate de tener una sesión SSH abierta como
#   respaldo antes de ejecutar este script.
#
# Qué hace paso a paso:
#   1. Verifica privilegios de root
#   2. Verifica que ufw está disponible
#   3. Permite puerto 22 (SSH) — PRIMERO, antes de activar
#   4. Permite puerto 80 (HTTP — para redirección a HTTPS)
#   5. Permite puerto 443 (HTTPS — tráfico de producción)
#   6. Deniega puerto 8080 (backend — solo red Docker interna)
#   7. Deniega puerto 5432 (PostgreSQL — solo red Docker interna)
#   8. Activa ufw con confirmación del operador
#   9. Muestra estado final del firewall
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  SCRIPT 04 — CONFIGURACIÓN DEL FIREWALL (ufw)${NC}"
echo -e "${BLUE}  Sistema: Almacenes — codigoCodigoEnter${NC}"
echo -e "${BLUE}============================================================${NC}"

# ============================================================
# PASO 1 — Verificar privilegios de root
# ============================================================
step "1/9 Verificando privilegios de root..."
[[ $EUID -ne 0 ]] && err "Ejecutar con sudo:\n     sudo bash $0"
ok "Ejecutando como root"

# ============================================================
# PASO 2 — Verificar que ufw está disponible
# ============================================================
# ufw está preinstalado en Ubuntu 22.04/24.04.
# Si no está disponible, se instala con apt.
step "2/9 Verificando disponibilidad de ufw..."
if ! command -v ufw &>/dev/null; then
    warn "ufw no encontrado. Instalando..."
    apt-get install -y -qq ufw
fi
ok "ufw disponible: $(ufw version | head -1)"

# ============================================================
# PASO 3 — Permitir SSH (puerto 22)
# ============================================================
# ⚠ CRÍTICO: el puerto SSH DEBE habilitarse ANTES de activar
# el firewall. Si se activa ufw sin permitir el 22 primero,
# se pierde el acceso remoto al servidor.
# 'ufw allow ssh' es equivalente a 'ufw allow 22/tcp'.
step "3/9 Permitiendo SSH (puerto 22)..."
ufw allow ssh
ok "Puerto 22 (SSH): PERMITIDO"

# ============================================================
# PASO 4 — Permitir HTTP (puerto 80)
# ============================================================
# El puerto 80 es necesario para:
#   a) La redirección HTTP → HTTPS que hace nginx (301 Redirect)
#   b) El challenge HTTP-01 de certbot para renovar el certificado
step "4/9 Permitiendo HTTP (puerto 80)..."
ufw allow 80/tcp
ok "Puerto 80 (HTTP): PERMITIDO"

# ============================================================
# PASO 5 — Permitir HTTPS (puerto 443)
# ============================================================
# Todo el tráfico de producción usa HTTPS (443).
# nginx termina TLS y sirve la aplicación Angular + proxea /api/.
step "5/9 Permitiendo HTTPS (puerto 443)..."
ufw allow 443/tcp
ok "Puerto 443 (HTTPS): PERMITIDO"

# ============================================================
# PASO 6 — Denegar acceso externo al backend (puerto 8080)
# ============================================================
# El contenedor 'backend' usa 'expose: "8080"' en docker-compose.yml
# (NO 'ports'), lo que significa que el puerto 8080 solo es
# accesible dentro de la red interna de Docker.
# Sin embargo, Docker puede modificar reglas de iptables que
# bypass-an ufw. Para garantizar protección, se deniega
# explícitamente 8080 en ufw también.
step "6/9 Denegando acceso externo al backend (puerto 8080)..."
ufw deny 8080/tcp
ok "Puerto 8080 (backend Spring Boot): DENEGADO"

# ============================================================
# PASO 7 — Denegar acceso externo a PostgreSQL (puerto 5432)
# ============================================================
# PostgreSQL tampoco tiene 'ports' en docker-compose.yml.
# La doble protección (expose sin ports + deny en ufw) garantiza
# que la base de datos nunca es alcanzable desde internet.
step "7/9 Denegando acceso externo a PostgreSQL (puerto 5432)..."
ufw deny 5432/tcp
ok "Puerto 5432 (PostgreSQL): DENEGADO"

# ============================================================
# PASO 8 — Activar ufw con confirmación del operador
# ============================================================
# Antes de activar el firewall, mostrar un resumen de las
# reglas configuradas y solicitar confirmación explícita.
# Activar ufw sin el SSH permitido bloquearía el servidor.
step "8/9 Activando el firewall..."
echo ""
echo -e "${YELLOW}  ⚠ ADVERTENCIA: Estás a punto de activar el firewall.${NC}"
echo ""
echo "  Reglas que se aplicarán:"
echo "    ALLOW  22/tcp   (SSH)"
echo "    ALLOW  80/tcp   (HTTP → redirección a HTTPS)"
echo "    ALLOW  443/tcp  (HTTPS)"
echo "    DENY   8080/tcp (backend — solo red Docker interna)"
echo "    DENY   5432/tcp (PostgreSQL — solo red Docker interna)"
echo ""
echo -e "${YELLOW}  Asegúrate de tener una sesión SSH abierta como respaldo.${NC}"
echo ""
read -rp "  ¿Activar el firewall? (s/N): " CONFIRM
[[ "${CONFIRM,,}" != "s" ]] && { warn "Firewall NO activado. Ejecuta el script de nuevo cuando estés listo."; exit 0; }

# 'yes |' responde automáticamente 'y' al prompt interno de ufw
# que pregunta "Command may disrupt existing ssh connections. Proceed with operation (y|n)?"
yes | ufw enable
ok "Firewall ufw activado"

# ============================================================
# PASO 9 — Mostrar estado final
# ============================================================
step "9/9 Verificando estado del firewall..."
ufw status numbered
echo ""

# Resumen de verificación
ALLOW_22=$(ufw status | grep -c "22.*ALLOW" || true)
ALLOW_80=$(ufw status | grep -c "80.*ALLOW" || true)
ALLOW_443=$(ufw status | grep -c "443.*ALLOW" || true)
DENY_8080=$(ufw status | grep -c "8080.*DENY" || true)
DENY_5432=$(ufw status | grep -c "5432.*DENY" || true)

[[ $ALLOW_22 -ge 1 ]]  && ok "22/tcp  SSH    : ALLOW" || warn "22/tcp  SSH    : no encontrado en reglas"
[[ $ALLOW_80 -ge 1 ]]  && ok "80/tcp  HTTP   : ALLOW" || warn "80/tcp  HTTP   : no encontrado en reglas"
[[ $ALLOW_443 -ge 1 ]] && ok "443/tcp HTTPS  : ALLOW" || warn "443/tcp HTTPS  : no encontrado en reglas"
[[ $DENY_8080 -ge 1 ]] && ok "8080/tcp backend: DENY" || warn "8080/tcp backend: no encontrado en reglas"
[[ $DENY_5432 -ge 1 ]] && ok "5432/tcp postgre: DENY" || warn "5432/tcp postgre: no encontrado en reglas"

# ============================================================
# RESUMEN FINAL
# ============================================================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  SCRIPT 04 COMPLETADO EXITOSAMENTE                         ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "  Firewall ufw: ACTIVO"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO (verificación):${NC}"
echo "  bash 05-verify.sh"
echo ""
