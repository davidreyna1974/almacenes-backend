# Runbook de operación — Sistema Almacenes

<!-- El "cómo" operativo, paso a paso, para operar el sistema desplegado (Day-2 ops).
     Pensado para ejecutarse bajo presión: comandos exactos. Sin secretos en claro. -->

**Entorno:** producción · **Directorio de despliegue:** `/opt/almacenes` · **Última actualización:** 2026-07-10

> Complementa al **INSTRUCTIVO de puesta en producción** (`INSTRUCTIVO_puesta_produccion_almacenes.md`,
> el despliegue inicial paso a paso) y a la **guía de despliegue en VM GCP**. Este runbook cubre la
> **operación recurrente** una vez desplegado. Origen: Brecha 6 (mínimo Tier 0+0.5).

## 1. Arquitectura de despliegue
Stack de **3 contenedores** orquestados por `docker compose` (`/opt/almacenes/docker-compose.yml`,
generado por `03-deploy.sh`):

| Contenedor | Imagen | Rol |
|---|---|---|
| `almacenes-db` | postgres:16-alpine | Base de datos (volumen `postgres_data`) |
| `almacenes-backend` | Spring Boot (Dockerfile) | API REST `:8080` (red interna) |
| `almacenes-frontend` | nginx:alpine + Angular | Web + TLS `:80/:443` (proxy `/api/` → backend) |

Scripts de operación en `backend/scripts/`: `01`–`05` (despliegue), `maint-db.sh` (extensión/índices),
`backup-db.sh` (respaldo).

## 2. Requisitos previos
- Acceso SSH al host con usuario con permisos de `docker`.
- `docker` + `docker compose` instalados (los deja `01-prepare-server.sh`).
- Archivo `/opt/almacenes/.env` presente (lo genera `03-deploy.sh`; contiene `POSTGRES_*`,
  `JWT_SECRET`, `DOMAIN`, ramas). **Nunca** exponer su contenido.

## 3. Desplegar una nueva versión
```bash
cd /opt/almacenes
# 1. Actualizar los repos al TAG exacto a desplegar (inmutable)
git -C backend  fetch --tags && git -C backend  checkout vX.Y.Z
git -C frontend fetch --tags && git -C frontend checkout vX.Y.Z
# 2. Reconstruir imágenes y levantar (rolling: primero db, luego backend/frontend)
docker compose --env-file .env build backend frontend
docker compose --env-file .env up -d
# 3. (Si el primer arranque o cambió el esquema) extensión/función/índices:
bash backend/scripts/maint-db.sh
# 4. Verificar (ver §4)
```

## 4. Smoke test post-despliegue
```
[ ] docker compose ps → los 3 contenedores "Up" y (db/backend) "healthy".
[ ] Health backend: docker compose exec -T backend wget -qO- http://localhost:8080/actuator/health → {"status":"UP"}
[ ] Web pública responde: curl -I https://<DOMAIN>/ → 200/301.
[ ] Login con un usuario válido funciona (POST /api/v1/auth/login).
[ ] Sin errores 5xx en los logs los primeros minutos (§6 Logs).
```

## 5. Rollback
```bash
cd /opt/almacenes
# Volver al tag anterior estable y reconstruir
git -C backend  checkout vX.Y.(Z-1)
git -C frontend checkout vX.Y.(Z-1)
docker compose --env-file .env build backend frontend && docker compose --env-file .env up -d
# Si la nueva versión aplicó cambios de datos incompatibles: restaurar desde backup (§6 Backup).
```

## 6. Operación rutinaria

### 6.1 Confiabilidad y arranque  *(Brecha 6 — FASE A)*
- **Restart automático:** los 3 servicios tienen `restart: unless-stopped` (se re-levantan tras caída/reboot).
- **Límites de recursos:** definidos por servicio en el compose (`deploy.resources.limits`) — evitan que
  uno agote la RAM del host. **Calibrados para VM ~2 GB; ajustar** si la VM es distinta.
- **Apagado ordenado (graceful):** el backend drena las peticiones en vuelo al detenerse
  (`server.shutdown: graceful` + `timeout-per-shutdown-phase: 30s`, con `stop_grace_period: 40s` en compose).
- **Healthchecks:** `db` (pg_isready), `backend` (Actuator), `frontend` (puerto 80).
- Reinicio manual de un servicio: `docker compose -f /opt/almacenes/docker-compose.yml restart backend`.

