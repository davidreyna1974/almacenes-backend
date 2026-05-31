# Memoria Técnica — Módulo `reports`

## 1. Propósito del módulo

El módulo `reports` centraliza todos los endpoints de consulta analítica del sistema de almacenes.
No genera ningún efecto lateral en la base de datos (todos sus métodos son `readOnly = true`).
Expone tres categorías de informes:

- **Ejecutivos**: KPIs financieros de alto nivel para gerencia (dashboard, valuación de inventario, rentabilidad).
- **Gestión**: Análisis de productos, tendencias de venta, análisis ABC, rotación de inventario y compras por proveedor.
- **Operativos**: Stock bajo mínimo, Kardex de producto, operaciones pendientes, resumen de movimientos.

## 2. Decisiones de diseño

| Decisión | Justificación |
|---|---|
| Sin entidades JPA propias | El módulo es exclusivamente de lectura — agrega datos de entidades existentes. Crear entidades nuevas introduciría riesgo de migración innecesaria. |
| Sin mappers MapStruct | Los DTOs de reports se construyen directamente en los servicios mediante builders. Los datos provienen de `Object[]` de queries JPQL, no de entidades completas — MapStruct no aporta valor aquí. |
| Tres servicios separados | `ExecutiveReportService`, `ManagementReportService` y `OperationalReportService` reflejan las tres audiencias del sistema. Facilita el control de acceso por rol en SecurityConfig. |
| Fechas como `LocalDate` en API | Los endpoints aceptan `from`/`to` como `LocalDate` (sin hora). El servicio convierte a `LocalDateTime` con `atStartOfDay()` y `plusDays(1).atStartOfDay()` para crear rangos inclusivos de día completo. |
| Queries JPQL en repositorios existentes | Evita crear repositorios de reports (que no tendrían entidad asociada). Las queries nuevas se agregan a los repositorios del módulo dueño de cada entidad. |
| Separación `currentStock` vs `availableStock` vs `reservedStock` | La Fase de inventario ya establece estas tres magnitudes. El Kardex usa `currentStock` actual como punto de partida para reconstruir el saldo histórico hacia atrás. |

## 3. Contratos de API (endpoints)

Base path: `/api/v1/reports`

### Ejecutivos
| Método | Path | Roles | Descripción |
|---|---|---|---|
| GET | `/dashboard/executive` | ADMIN | KPIs financieros con filtro de período opcional |
| GET | `/inventory/valuation` | ADMIN, MANAGER | Valuación del inventario por categoría |
| GET | `/sales/profitability` | ADMIN, MANAGER | Rentabilidad de ventas en un período |

### Gestión
| Método | Path | Roles | Descripción |
|---|---|---|---|
| GET | `/products/top-performers` | ADMIN, MANAGER | Top N productos por revenue |
| GET | `/inventory/abc` | ADMIN, MANAGER | Clasificación ABC de productos |
| GET | `/inventory/turnover` | ADMIN, MANAGER | Rotación de inventario |
| GET | `/purchases/by-supplier` | ADMIN, MANAGER | Compras agrupadas por proveedor |
| GET | `/sales/trend` | ADMIN, MANAGER | Tendencia de ventas por período |

### Operativos
| Método | Path | Roles | Descripción |
|---|---|---|---|
| GET | `/inventory/low-stock` | ADMIN, MANAGER, WAREHOUSEMAN | Productos bajo stock mínimo |
| GET | `/inventory/kardex/{productId}` | ADMIN, MANAGER, WAREHOUSEMAN | Movimientos históricos de un producto |
| GET | `/operations/pending` | ADMIN, MANAGER, WAREHOUSEMAN, SALES | Órdenes pendientes (compras y ventas) |
| GET | `/inventory/movements` | ADMIN, MANAGER, WAREHOUSEMAN | Resumen de movimientos en un período |

## 4. Dependencias entre módulos

