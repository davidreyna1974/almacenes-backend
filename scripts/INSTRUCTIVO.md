# Instructivo de instalación en producción
## Sistema Almacenes — codigoCodigoEnter
## Dominio: https://almacenes.codigo2enter.com

---

## Resumen ejecutivo

Este directorio contiene 5 scripts de automatización + 1 script de verificación
para desplegar el sistema en un servidor Ubuntu/Debian de producción.

**Los scripts se ejecutan UNA SOLA VEZ, en orden, en el primer despliegue.**
Para actualizaciones posteriores, ver la sección "Actualizaciones de la aplicación".

---

## Prerequisitos antes de ejecutar cualquier script

1. **Servidor Ubuntu 22.04 LTS o 24.04 LTS** (o Debian 12)
   - Mínimo recomendado: 2 vCPU, 4 GB RAM, 20 GB disco
   - Acceso SSH con usuario sudo

2. **Registro DNS configurado**
   - Tipo A: `almacenes.codigo2enter.com` → IP pública del servidor
   - La propagación DNS puede tomar hasta 24 horas
   - Verificar: `nslookup almacenes.codigo2enter.com`

3. **Repositorios listos para producción**
   - Backend: rama `main` con el código final
   - Frontend: rama `main` con `environment.prod.ts` actualizado:
     ```typescript
     export const environment = {
       production: true,
       apiUrl: 'https://almacenes.codigo2enter.com/api/v1'
     };
     ```

4. **Copiar los scripts al servidor**
   ```bash
   scp -r scripts/ usuario@IP-DEL-SERVIDOR:~/
   cd scripts/
   ```

---

## Orden de ejecución

```
01-prepare-server.sh  →  02-ssl.sh  →  03-deploy.sh  →  04-init-db.sh  →  05-firewall.sh  →  verify.sh
```

---

## Script 01 — Preparación del servidor

**Ejecutar como:** `sudo bash 01-prepare-server.sh`

**Qué hace:**
- Instala Docker Engine (desde el repositorio oficial de Docker)
- Instala Docker Compose v2 (plugin moderno, comando `docker compose`)
- Agrega el usuario al grupo `docker`
- Habilita Docker para inicio automático con el servidor
- Crea la estructura `/opt/almacenes/backend` y `/opt/almacenes/frontend`

**Tiempo estimado:** 3-5 minutos

**Acción manual requerida después:**
> Cerrar y reabrir la sesión SSH para que el grupo `docker` surta efecto.
> Sin este paso, los scripts 03-05 fallarán con "permission denied".

---

## Script 02 — Obtención del certificado SSL

**Ejecutar como:** `sudo bash 02-ssl.sh almacenes.codigo2enter.com`

