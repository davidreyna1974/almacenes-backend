# Despliegue beta local — Almacenes

**Objetivo:** validar el despliegue completo en una VM Ubuntu local usando Lima,
sin registro DNS ni Let's Encrypt. Máximamente cercano a producción.

**Dominio simulado:** `almacenes.codigo2enter.com` (vía `/etc/hosts`)
**Certificado:** autofirmado (reemplaza Let's Encrypt)
**Puertos en el Mac:** 10080 → VM:80 | 10443 → VM:443

---

## Desde el Mac

**Paso 1 — Instalar Homebrew (si no está instalado)**
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

**Paso 2 — Instalar Lima**
```bash
brew install lima
```

**Paso 3 — Crear la VM Ubuntu 24.04** *(~2–5 min la primera vez — descarga imagen)*
```bash
limactl start --name=almacenes-beta \
  "/Users/davidreynapineda/Documents/Proyecto desarrollo/codigo/backend/almacenes/scripts_beta/almacenes-beta.yaml"
```
> Si falla con `vmType: vz`, editar `almacenes-beta.yaml` y cambiar a `vmType: qemu`.

**Paso 4 — Entrar a la VM**
```bash
limactl shell almacenes-beta
```

---

## Dentro de la VM (todos los pasos siguientes)

**Paso 5 — Definir ruta a scripts_beta** *(ejecutar en cada sesión nueva)*
```bash
BETA="/Users/davidreynapineda/Documents/Proyecto desarrollo/codigo/backend/almacenes/scripts_beta"
```

**Paso 6 — Preparar el servidor** *(instala Docker, git, dependencias — ~5 min)*
```bash
sudo bash "$BETA/01-prepare-server.sh"
```

**Paso 7 — Reabrir sesión** *(obligatorio para activar el grupo `docker`)*
```bash
exit
```
```bash
limactl shell almacenes-beta
BETA="/Users/davidreynapineda/Documents/Proyecto desarrollo/codigo/backend/almacenes/scripts_beta"
```

**Paso 8 — Clonar repositorios**
```bash
sudo mkdir -p /opt/almacenes/backend /opt/almacenes/frontend
sudo chown -R "$(whoami):$(whoami)" /opt/almacenes

git clone https://github.com/davidreyna1974/almacenes-backend.git /opt/almacenes/backend
git -C /opt/almacenes/backend checkout main

git clone <URL-REPO-FRONTEND> /opt/almacenes/frontend
git -C /opt/almacenes/frontend checkout main
```
> Sustituir `<URL-REPO-FRONTEND>` con la URL real del repositorio frontend.

**Paso 9 — Agregar dominio en `/etc/hosts` de la VM**
```bash
echo "127.0.0.1  almacenes.codigo2enter.com" | sudo tee -a /etc/hosts
```

**Paso 10 — Generar certificado autofirmado**
```bash
sudo bash "$BETA/02-ssl-local.sh"
```

**Paso 11 — Desplegar** *(builds Docker, inicializa BD, levanta servicios)*
```bash
bash "$BETA/03-deploy.sh"
```
> El script pedirá confirmación antes de sobrescribir `.env` y `docker-compose.yml`.
> En primera ejecución: presionar `s` para aceptar valores generados.

**Paso 12 — Firewall** *(opcional en beta)*
```bash
sudo bash "$BETA/04-firewall.sh"
```

**Paso 13 — Verificar** *(8/8 tests deben pasar)*
```bash
bash "$BETA/05-verify-local.sh"
```

---

## Desde el Mac — prueba en navegador

**Paso 14 — Agregar dominio en `/etc/hosts` del Mac**
```bash
echo "127.0.0.1  almacenes.codigo2enter.com" | sudo tee -a /etc/hosts
```

**Paso 15 — Abrir en el navegador**
```
https://almacenes.codigo2enter.com:10443
```
> Aceptar la advertencia de certificado autofirmado (Avanzado → Continuar).

---

## Limpiar al terminar

**Desde el Mac:**
```bash
limactl stop almacenes-beta
limactl delete almacenes-beta
sudo sed -i '' '/almacenes.codigo2enter.com/d' /etc/hosts
```
