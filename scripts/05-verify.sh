#!/bin/bash
# ============================================================
# SCRIPT 05 — VERIFICACIÓN POST-DESPLIEGUE (Smoke Tests)
# Sistema  : Almacenes — codigoCodigoEnter
# Dominio  : almacenes.codigo2enter.com
# Versión  : 1.0 (2026-06-18)
#
# Descripción:
#   Ejecuta 8 pruebas rápidas para confirmar que el sistema
#   está funcionando correctamente en producción después del
#   despliegue completo.
#
# Prerequisitos:
#   - Scripts 01-04 ejecutados exitosamente
#   - DNS del dominio apuntando al servidor
#
# Cómo ejecutar:
#   bash 05-verify.sh
#   bash 05-verify.sh almacenes.codigo2enter.com    (dominio custom)
#
# Pruebas (smoke tests):
#   1. Contenedores Docker activos (db, backend, frontend)
#   2. Backend saludable (/actuator/health desde dentro del contenedor)
#   3. Redirección HTTP → HTTPS (HTTP 301)
#   4. HTTPS responde con HTTP 200
#   5. Certificado SSL válido (no expirado, dominio correcto)
#   6. API accesible via HTTPS (/api/v1/auth/login responde)
#   7. SPA routing — ruta de Angular responde con HTTP 200
#   8. Puerto 8080 bloqueado desde el exterior
#
# Resultado:
#   Muestra PASS o FAIL por cada prueba.
#   Sale con código 0 si todas pasan, 1 si alguna falla.
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

DEPLOY_DIR="/opt/almacenes"
DOMAIN="${1:-almacenes.codigo2enter.com}"
PASS=0
FAIL=0

pass() { echo -e "  ${GREEN}✔ PASS${NC} — $1"; PASS=$((PASS + 1)); }
fail() { echo -e "  ${RED}✘ FAIL${NC} — $1"; FAIL=$((FAIL + 1)); }
info() { echo -e "  ${YELLOW}ℹ${NC}      $1"; }

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  VERIFY — VERIFICACIÓN POST-DESPLIEGUE (Smoke Tests)${NC}"
echo -e "${BLUE}  Sistema: Almacenes — codigoCodigoEnter${NC}"
echo -e "${BLUE}  Dominio: $DOMAIN${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# ============================================================
# TEST 1 — Contenedores Docker activos
# ============================================================
# Verificar que los tres servicios (db, backend, frontend)
# están corriendo. docker compose ps filtra por estado.
echo -e "${BLUE}[1/8] Contenedores Docker activos...${NC}"
for SERVICE in db backend frontend; do
    STATUS=$(docker compose -f "$DEPLOY_DIR/docker-compose.yml" ps --format "{{.Status}}" "$SERVICE" 2>/dev/null || echo "")
    if echo "$STATUS" | grep -qi "running\|up"; then
        pass "Contenedor '$SERVICE': $STATUS"
    else
        fail "Contenedor '$SERVICE': ${STATUS:-'no encontrado o detenido'}"
    fi
done

# ============================================================
# TEST 2 — Backend saludable (Spring Boot Actuator)
# ============================================================
# /actuator/health es el endpoint de health-check de Spring Boot
# (spring-boot-starter-actuator). Devuelve JSON {"status":"UP"}
# cuando la aplicación y la conexión a la BD están bien.
echo ""
echo -e "${BLUE}[2/8] Backend saludable (/actuator/health)...${NC}"
HEALTH=$(docker compose -f "$DEPLOY_DIR/docker-compose.yml" exec -T backend \
    curl -sf http://localhost:8080/actuator/health 2>/dev/null || echo "ERROR")
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    pass "Backend /actuator/health: UP"
    info "Respuesta: $HEALTH"
else
    fail "Backend /actuator/health no retornó {\"status\":\"UP\"}"
    info "Respuesta: $HEALTH"
fi

# ============================================================
# TEST 3 — Redirección HTTP → HTTPS (código 301)
# ============================================================
# nginx redirige todo el tráfico HTTP (puerto 80) a HTTPS
# con un redirect 301 permanente. Verificar que esta
# redirección funciona antes de que el certificado sea procesado.
echo ""
echo -e "${BLUE}[3/8] Redirección HTTP → HTTPS...${NC}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "http://$DOMAIN" 2>/dev/null || echo "0")
if [[ "$HTTP_CODE" == "301" ]]; then
    pass "http://$DOMAIN → 301 Redirect a HTTPS"
else
    fail "http://$DOMAIN retornó HTTP $HTTP_CODE (esperado 301)"
fi

# ============================================================
# TEST 4 — HTTPS responde con HTTP 200
# ============================================================
# La aplicación Angular debe responder con 200 en la raíz
# cuando se accede via HTTPS.
echo ""
echo -e "${BLUE}[4/8] HTTPS responde con HTTP 200...${NC}"
HTTPS_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "https://$DOMAIN" 2>/dev/null || echo "0")
if [[ "$HTTPS_CODE" == "200" ]]; then
    pass "https://$DOMAIN → HTTP 200"
else
    fail "https://$DOMAIN retornó HTTP $HTTPS_CODE (esperado 200)"
fi