**Qué hace:**
- Verifica que el DNS del dominio apunta al servidor
- Verifica que el puerto 80 está libre
- Instala certbot (herramienta oficial de Let's Encrypt)
- Obtiene el certificado SSL gratuito (válido 90 días)
- Configura renovación automática via cron (diario a las 3 AM)

**Tiempo estimado:** 2-3 minutos

**Prerequisito crítico:**
> El registro DNS A debe apuntar a la IP del servidor ANTES de ejecutar este script.
> Let's Encrypt verifica la propiedad del dominio con una petición HTTP.
> Si el DNS no apunta aquí, certbot fallará.

**Nota de seguridad:**
> Let's Encrypt tiene un límite de 5 certificados por dominio por semana.
> No ejecutes este paso repetidamente en pruebas.

**Dónde quedan los certificados:**
```
/etc/letsencrypt/live/almacenes.codigo2enter.com/
├── fullchain.pem  ← certificado + cadena de confianza
└── privkey.pem    ← clave privada (protegida, solo root)
```

---

## Script 03 — Despliegue de servicios Docker

**Ejecutar como:** `bash 03-deploy.sh` *(sin sudo — el usuario ya está en el grupo docker)*

**Qué hace:**
- Solicita interactivamente la contraseña de la base de datos
- Genera automáticamente un `JWT_SECRET` criptográficamente seguro (512 bits)
- Crea `/opt/almacenes/.env` con permisos 600 (solo el propietario puede leer)
- Solicita clonar manualmente los repositorios si aún no existen
- Construye las imágenes Docker (`docker compose build --no-cache`)
- Levanta los contenedores en background (`docker compose up -d`)
- Espera a que el backend Spring Boot esté disponible

**Tiempo estimado:** 5-10 minutos (compilación Java + build Angular)

**Paso manual durante la ejecución:**
> Si los repositorios no están en `/opt/almacenes/backend` y `/opt/almacenes/frontend`,
> el script pausará y pedirá clonarlos manualmente:
> ```bash
> cd /opt/almacenes/backend
> git clone https://github.com/davidreyna1974/almacenes-backend.git .
> git checkout main
> # (presiona Enter para continuar en el script)
> ```

**Archivo .env generado (contenido):**
```
POSTGRES_DB=almacenes_db
POSTGRES_USER=almacenes_user
POSTGRES_PASSWORD=<contraseña ingresada>
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/almacenes_db
JWT_SECRET=<generado automáticamente — 512 bits>
SPRING_PROFILES_ACTIVE=prod
DOMAIN=almacenes.codigo2enter.com
```

> ⚠ El archivo `.env` **nunca se commitea** al repositorio (está en `.gitignore`).
> Guarda la contraseña de la BD en un lugar seguro fuera del servidor.

---

## Script 04 — Inicialización de la base de datos

**Ejecutar como:** `bash 04-init-db.sh`
**Con carga de SQL:** `bash 04-init-db.sh --schema /ruta/schema.sql`

**Qué hace:**
- Espera a que PostgreSQL esté listo para conexiones
- Instala la extensión `unaccent` (búsquedas accent-insensitive: "galon" encuentra "Galón")
- Crea la función `f_unaccent(text)` inmutable (necesaria para índices funcionales)
- (Opcional) Carga un script SQL de esquema/datos iniciales
- Crea los 10 índices de rendimiento del sistema

**Tiempo estimado:** 1-2 minutos

**Cuándo ejecutar con --schema:**
- Solo si deseas cargar un esquema SQL previo o datos semilla
- En producción con Hibernate y `spring.jpa.hibernate.ddl-auto=create` o `update`,
  el esquema se crea automáticamente — el `--schema` es opcional

**Índices creados:**
| Índice | Tabla | Propósito |
|---|---|---|
| idx_product_name_unaccent | products | Búsqueda por nombre accent-insensitive |
| idx_product_category | products | Filtrado por categoría |
| idx_product_active | products | Filtrado por activo/inactivo |
| idx_supplier_name_unaccent | suppliers | Búsqueda por nombre |
| idx_client_name_unaccent | clients | Búsqueda por nombre |
| idx_purchase_order_status | purchase_orders | Filtrado por estado |
| idx_sale_order_status | sale_orders | Filtrado por estado |
| idx_movement_product | inventory_movements | Kardex por producto |
| idx_purchase_order_created_at | purchase_orders | Listado cronológico |
| idx_sale_order_created_at | sale_orders | Listado cronológico |

---

## Script 05 — Configuración del firewall

**Ejecutar como:** `sudo bash 05-firewall.sh`

**Qué hace:**
- Permite SSH (22), HTTP (80) y HTTPS (443)
- Deniega acceso externo al backend (8080) y PostgreSQL (5432)
- Activa el firewall ufw con confirmación explícita del operador

**Tiempo estimado:** < 1 minuto

**⚠ Advertencia crítica antes de ejecutar:**
> - Asegúrate de tener una sesión SSH de respaldo abierta
> - El script permite el puerto 22 ANTES de activar el firewall
> - Si ya tienes reglas ufw personalizadas, revisa el script antes de ejecutar

**Reglas aplicadas:**
```
ALLOW  22/tcp   (SSH — acceso administrativo)
ALLOW  80/tcp   (HTTP → redirige a HTTPS; certbot renewal)
ALLOW  443/tcp  (HTTPS — tráfico de producción)
DENY   8080/tcp (backend Spring Boot — solo red Docker interna)
DENY   5432/tcp (PostgreSQL — solo red Docker interna)
```

---

## Script verify.sh — Verificación post-despliegue

**Ejecutar como:** `bash verify.sh`
**Con dominio custom:** `bash verify.sh almacenes.codigo2enter.com`

**Qué verifica (8 pruebas):**
1. Los 3 contenedores Docker están corriendo (db, backend, frontend)
2. El backend responde `/actuator/health` con `{"status":"UP"}`
3. HTTP → HTTPS redirige con 301
4. HTTPS responde con 200
5. Certificado SSL es válido y no está expirado
6. La API (`/api/v1/auth/login`) es accesible via HTTPS
7. SPA routing: `/login` responde con 200 (nginx devuelve `index.html`)
8. Puerto 8080 NO es accesible desde el exterior

**Resultado esperado:**
```
8/8 PASS — SISTEMA EN PRODUCCIÓN — TODAS LAS PRUEBAS PASARON
```

---

## Diagrama de flujo completo

```
[Servidor limpio Ubuntu]
        │
        ▼
[01-prepare-server.sh]  →  Docker instalado, /opt/almacenes/ creado
        │
        │  (cerrar y reabrir sesión SSH)
        │
        ▼
[02-ssl.sh dominio]     →  Certificado SSL en /etc/letsencrypt/
        │
        │  (clonar repos si no existen)
        │
        ▼
[03-deploy.sh]          →  .env creado, imágenes construidas, contenedores up
        │
        ▼
[04-init-db.sh]         →  unaccent, f_unaccent, 10 índices creados
        │
        ▼
[05-firewall.sh]        →  ufw activo (22/80/443 ✓, 8080/5432 ✗)
        │
        ▼
[verify.sh]             →  8/8 pruebas PASS
        │
        ▼
[https://almacenes.codigo2enter.com — PRODUCCIÓN]
```

---

## Actualizaciones de la aplicación

Para actualizar el backend o frontend después del despliegue inicial:

```bash
# 1. Actualizar el repositorio correspondiente
cd /opt/almacenes/backend   # o /frontend
git pull origin main

# 2. Reconstruir solo el servicio afectado
docker compose -f /opt/almacenes/docker-compose.yml build backend  # o frontend

# 3. Reiniciar solo ese servicio (sin downtime para el otro)
docker compose -f /opt/almacenes/docker-compose.yml up -d --no-deps backend  # o frontend

# 4. Verificar
bash verify.sh
```

---

## Comandos de diagnóstico frecuentes

```bash
# Ver logs de un servicio
docker compose -f /opt/almacenes/docker-compose.yml logs -f backend
docker compose -f /opt/almacenes/docker-compose.yml logs -f frontend

# Ver estado de contenedores
docker compose -f /opt/almacenes/docker-compose.yml ps

# Reiniciar un servicio
docker compose -f /opt/almacenes/docker-compose.yml restart backend

# Ejecutar SQL en la BD
docker compose -f /opt/almacenes/docker-compose.yml exec db psql -U almacenes_user -d almacenes_db

# Ver reglas del firewall
sudo ufw status numbered

# Ver estado del cron de renovación SSL
crontab -l | grep certbot

# Renovar certificado manualmente (simular)
sudo certbot renew --dry-run
```

---

## Solución de problemas comunes

| Problema | Causa probable | Solución |
|---|---|---|
| `permission denied` al usar docker | Usuario no está en grupo docker | Cerrar y reabrir sesión SSH |
| certbot falla con `Connection refused` | DNS no apunta al servidor | Configurar registro A en DNS |
| Puerto 8080 accesible desde internet | ufw no activado o Docker bypassed iptables | Ejecutar `05-firewall.sh` |
| Backend no arranca (`FAIL /actuator/health`) | Error de conexión a BD o JWT_SECRET inválido | Ver logs: `docker compose logs backend` |
| Ruta `/login` devuelve 404 | nginx sin `try_files` para SPA | Verificar `nginx.conf` en el Dockerfile del frontend |
| Certificado expirado | Cron de renovación no funciona | `sudo certbot renew --force-renewal` |

---

*Documento generado junto con el plan de producción `docs/global/plan_salida_produccion_v1_almacenes.txt`.*
