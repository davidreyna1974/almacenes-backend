#!/bin/bash
# ============================================================
# SCRIPT 01 — PREPARACIÓN DEL SERVIDOR
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : (agnóstico del dominio — se pasa como argumento a 02/03)
# Versión  : 1.0 (2026-06-18)
#
# Descripción:
#   Instala Docker Engine y Docker Compose v2 en el servidor
#   de producción, agrega el usuario al grupo docker y crea
#   la estructura de directorios que usarán los demás scripts.
#
# Sistema operativo compatible:
#   Ubuntu 22.04 LTS / Ubuntu 24.04 LTS / Debian 12
#
# Cómo ejecutar:
#   sudo bash 01-prepare-server.sh
#
# Qué hace paso a paso:
#   1. Verifica privilegios de root
#   2. Actualiza el índice de paquetes apt
#   3. Instala dependencias previas (curl, gnupg, etc.)
#   4. Agrega el repositorio oficial de Docker
#   5. Instala Docker Engine y Docker Compose v2
#   6. Agrega el usuario actual al grupo 'docker'
#   7. Habilita Docker para inicio automático
#   8. Crea /opt/almacenes/ con la estructura de despliegue
# ============================================================

set -euo pipefail  # Salir en error, variable indefinida o pipe fallida

# --------------- Colores para salida legible ----------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # Sin color (reset)

step() { echo -e "\n${BLUE}▶ $1${NC}"; }
ok()   { echo -e "  ${GREEN}✔ $1${NC}"; }
warn() { echo -e "  ${YELLOW}⚠ $1${NC}"; }
err()  { echo -e "  ${RED}✘ ERROR: $1${NC}"; exit 1; }

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  SCRIPT 01 — PREPARACIÓN DEL SERVIDOR${NC}"
echo -e "${BLUE}  Sistema: Almacenes — codigoCodigoEnter${NC}"
echo -e "${BLUE}============================================================${NC}"

# ============================================================
# PASO 1 — Verificar que se ejecuta como root
# ============================================================
# Todos los pasos de instalación de software y configuración
# del sistema requieren privilegios de superusuario (root).
# $EUID es la variable de entorno que contiene el UID efectivo
# del proceso actual. root siempre tiene UID = 0.
step "1/8 Verificando privilegios de root..."
[[ $EUID -ne 0 ]] && err "Este script debe ejecutarse con sudo:\n     sudo bash $0"
ok "Ejecutando como root (UID=$EUID)"

# Capturar el usuario real que invocó sudo (para asignar
# permisos correctos al directorio de despliegue al final).
# SUDO_USER contiene el nombre del usuario que ejecutó sudo.
REAL_USER="${SUDO_USER:-$USER}"

# ============================================================
# PASO 2 — Actualizar lista de paquetes
# ============================================================
# apt-get update descarga la lista actualizada de paquetes
# disponibles desde los repositorios configurados en
# /etc/apt/sources.list. No instala nada aún.
# -qq: modo silencioso (muestra solo errores).
step "2/8 Actualizando lista de paquetes apt..."
apt-get update -qq
ok "Lista de paquetes actualizada"

# ============================================================
# PASO 3 — Instalar dependencias previas
# ============================================================
# Paquetes necesarios para agregar el repositorio de Docker:
#   ca-certificates    : certificados SSL para conexiones HTTPS
#   curl               : descarga de archivos y la clave GPG
#   gnupg              : verificación de firmas criptográficas
#   lsb-release        : detectar la versión del SO (Ubuntu/Debian)
#   apt-transport-https: permite apt usar repositorios HTTPS
step "3/8 Instalando dependencias del sistema..."
apt-get install -y -qq \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    apt-transport-https
ok "Dependencias instaladas (ca-certificates, curl, gnupg, lsb-release)"

# ============================================================
# PASO 4 — Instalar Docker Engine
# ============================================================
# Se usa el repositorio oficial de Docker (no el paquete
# 'docker.io' de Ubuntu, que puede estar desactualizado).
# El proceso:
#   a) Descargar la clave GPG oficial de Docker para verificar
#      la autenticidad de los paquetes del repositorio.
#   b) Agregar el repositorio de Docker a las fuentes de apt.
#   c) Instalar docker-ce (Community Edition).
step "4/8 Instalando Docker Engine..."
if command -v docker &>/dev/null; then
    # Docker ya está instalado — mostrar versión y continuar
    warn "Docker ya está instalado. Se omite la instalación."
    warn "Versión actual: $(docker --version)"