```
reports/service
  ├── uses → inventory/repository/ProductRepository        (valuación, stock bajo, Kardex)
  ├── uses → inventory/repository/StockMovementRepository  (Kardex, resumen movimientos)
  ├── uses → sales/repository/SaleOrderDetailRepository    (revenue, COGS, top products, ABC)
  ├── uses → sales/repository/SaleOrderRepository          (tendencias, pendientes)
  └── uses → purchases/repository/PurchaseOrderRepository  (compras por proveedor, pendientes)
```

Ningún servicio de reports modifica datos — solo consume repositorios en modo `readOnly`.

## 5. Queries JPQL agregadas

Se agregaron las siguientes queries a repositorios existentes:

### SaleOrderDetailRepository
- `sumRevenue(from, to)` — suma de subtotales de ventas DELIVERED en período
- `sumCogs(from, to)` — suma de quantity × unitCost de ventas DELIVERED en período
- `revenueByProduct(from, to)` — revenue agrupado por producto (retorna `Object[]`)
- `cogsByProduct(from, to)` — COGS agrupado por producto (retorna `Object[]`)
- `quantitySoldByProduct(from, to)` — cantidad vendida por producto (retorna `Object[]`)
- `countDeliveredOrders(from, to)` — cuenta de órdenes DELIVERED en período

### ProductRepository
- `inventoryValueByCategory()` — valuación del inventario agrupado por categoría
- `totalInventoryValue()` — valor total del inventario activo

### StockMovementRepository
- `findByProductAndPeriod(productId, from, to)` — movimientos de un producto en período (ASC)
- `sumInByPeriod(from, to)` — total de entradas en período
- `sumOutByPeriod(from, to)` — total de salidas en período

### PurchaseOrderRepository
- `countPendingAndApproved()` — cuenta de órdenes activas (PENDING + APPROVED)
- `totalsBySupplier(from, to)` — totales agrupados por proveedor en órdenes RECEIVED
- `findPendingAndApproved()` — órdenes PENDING y APPROVED para reporte de pendientes

### SaleOrderRepository
- `countPendingAndApproved()` — cuenta de órdenes activas (PENDING + APPROVED)
- `revenueByPeriod(from, to, format)` — revenue agrupado por período con `TO_CHAR`
- `findPendingAndApproved()` — órdenes PENDING y APPROVED para reporte de pendientes

## 6. Estructura de archivos creados

```
src/main/java/com/codigo2enter/almacenes/modules/reports/
├── controller/ReportController.java
├── dto/
│   ├── executive/
│   │   ├── ExecutiveDashboardDTO.java
│   │   ├── InventoryValuationDTO.java
│   │   ├── InventoryValuationCategoryDTO.java
│   │   └── SalesProfitabilityDTO.java
│   ├── management/
│   │   ├── TopProductDTO.java
│   │   ├── AbcProductDTO.java
│   │   ├── InventoryTurnoverItemDTO.java
│   │   ├── PurchaseBySupplierDTO.java
│   │   └── SalesTrendItemDTO.java
│   └── operational/
│       ├── LowStockReportItemDTO.java
│       ├── KardexItemDTO.java
│       ├── KardexReportDTO.java
│       ├── PendingOrderSummaryDTO.java
│       ├── PendingOperationsDTO.java
│       └── MovementsSummaryDTO.java
└── service/
    ├── ExecutiveReportService.java
    ├── ExecutiveReportServiceImpl.java
    ├── ManagementReportService.java
    ├── ManagementReportServiceImpl.java
    ├── OperationalReportService.java
    └── OperationalReportServiceImpl.java

src/test/java/com/codigo2enter/almacenes/modules/reports/
├── service/
│   ├── ExecutiveReportServiceImplTest.java
│   ├── ManagementReportServiceImplTest.java
│   └── OperationalReportServiceImplTest.java
├── controller/ReportControllerTest.java
└── repository/ReportRepositoryTest.java
```

## 7. Resultado de ejecución de tests