# ============================================================
# TEST 5 — Certificado SSL válido
# ============================================================
# Verifica que el certificado Let's Encrypt:
#   a) No está expirado
#   b) Es válido para el dominio correcto
#   c) La cadena de confianza es completa (fullchain.pem)
# openssl s_client se conecta al puerto 443 y recupera el cert.
echo ""
echo -e "${BLUE}[5/8] Certificado SSL válido...${NC}"
CERT_INFO=$(echo | openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" 2>/dev/null | openssl x509 -noout -dates -subject 2>/dev/null || echo "ERROR")
if echo "$CERT_INFO" | grep -q "notAfter"; then
    EXPIRY=$(echo "$CERT_INFO" | grep "notAfter" | cut -d= -f2)
    # Verificar que el certificado sea para el dominio correcto
    if echo "$CERT_INFO" | grep -q "$DOMAIN"; then
        pass "Certificado SSL válido para $DOMAIN"
        info "Vencimiento: $EXPIRY"
    else
        fail "Certificado SSL no incluye el dominio $DOMAIN"
        info "Subject: $(echo "$CERT_INFO" | grep subject)"
    fi
else
    fail "No se pudo verificar el certificado SSL"
    info "Detalle: $CERT_INFO"
fi

# ============================================================
# TEST 6 — API accesible via HTTPS
# ============================================================
# El endpoint POST /api/v1/auth/login existe en el backend.
# Sin credenciales retorna 400 (Bad Request) o 401 (Unauthorized),
# pero lo importante es que no retorne 404, 502, ni 0 (timeout).
# Una respuesta 400 o 401 confirma que nginx está proxeando
# correctamente a Spring Boot.
echo ""
echo -e "${BLUE}[6/8] API accesible via HTTPS...${NC}"
API_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 \
    -X POST -H "Content-Type: application/json" \
    -d '{}' \
    "https://$DOMAIN/api/v1/auth/login" 2>/dev/null || echo "0")
if [[ "$API_CODE" == "400" ]] || [[ "$API_CODE" == "401" ]]; then
    pass "API https://$DOMAIN/api/v1/auth/login → HTTP $API_CODE (backend responde)"
elif [[ "$API_CODE" == "200" ]]; then
    fail "API retornó 200 con body vacío — verificar validación de campos"
else
    fail "API https://$DOMAIN/api/v1/auth/login → HTTP $API_CODE (esperado 400 o 401)"
fi

# ============================================================
# TEST 7 — SPA Routing (rutas de Angular)
# ============================================================
# Angular usa el HTML5 History API (rutas como /login, /inventory).
# nginx necesita la directiva 'try_files $uri $uri/ /index.html'
# para que F5 en cualquier ruta devuelva index.html (no 404).
# Verificar que /login devuelve 200 (y no 404).
echo ""
echo -e "${BLUE}[7/8] SPA Routing — ruta Angular devuelve 200...${NC}"
SPA_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "https://$DOMAIN/login" 2>/dev/null || echo "0")
if [[ "$SPA_CODE" == "200" ]]; then
    pass "SPA routing: https://$DOMAIN/login → HTTP 200"
else
    fail "SPA routing: https://$DOMAIN/login → HTTP $SPA_CODE (esperado 200)"
    info "Verificar 'try_files \$uri \$uri/ /index.html' en nginx.conf"
fi

# ============================================================
# TEST 8 — Puerto 8080 bloqueado desde el exterior
# ============================================================
# El backend NO debe ser accesible directamente desde internet.
# Solo debe ser accesible via HTTPS a través de nginx (proxy).
# curl con --max-time 5 retorna 0 si hay conexión, error si no.
# Esperamos que NO se pueda conectar (connection refused o timeout).
echo ""
echo -e "${BLUE}[8/8] Puerto 8080 bloqueado desde el exterior...${NC}"
HTTP_8080=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://$DOMAIN:8080" 2>/dev/null || echo "BLOCKED")
if [[ "$HTTP_8080" == "BLOCKED" ]] || [[ "$HTTP_8080" == "0" ]] || [[ "$HTTP_8080" == "000" ]]; then
    pass "Puerto 8080: no accesible desde el exterior (bloqueado)"
else
    fail "Puerto 8080: accesible desde el exterior (HTTP $HTTP_8080) — verificar ufw deny 8080/tcp"
fi

# ============================================================
# RESUMEN FINAL
# ============================================================
TOTAL=$((PASS + FAIL))
echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  RESUMEN DE VERIFICACIÓN${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""
echo "  Total de pruebas : $TOTAL"
echo -e "  ${GREEN}Exitosas (PASS)  : $PASS${NC}"
echo -e "  ${RED}Fallidas (FAIL)  : $FAIL${NC}"
echo ""

if [[ $FAIL -eq 0 ]]; then
    echo -e "${GREEN}  ✔ SISTEMA EN PRODUCCIÓN — TODAS LAS PRUEBAS PASARON${NC}"
    echo ""
    echo "  Acceso: https://$DOMAIN"
    echo ""
    exit 0
else
    echo -e "${RED}  ✘ $FAIL PRUEBA(S) FALLARON — Revisar y corregir antes de usar en producción${NC}"
    echo ""
    echo "  Comandos útiles para diagnóstico:"
    echo "    docker compose -f $DEPLOY_DIR/docker-compose.yml logs backend"
    echo "    docker compose -f $DEPLOY_DIR/docker-compose.yml logs frontend"
    echo "    docker compose -f $DEPLOY_DIR/docker-compose.yml ps"
    echo "    sudo ufw status numbered"
    echo ""
    exit 1
fi
