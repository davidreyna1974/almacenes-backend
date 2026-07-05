# Documentación — Almacenes Backend

Índice navegable de toda la documentación del repositorio. Para la visión general del proyecto,
ver el [README principal](../README.md).

---

## 🏗️ Arquitectura y decisiones
| Documento | Contenido |
|---|---|
| [Memoria técnica global](arquitectura/memoria_tecnica_global_proyecto.md) | Visión del sistema, stack, decisiones arquitectónicas, contratos de integración, RBAC transversal, estado del proyecto, lecciones globales (L29–L35). |
| [Diagrama de arquitectura](arquitectura/diagrama_arquitectura.md) | Diagramas Mermaid: capas, estructura interna, flujo de auth, máquinas de estado. |

## 🧩 Memorias técnicas por módulo
Cada módulo documenta contexto, decisiones de diseño, contratos, RBAC, seguridad, tests y bugs.

| Módulo | Documento |
|---|---|
| Reports | [memoria](modulos/reports/memoria_tecnica_modulo_reports.md) |

> Las memorias de los módulos auth, inventory, purchases y sales están consolidadas en la
> [memoria técnica global](arquitectura/memoria_tecnica_global_proyecto.md).

## ✅ Calidad (QA)
| Documento | Contenido |
|---|---|
| [Reporte de QA del sistema](https://github.com/davidreyna1974/almacenes) | Resultado de la campaña de certificación (repo paraguas): 704 casos, 5 módulos, 0 bugs. |
| Protocolo de 4 fases + seguridad server-side | Ver la sección de QA de la [memoria técnica global](arquitectura/memoria_tecnica_global_proyecto.md). |

## 📦 Despliegue y operación
| Documento | Contenido |
|---|---|
| [Instructivo de puesta en producción](../scripts/INSTRUCTIVO_puesta_produccion_almacenes.md) | Guía paso a paso de despliegue (servidor físico o VM en la nube): SSL, firewall, verificación. |
| [Guía de implementación en VM de GCP (prueba)](../scripts/guia_implementacion_vm_gcp_almacenes.txt) | Envuelve el instructivo para una VM en Google Cloud: crear la VM, firewall de VPC, dominio DuckDNS, swap, validación y limpieza. |
| [`scripts/`](../scripts/) | Scripts de despliegue a producción. |
| [`scripts_beta/`](../scripts_beta/) | Variantes locales/beta de los scripts. |
| [`Dockerfile`](../Dockerfile) | Imagen del backend. |

---

> **Convención de versionado/QA:** ante cualquier cambio nuevo se aplica el Protocolo de 4 fases
> y se actualiza la memoria técnica del módulo afectado + la global. Ver también
> [`../CLAUDE.md`](../CLAUDE.md) (convenciones del repo).