### 6.2 Logs y retención  *(Brecha 6 — FASE B)*
- Logs del backend (perfil prod): `/var/log/almacenes/app.log`, **rotación diaria**, **retención 30 días**,
  **tope 500 MB** (logback `maxHistory=30` + `totalSizeCap=500MB`) — no llenan el disco.
- Ver en vivo: `docker compose -f /opt/almacenes/docker-compose.yml logs -f --tail=100 backend`.
- Log del backup (si se usa cron): `/var/log/almacenes/backup.log`.

### 6.3 Credenciales y primer arranque  *(Brecha 6 — FASE C)*
- **OBLIGATORIO en el primer arranque de producción:** cambiar la contraseña del admin por defecto
  (`admin` / `Admin123!`) desde la UI (perfil → cambiar contraseña) **antes** de exponer el sistema.
  Dejar la credencial por defecto es una puerta abierta.
- Secretos (`JWT_SECRET`, `POSTGRES_PASSWORD`) viven en `/opt/almacenes/.env` (los genera `03-deploy.sh`),
  nunca en git. **Rotación periódica:** editar el `.env`, `docker compose up -d` para recargar. Rotar
  `JWT_SECRET` invalida los tokens vigentes (los usuarios re-inician sesión) — hacerlo en ventana de bajo uso.

### 6.4 Backup y restauración  *(Brecha 6 — FASE D)*  ⚠ crítico
- **Respaldo:** `bash /opt/almacenes/backend/scripts/backup-db.sh` (pg_dump + gzip + cifrado gpg opcional
  + copia off-site + rotación). Variables: `BACKUP_DIR`, `BACKUP_RETENTION_DAYS` (def 14), `GPG_RECIPIENT`,
  `OFFSITE_DEST`, `OFFSITE_CMD`. **RPO objetivo: 24 h.**
- **Programar (activación en deploy):** cron diario —
  `0 3 * * *  cd /opt/almacenes && bash backend/scripts/backup-db.sh >> /var/log/almacenes/backup.log 2>&1`
- **Off-site + cifrado:** definir `OFFSITE_DEST` (otro host/bucket) y `GPG_RECIPIENT`. Nunca dejar el backup
  solo en el mismo host, ni copiar sin cifrar a destinos no confiables (regla 3-2-1).
- **Restauración / DRILL (obligatorio, RTO objetivo < 1 h):** el procedimiento paso a paso está al pie de
  `backup-db.sh`. Resumen: descifrar → restaurar en una BD limpia (`almacenes_restore_test`) → verificar
  conteos → medir el tiempo → limpiar. **Ejecutar el drill periódicamente** (un backup no probado no cuenta).

### 6.5 Monitoreo y alertas  *(Brecha 6 — FASE E)*
- **Monitor de uptime EXTERNO (activación en deploy):** registrar en un servicio externo (UptimeRobot,
  Better Stack u otro) un chequeo a `https://<DOMAIN>/` con **intervalo 1–5 min** y **alerta por correo**
  al caer. Detecta caídas totales que un monitor interno no vería.
- **Probar la alerta:** detener el frontend en staging (`docker compose stop frontend`) y confirmar que
  llega el aviso; luego re-levantar.
- Registrar aquí: **URL monitoreada**, **intervalo**, **contacto de alerta** = `<llenar al desplegar>`.

## 7. Diagnóstico de incidencias
| Síntoma | Causa probable | Acción |
|---|---|---|
| 502/503 en la web | backend caído o no "healthy" | `docker compose ps`; `logs backend`; `restart backend` |
| Login falla (todos) | `JWT_SECRET` cambiado / reloj desfasado | revisar `.env`; `timedatectl`; re-login |
| BD no conecta | credenciales/red/volumen | `logs db`; `pg_isready`; revisar `.env` y `almacenes-network` |
| Disco lleno | logs o backups sin rotar | revisar `/var/log/almacenes` y `BACKUP_DIR`; ajustar retención |
| Contenedor OOM-killed | límite de memoria muy bajo | subir `deploy.resources.limits` acorde a la VM |
| Cert TLS expirado | renovación certbot fallida | ver `02-ssl.sh` / `certbot renew`; revisar `certbot.timer` |

## 8. Contactos / escalamiento
- Responsable de operación: **<rol / medio de contacto — llenar al desplegar>**.
- Proveedor de la VM/hosting: **<soporte — llenar>**.
