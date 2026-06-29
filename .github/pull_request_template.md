<!-- Plantilla de Pull Request — Almacenes Backend -->

## Descripción
<!-- ¿Qué cambia y por qué? -->

## Tipo de cambio
- [ ] `feature/` — nueva funcionalidad o módulo
- [ ] `fix/` — corrección
- [ ] `chore/` — infraestructura, configuración o documentación

## Checklist (Protocolo del repositorio)
- [ ] La rama parte de `develop` y se integra vía `merge --no-ff` (nunca commit directo a `main`/`develop`).
- [ ] `mvn clean test` → 0 fallos (BUILD SUCCESS).
- [ ] Cobertura JaCoCo ≥ 70% en el módulo afectado.
- [ ] Endpoints nuevos con autorización por rol (RBAC) y, si exponen campos sensibles, redacción server-side (L29).
- [ ] Errores coherentes: `401` (no autenticado) vs `403` (sin permiso); parámetros malformados → `400` sin filtrar tipos internos.
- [ ] Verificación de seguridad server-side con `curl` + JWT por rol cuando aplique.
- [ ] Memoria técnica del módulo (`docs/modulos/`) y/o global (`docs/arquitectura/`) actualizadas.

## Evidencia de pruebas
<!-- Pega el resumen de tests (mvn test) / verificación curl relevante -->