```
./mvnw test  — 2026-05-31

Tests nuevos del módulo reports:
  ExecutiveReportServiceImplTest   :  11 tests
  ManagementReportServiceImplTest  :  17 tests
  OperationalReportServiceImplTest :  12 tests
  ReportControllerTest             :  14 tests
  ReportRepositoryTest             :   7 tests
  SUBTOTAL REPORTS                 :  61 tests

Suite completa:
  Tests run: 353, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
```

**Tests previos**: 239 tests (antes de implementar reports)
**Tests nuevos**: 61 tests del módulo reports + 53 de SecurityFilterTest/RBAC/integraciones (que creció de 19 a 33 en SecurityFilterTest durante versiones anteriores)
**Total después**: 353 tests — todos pasan.

## 8. Bugs encontrados durante implementación

### Bug 1: `FUNCTION('TO_CHAR', ..., :format)` con GROUP BY falla en PostgreSQL

**Síntoma**: `ERROR: column "so1_0.delivered_at" must appear in the GROUP BY clause or be used in an aggregate function`

**Causa**: PostgreSQL trata cada bind variable `?` como una expresión separada. Cuando se usa `GROUP BY FUNCTION('TO_CHAR', so.deliveredAt, :format)`, el driver JDBC envía `GROUP BY TO_CHAR(delivered_at, ?)` con el mismo valor `?` que en el SELECT. Sin embargo, PostgreSQL no puede probar que `TO_CHAR(delivered_at, ?)` en SELECT y GROUP BY son la misma expresión cuando el argumento es un parámetro — necesita ver el literal.

**Por qué los tests de servicio (Tipo A) no lo detectaron**: `SaleOrderRepository` está mockeado en los tests unitarios.

**Corrección**: cambiar a `nativeQuery = true` con una subquery que calcule el período en el subselect y haga GROUP BY sobre el alias:

```sql
SELECT period, COALESCE(SUM(total_amount), 0), COUNT(id)
FROM (SELECT total_amount, id, TO_CHAR(delivered_at, :format) AS period
      FROM sale_orders
      WHERE status = 'DELIVERED' AND delivered_at >= :from AND delivered_at < :to) sub
GROUP BY period ORDER BY period
```

Este patrón de subquery garantiza que PostgreSQL materialice la columna `period` antes del agrupamiento, eliminando la ambigüedad.

**Detectado por**: `ReportRepositoryTest.revenueByPeriod_formatoMensual_funcionaEnPostgresql` (Tipo D)

## 9. Estándares aplicados

- Todos los métodos de servicio son `@Transactional(readOnly = true)` — no hay escritura.
- Inyección por constructor con `@RequiredArgsConstructor` y campos `final`.
- DTOs usan `@Data @Builder @NoArgsConstructor @AllArgsConstructor` de Lombok.
- Javadoc explica el **por qué** de cada método y criterios de éxito.
- Tests tipo A (Mockito puro) sin contexto Spring para servicios.
- Tests tipo B (`@WebMvcTest` + `addFilters=false`) para controladores.
- Tests tipo D (`@DataJpaTest` + `replace=NONE`) para repositorios con PostgreSQL real.

## 10. Checklist de criterios de éxito

- [x] `./mvnw test` pasa con 0 fallos (suite completa: 353 tests)
- [x] Los 239 tests previos siguen pasando
- [x] Los nuevos tests cubren happy path + validaciones de borde
- [x] SecurityConfig tiene las 6 reglas de autorización correctas
- [x] Todos los endpoints retornan `ResponseEntity<T>` con HTTP 200
- [x] Dashboard con `from`/`to` null usa defaults seguros (sin NPE)
- [x] Kardex calcula saldo acumulado correctamente (acumulación iterativa)
- [x] ABC clasifica correctamente A<=80%, B<=95%, C=resto
- [x] Profitability lanza excepción cuando `from`/`to` son null
- [x] revenueByPeriod usa nativeQuery con subquery para evitar bug de GROUP BY en PostgreSQL