else
    # a) Crear directorio para keyrings si no existe
    install -m 0755 -d /etc/apt/keyrings

    # b) Descargar y almacenar la clave GPG de Docker
    #    Detecta Ubuntu o Debian para usar la URL correcta del repositorio.
    OS_ID=$(. /etc/os-release && echo "${ID}")   # ubuntu o debian
    [[ "$OS_ID" != "ubuntu" && "$OS_ID" != "debian" ]] && OS_ID="ubuntu"
    curl -fsSL "https://download.docker.com/linux/${OS_ID}/gpg" \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    # c) Agregar el repositorio de Docker a las fuentes de apt
    #    $(dpkg --print-architecture): detecta la arquitectura (amd64, arm64, etc.)
    #    $(lsb_release -cs): detecta el nombre del SO (jammy, noble, bookworm, etc.)
    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/${OS_ID} \
        $(lsb_release -cs) stable" \
        | tee /etc/apt/sources.list.d/docker.list > /dev/null

    # d) Actualizar lista de paquetes con el nuevo repositorio
    apt-get update -qq

    # e) Instalar Docker Engine y plugins
    #    docker-ce            : el motor de Docker
    #    docker-ce-cli        : herramienta de línea de comandos
    #    containerd.io        : runtime de contenedores
    #    docker-buildx-plugin : plugin para builds multi-plataforma
    apt-get install -y -qq \
        docker-ce \
        docker-ce-cli \
        containerd.io \
        docker-buildx-plugin
    ok "Docker Engine instalado: $(docker --version)"
fi

# ============================================================
# PASO 5 — Instalar Docker Compose v2
# ============================================================
# Docker Compose v2 es el plugin moderno (comando: 'docker compose').
# Se diferencia de la versión v1 (comando: 'docker-compose' con guión).
# docker-compose-plugin lo instala como plugin oficial de Docker.
step "5/8 Instalando Docker Compose v2..."
if docker compose version &>/dev/null 2>&1; then
    warn "Docker Compose ya está disponible. Se omite."
    warn "Versión actual: $(docker compose version)"
else
    apt-get install -y -qq docker-compose-plugin
    ok "Docker Compose instalado: $(docker compose version)"
fi

# ============================================================
# PASO 6 — Agregar usuario al grupo docker
# ============================================================
# Por defecto, el socket de Docker (/var/run/docker.sock) es
# propiedad de root:docker. Agregar el usuario al grupo docker
# permite ejecutar comandos docker sin sudo.
# IMPORTANTE: el cambio de grupo solo tiene efecto en sesiones
# nuevas. Hay que cerrar y reabrir la sesión SSH.
step "6/8 Agregando usuario '$REAL_USER' al grupo docker..."
if groups "$REAL_USER" | grep -q '\bdocker\b'; then
    warn "El usuario '$REAL_USER' ya pertenece al grupo docker."
else
    usermod -aG docker "$REAL_USER"
    ok "Usuario '$REAL_USER' agregado al grupo docker"
    warn "IMPORTANTE: cierra y vuelve a abrir la sesión SSH para"
    warn "            que el grupo surta efecto antes de los siguientes scripts."
fi

# ============================================================
# PASO 7 — Habilitar Docker para inicio automático
# ============================================================
# systemctl enable: registra el servicio para inicio con el SO
# systemctl start : arranca el servicio inmediatamente
step "7/8 Habilitando Docker para inicio automático con el servidor..."
systemctl enable docker --quiet
systemctl start docker
ok "Docker habilitado y corriendo"

# ============================================================
# PASO 8 — Crear estructura de directorios de despliegue
# ============================================================
# /opt/almacenes/ es el directorio raíz del despliegue.
# Contendrá docker-compose.yml, .env, y los repos clonados.
# Estructura esperada:
#   /opt/almacenes/
#   ├── docker-compose.yml    (archivo de orquestación Docker)
#   ├── .env                  (variables de producción — 03-deploy.sh lo crea)
#   ├── backend/              (repositorio almacenes-backend)
#   └── frontend/             (repositorio almacenes-frontend)
step "8/8 Creando estructura de directorios en /opt/almacenes/..."
mkdir -p /opt/almacenes/backend
mkdir -p /opt/almacenes/frontend
# Asignar propiedad al usuario real (no a root) para que los
# scripts siguientes puedan ejecutarse sin sudo donde sea posible.
chown -R "$REAL_USER":"$REAL_USER" /opt/almacenes
ok "Directorio /opt/almacenes/ creado"
ok "Propietario: $REAL_USER"

# ============================================================
# RESUMEN FINAL
# ============================================================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  SCRIPT 01 COMPLETADO EXITOSAMENTE                         ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "  Docker:         $(docker --version)"
echo "  Docker Compose: $(docker compose version)"
echo "  Directorio:     /opt/almacenes/"
echo "  Usuario:        $REAL_USER"
echo ""
echo -e "${YELLOW}  SIGUIENTE PASO (pasa tu dominio como argumento):${NC}"
echo "  sudo bash 02-ssl.sh <DOMINIO>"
echo ""
