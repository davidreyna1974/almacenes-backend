-- =============================================================================
-- Script de carga de datos de prueba — Almacenes
-- Fecha: 2026-06-06
-- Limpia datos de prueba anteriores e inserta:
--   30 categorías, 20 proveedores, 50 productos
-- NO modifica: users, roles, user_roles
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. LIMPIEZA (orden FK-safe)
-- ---------------------------------------------------------------------------
DELETE FROM stock_movements;
DELETE FROM sale_order_details;
DELETE FROM sale_orders;
DELETE FROM purchase_order_details;
DELETE FROM purchase_orders;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM suppliers;

-- Reiniciar secuencias para IDs limpios
ALTER SEQUENCE categories_id_seq RESTART WITH 1;
ALTER SEQUENCE suppliers_id_seq RESTART WITH 1;
ALTER SEQUENCE products_id_seq RESTART WITH 1;

-- ---------------------------------------------------------------------------
-- 2. CATEGORÍAS (30 categorías con nombres reales de almacén WMS)
-- ---------------------------------------------------------------------------
INSERT INTO categories (name, description, active, created_by) VALUES
('Electrónica de Consumo',        'Televisores, reproductores, audífonos y dispositivos de entretenimiento.',       true, 605),
('Computación y Periféricos',     'Laptops, desktops, teclados, ratones, monitores y accesorios de cómputo.',      true, 605),
('Telefonía y Comunicaciones',    'Smartphones, tablets, routers, switches y equipos de telecomunicaciones.',      true, 605),
('Electrodomésticos de Línea Blanca', 'Refrigeradores, lavadoras, secadoras, lavavajillas y hornos de cocina.',   true, 605),
('Electrodomésticos de Línea Café',   'Cafeteras, licuadoras, tostadoras, microondas y pequeños electrodomésticos.', true, 605),
('Herramientas Manuales',         'Llaves, desarmadores, pinzas, martillos, sierras y herramientas de mano.',      true, 605),
('Herramientas Eléctricas',       'Taladros, amoladoras, sierras eléctricas, pistolas de calor y rotomartillos.', true, 605),
('Material de Construcción',      'Cemento, varillas, blocks, arena, grava y materiales estructurales.',           true, 605),
('Pinturas y Recubrimientos',     'Pinturas vinílicas, esmaltes, barnices, selladores y primers para interiores y exteriores.', true, 605),
('Plomería y Sanitarios',         'Tuberías, conexiones, válvulas, llaves de paso, regaderas y sanitarios.',      true, 605),
('Electricidad e Iluminación',    'Cables, interruptores, contactos, lámparas LED, reflectores y tableros.',      true, 605),
('Seguridad y Vigilancia',        'Cámaras CCTV, alarmas, cercas eléctricas, control de acceso y cerraduras.',    true, 605),
('Muebles de Oficina',            'Escritorios, sillas ergonómicas, libreros, archiveros y módulos de trabajo.',  true, 605),
('Muebles para el Hogar',         'Salas, comedores, recámaras, colchones, almacenamiento y decoración.',         true, 605),
('Artículos de Limpieza',         'Detergentes, desinfectantes, escobas, trapeadores, cubetas y jabones.',        true, 605),
('Papelería y Oficina',           'Papel, bolígrafos, carpetas, folders, agendas y suministros administrativos.', true, 605),
('Embalaje e Insumos Logísticos', 'Cajas de cartón, cintas, esquineros, estiramiento y materiales de empaque.',   true, 605),
('Equipos de Protección Personal','Cascos, guantes, lentes, tapones auditivos, chalecos y calzado de seguridad.', true, 605),
('Lubricantes y Químicos',        'Aceites lubricantes, grasas, solventes, limpiametales y penetrantes.',         true, 605),
('Neumática e Hidráulica',        'Compresores, mangueras, pistones neumáticos, cilindros hidráulicos y válvulas.',true, 605),
('Equipos de Almacenamiento',     'Racks industriales, pallets, estantería metálica, contenedores y cajones.',    true, 605),
('Equipos de Transporte Interno', 'Montacargas manuales, carretillas, patines hidráulicos y dollies.',            true, 605),
('Climatización y Ventilación',   'Aires acondicionados, ventiladores industriales, extractores y calefactores.', true, 605),
('Jardinería y Exteriores',       'Macetas, tierra, fertilizantes, herramientas de jardín, mangueras y aspersores.',true, 605),
('Alimentos y Bebidas (Seco)',    'Granos, cereales, conservas, condimentos, harinas y productos de larga duración.',true, 605),
('Productos Farmacéuticos',       'Medicamentos OTC, vitaminas, suplementos, primeros auxilios y material médico.',true, 605),
('Ropa de Trabajo y Uniformes',   'Overoles, camisas industriales, pantalones de mezclilla y ropa de protección.', true, 605),
('Calzado Industrial',            'Botas de casquillo, botas dieléctricas, zapatos antideslizantes y plantillas.', true, 605),
('Soldadura y Corte',             'Electrodos, alambres MIG, discos de corte, caretas, guantes de soldador.',     true, 605),
('Medición y Calibración',        'Vernieres, micrómetros, niveles, cintas métricas, medidores láser y manómetros.',true, 605);

-- ---------------------------------------------------------------------------
-- 3. PROVEEDORES (20 proveedores mexicanos con datos completos)
-- ---------------------------------------------------------------------------
INSERT INTO suppliers (rfc, company_name, contact_name, phone, email, address, active, created_by) VALUES
('MEXD840315AB2', 'Distribuidora México Digital S.A. de C.V.',  'Ing. Carlos Mendoza Ríos',      '5555-1234', 'ventas@mexdigital.com.mx',       'Av. Insurgentes Sur 1234, Col. Del Valle, CDMX CP 03100',         true, 605),
('ELCI920601XY3', 'Electro Industrial del Centro S.A. de C.V.','Lic. María Torres Sánchez',      '4422-5678', 'compras@electroindustrial.mx',    'Blvd. Adolfo López Mateos 456, Querétaro QRO CP 76000',           true, 605),
('PROA780910MN4', 'Proveedora Nacional de Almacenes S.A.',      'Sr. Roberto Jiménez Castro',     '3336-9012', 'roberto.jimenez@proalnacional.mx', 'Av. Lázaro Cárdenas 789, Col. Industrial, Guadalajara JAL CP 44940',true, 605),
('TECH950301PQ5', 'TechSupply México S.A. de C.V.',             'Ing. Ana Lucía Vargas Pérez',    '8118-3456', 'ana.vargas@techsupply.mx',         'Av. Constitución 321, Col. Centro, Monterrey NL CP 64000',        true, 605),
('FERR860720RS6', 'Ferretera Industrial del Norte S.A. de C.V.','Sr. Juan Pablo Ortiz Morales',   '6181-7890', 'jpablo.ortiz@ferrenorte.mx',       'Calle Industria 654, Parque Industrial, Chihuahua CHIH CP 31020', true, 605),
('CONS880415TU7', 'Constructora y Suministros del Bajío S.A.',  'Arq. Sandra López Gutiérrez',    '4773-2345', 'sandra.lopez@suministrosbajio.mx', 'Paseo Tecnológico 987, León GTO CP 37545',                        true, 605),
('QUIM730601VW8', 'Químicos y Lubricantes Industriales S.A.',   'Ing. Miguel Ángel Reyes Díaz',   '7222-6789', 'miguel.reyes@quimlub.com.mx',      'Av. de las Industrias 147, Toluca MEX CP 50200',                  true, 605),
('EMBA910310XZ9', 'Empaques y Embalajes del Sureste S.A.',      'Lic. Patricia Hernández Cruz',   '9981-0123', 'patricia.hernandez@embasureste.mx','Periférico Norte 258, Mérida YUC CP 97130',                       true, 605),
('MUEB850625AA0', 'Muebles y Equipos Corporativos S.A. de C.V.','Sr. Fernando Castillo Reyna',    '5556-4567', 'fcastillo@muebcorp.mx',            'Anillo Periférico 369, Col. Mixcoac, CDMX CP 03910',              true, 605),
('LIMP940918BB1', 'Limpieza Profesional e Higiene S.A. de C.V.','Lic. Gabriela Moreno Fuentes',   '2281-8901', 'gmoreno@limpprohigiene.mx',        'Av. 20 de Noviembre 741, Xalapa VER CP 91000',                    true, 605),
('SEGV870203CC2', 'Seguridad y Vigilancia Electrónica S.A.',    'Ing. Alejandro Ruiz Serrano',    '6672-2345', 'alejandro.ruiz@segurelec.mx',      'Blvd. Culiacán 852, Col. Las Quintas, Culiacán SIN CP 80060',     true, 605),
('HERM810715DD3', 'Herramientas y Maquinaria del Pacífico S.A.','Sr. Jorge Antonio Medina Villa', '3221-6789', 'jorge.medina@hermaquipac.mx',      'Av. de la Tecnología 963, Colima COL CP 28010',                   true, 605),
('ROPE960501EE4', 'Ropa de Protección y EPP S.A. de C.V.',      'Lic. Claudia Vázquez Ávila',     '4613-0123', 'claudia.vazquez@ropprotep.mx',     'Calle Textil 174, Aguascalientes AGS CP 20230',                   true, 605),
('SOLD720914FF5', 'Soldadura y Equipos Industriales S.A.',      'Ing. Raúl Espinosa Navarro',     '7771-4567', 'raul.espinosa@soldindustrial.mx',  'Carretera a Pachuca Km 12, Tulancingo HGO CP 43600',              true, 605),
('AGRO990625GG6', 'Agroquímica y Fertilizantes del Valle S.A.','Ing. Laura Ramírez Torres',      '3312-8901', 'laura.ramirez@agrofertivalle.mx',  'Libramiento Sur 285, Zamora MICH CP 59600',                       true, 605),
('CLIT880312HH7', 'Climatización e Instalaciones Térmicas S.A.','Ing. Pablo Guerrero Méndez',    '9993-2345', 'pablo.guerrero@cliterm.mx',        'Av. Kabah 396, Col. Fracc. Chuburná, Cancún QROO CP 77500',       true, 605),
('PAPE760829II8', 'Papelería y Artículos de Oficina Nacional S.A.','Lic. Silvia Acosta Bravo',   '5557-6789', 'silvia.acosta@papeoficial.mx',     'Eje Central Lázaro Cárdenas 507, Col. Doctores, CDMX CP 06720',   true, 605),
('MEDI950115JJ9', 'Medicamentos y Salud Ocupacional S.A.',      'Dr. Antonio Flores Chávez',      '4444-0123', 'antonio.flores@medisaludoc.mx',    'Av. Universidad 618, Col. Narvarte, CDMX CP 03020',               true, 605),
('MEDI810730KK0', 'Medición y Calibración Industrial S.A.',     'Ing. Verónica Soto Ibarra',      '8115-4567', 'veronica.soto@medcalind.mx',       'Parque Industrial Escobedo, Monterrey NL CP 66050',               true, 605),
('DIST900420LL1', 'Distribuidora Logística del Centro S.A.',    'Lic. César Montes Arriaga',      '4423-8901', 'cesar.montes@distlogcentro.mx',    'Av. 5 de Febrero 729, Col. Industrial, Querétaro QRO CP 76130',   true, 605);

-- ---------------------------------------------------------------------------
-- 4. PRODUCTOS (50 productos distribuidos en categorías y proveedores)
-- Columnas: sku, name, description, price, current_stock, minimum_stock,
--           status, active, created_at, reserved_stock, unit_cost,
--           category_id, supplier_id, created_by, version
-- ---------------------------------------------------------------------------
INSERT INTO products
  (sku, name, description, price, current_stock, minimum_stock,
   status, active, created_at, reserved_stock, unit_cost,
   category_id, supplier_id, created_by, version)
VALUES

-- Electrónica de Consumo (cat 1) — Proveedor 1 (México Digital)
('ELEC-TV55-001', 'Televisor LED 55" 4K Smart TV',
 'Pantalla LED 55 pulgadas, resolución 4K UHD, HDR10, conectividad WiFi/Bluetooth, 3 HDMI.',
 12999.00, 45, 10, 'AVAILABLE', true, NOW(), 5, 8500.00, 1, 1, 605, 0),

('ELEC-SP40-002', 'Bocina Inalámbrica Bluetooth 40W',
 'Altavoz portátil 40W RMS, resistente al agua IPX5, batería 12h, conexión BT 5.0.',
 1299.00, 80, 15, 'AVAILABLE', true, NOW(), 3, 780.00, 1, 1, 605, 0),

-- Computación y Periféricos (cat 2) — Proveedor 4 (TechSupply)
('COMP-LT15-003', 'Laptop 15.6" Core i7 16GB RAM',
 'Procesador Intel Core i7 12va generación, 16GB DDR5, SSD 512GB NVMe, pantalla FHD IPS.',
 22500.00, 30, 5, 'AVAILABLE', true, NOW(), 8, 16000.00, 2, 4, 605, 0),

('COMP-KBW-004', 'Teclado Mecánico Inalámbrico TKL',
 'Teclado mecánico 80%, switches rojos táctiles, retroiluminación RGB, batería recargable.',
 899.00, 120, 20, 'AVAILABLE', true, NOW(), 10, 520.00, 2, 4, 605, 0),

('COMP-MON-005', 'Monitor 27" QHD 165Hz IPS',
 'Monitor gaming 27 pulgadas, resolución 2560x1440, 165Hz, 1ms, panel IPS, FreeSync Premium.',
 7500.00, 18, 5, 'AVAILABLE', true, NOW(), 2, 4800.00, 2, 4, 605, 0),

-- Telefonía y Comunicaciones (cat 3) — Proveedor 1
('TELE-SM6P-006', 'Smartphone 6.7" 256GB 5G',
 'Pantalla AMOLED 6.7", 256GB almacenamiento, 5G, cámara triple 108MP, batería 5000mAh.',
 15999.00, 25, 8, 'AVAILABLE', true, NOW(), 5, 10500.00, 3, 1, 605, 0),

('TELE-RUT-007', 'Router WiFi 6 AX3000 Dual Band',
 'WiFi 6, velocidad hasta 3000Mbps, cobertura 180m², 4 puertos LAN Gigabit, MU-MIMO.',
 2499.00, 40, 10, 'AVAILABLE', true, NOW(), 4, 1600.00, 3, 1, 605, 0),

-- Electrodomésticos Línea Blanca (cat 4) — Proveedor 2 (Electro Industrial)
('BLAN-REF19-008', 'Refrigerador Side by Side 19 pies',
 'Refrigerador 19 pies cúbicos, dispensador de agua y hielo, panel digital, compresor inverter.',
 18500.00, 12, 3, 'AVAILABLE', true, NOW(), 2, 12000.00, 4, 2, 605, 0),

('BLAN-LAV17-009', 'Lavadora Automática 17kg Inverter',
 'Lavadora de carga frontal 17kg, motor inverter, 15 programas de lavado, vapor anti-bacterias.',
 14999.00, 8, 3, 'AVAILABLE', true, NOW(), 1, 9800.00, 4, 2, 605, 0),

-- Electrodomésticos Línea Café (cat 5) — Proveedor 2
('CAFE-CAF-010', 'Cafetera Espresso Automática 19 bar',
 'Cafetera espresso 19 bar, molinillo integrado, pantalla táctil, depósito de agua 1.8L.',
 3200.00, 35, 8, 'AVAILABLE', true, NOW(), 5, 2100.00, 5, 2, 605, 0),

('CAFE-LIC-011', 'Licuadora Industrial 2L 1500W',
 'Licuadora de alto rendimiento, 1500W, jarra de tritan 2L, 10 velocidades, base antideslizante.',
 1850.00, 50, 10, 'AVAILABLE', true, NOW(), 3, 1100.00, 5, 2, 605, 0),

-- Herramientas Manuales (cat 6) — Proveedor 5 (Ferretera del Norte)
('HMAN-JLL-012', 'Juego de Llaves Combinadas 14 piezas',
 'Set de llaves combinadas métricas y estándar, acero cromo-vanadio, acabado espejo.',
 650.00, 60, 15, 'AVAILABLE', true, NOW(), 0, 380.00, 6, 5, 605, 0),

('HMAN-DES-013', 'Desarmador de Impacto Manual 6 en 1',
 'Desarmador de impacto con 6 puntas intercambiables, mango bi-material ergonómico.',
 320.00, 90, 20, 'AVAILABLE', true, NOW(), 0, 180.00, 6, 5, 605, 0),

-- Herramientas Eléctricas (cat 7) — Proveedor 12 (Herramientas Pacífico)
('HELEC-TAL-014', 'Taladro Percutor 13mm 900W',
 'Taladro percutor 900W, mandril de 13mm, función percusión/taladro, velocidad variable.',
 1450.00, 40, 10, 'AVAILABLE', true, NOW(), 4, 870.00, 7, 12, 605, 0),

('HELEC-AMO-015', 'Amoladora Angular 4.5" 1400W',
 'Amoladora 4.5 pulgadas, 1400W, 11000 RPM, disco de corte incluido, protección anti-vibración.',
 980.00, 55, 12, 'AVAILABLE', true, NOW(), 6, 590.00, 7, 12, 605, 0),

-- Material de Construcción (cat 8) — Proveedor 6 (Constructora Bajío)
('CONS-CEM50-016', 'Cemento Portland Tipo I Saco 50kg',
 'Cemento Portland ordinario tipo I/II, saco de 50 kilogramos, certificado NMX.',
 185.00, 500, 100, 'AVAILABLE', true, NOW(), 0, 120.00, 8, 6, 605, 0),

('CONS-VAR12-017', 'Varilla Corrugada 3/8" x 12m',
 'Varilla corrugada de acero grado 42, diámetro 3/8 de pulgada, longitud 12 metros.',
 145.00, 1000, 200, 'AVAILABLE', true, NOW(), 0, 95.00, 8, 6, 605, 0),

-- Pinturas y Recubrimientos (cat 9) — Proveedor 6
('PINT-VIN19-018', 'Pintura Vinílica Interior Blanco 19L',
 'Pintura vinílica para interiores, cubeta de 19 litros, acabado mate lavable, rendimiento 50m²/L.',
 890.00, 80, 20, 'AVAILABLE', true, NOW(), 0, 560.00, 9, 6, 605, 0),

('PINT-ESM1G-019', 'Esmalte Sintético Rojo Inglés 1 Galón',
 'Esmalte sintético color rojo inglés, galón 3.78L, secado rápido, resistente a la intemperie.',
 380.00, 40, 10, 'AVAILABLE', true, NOW(), 0, 230.00, 9, 6, 605, 0),

-- Plomería y Sanitarios (cat 10) — Proveedor 6
('PLOM-TUB2-020', 'Tubo CPVC 1/2" x 3m Temperatura',
 'Tubería CPVC diámetro 1/2 pulgada, longitud 3 metros, uso agua caliente hasta 93°C, NMX.',
 95.00, 300, 80, 'AVAILABLE', true, NOW(), 0, 58.00, 10, 6, 605, 0),

-- Electricidad e Iluminación (cat 11) — Proveedor 11 (Seguridad Vilca — reuse prov 2)
('ELUM-LED18-021', 'Panel LED Empotrable 18W Redondo',
 'Panel LED redondo 18W, luz día 6500K, diámetro 22cm, driver incluido, vida útil 25000h.',
 285.00, 150, 30, 'AVAILABLE', true, NOW(), 10, 165.00, 11, 2, 605, 0),

('ELUM-CAB12-022', 'Cable Duplex THW 12 AWG Rollo 100m',
 'Cable duplex THW calibre 12, rollo de 100 metros, conducto de cobre, aislamiento 600V.',
 1250.00, 60, 15, 'AVAILABLE', true, NOW(), 5, 780.00, 11, 2, 605, 0),

-- Seguridad y Vigilancia (cat 12) — Proveedor 11 (Seguridad Vilca)
('SEGV-CAM4K-023', 'Cámara Domo IP 4K PoE Exterior',
 'Cámara CCTV IP domo 4K, visión nocturna 30m, PoE, resistencia IP67, detección de movimiento.',
 2850.00, 35, 8, 'AVAILABLE', true, NOW(), 5, 1750.00, 12, 11, 605, 0),

('SEGV-DVR8-024', 'DVR 8 Canales 4K Ultra HD H.265+',
 'Grabador digital 8 canales, soporte 4K, compresión H.265+, compatible con cámaras TVI/AHD/CVBS.',
 4500.00, 15, 4, 'AVAILABLE', true, NOW(), 2, 2900.00, 12, 11, 605, 0),

-- Muebles de Oficina (cat 13) — Proveedor 9 (Muebles Corporativos)
('MOFI-SIL-025', 'Silla Ejecutiva Ergonómica Malla',
 'Silla ejecutiva con respaldo de malla, reposabrazos ajustables 4D, soporte lumbar, base nylon.',
 3850.00, 25, 5, 'AVAILABLE', true, NOW(), 3, 2400.00, 13, 9, 605, 0),

('MOFI-ESC-026', 'Escritorio en L 160x120 cm Wengué',
 'Escritorio en L para oficina, tablero MDP enchapado wengué, cajones con llave, 160x120cm.',
 4200.00, 12, 3, 'AVAILABLE', true, NOW(), 1, 2750.00, 13, 9, 605, 0),

-- Artículos de Limpieza (cat 15) — Proveedor 10 (Limpieza Profesional)
('LIMP-DES5L-027', 'Desinfectante Multiusos Cítrico 5L',
 'Desinfectante líquido multiusos aroma cítrico, concentrado 5 litros, mata 99.9% gérmenes.',
 185.00, 200, 40, 'AVAILABLE', true, NOW(), 0, 110.00, 15, 10, 605, 0),

('LIMP-PAP-028', 'Papel Higiénico Industrial 2 capas x 48',
 'Papel higiénico rollo jumbo 2 capas, paquete con 48 rollos de 300 hojas, uso institucional.',
 480.00, 120, 24, 'AVAILABLE', true, NOW(), 0, 290.00, 15, 10, 605, 0),

-- Papelería y Oficina (cat 16) — Proveedor 17 (Papelería Nacional)
('PAPE-RES-029', 'Resma Papel Blanco A4 75g 500 Hojas',
 'Resma de papel bond blanco A4 75g/m², 500 hojas, blancura 92%, compatible con impresoras láser e inkjet.',
 120.00, 400, 80, 'AVAILABLE', true, NOW(), 20, 72.00, 16, 17, 605, 0),

('PAPE-BOL-030', 'Bolígrafo Retráctil Azul Caja x 50',
 'Caja de 50 bolígrafos retráctiles punto medio 1.0mm, tinta azul, cuerpo traslúcido.',
 195.00, 80, 20, 'AVAILABLE', true, NOW(), 0, 115.00, 16, 17, 605, 0),

-- Embalaje e Insumos Logísticos (cat 17) — Proveedor 8 (Empaques Sureste)
('EMBA-CAJA-031', 'Caja de Cartón Corrugado 60x40x40 cm',
 'Caja de cartón corrugado calibre doble, dimensiones 60x40x40 cm, capacidad hasta 30kg.',
 28.50, 1000, 200, 'AVAILABLE', true, NOW(), 0, 16.00, 17, 8, 605, 0),

('EMBA-FILM-032', 'Rollo Stretch Film 50cm x 300m',
 'Película estiramiento para embalaje, rollo 50cm de ancho x 300 metros, transparente 23µm.',
 145.00, 80, 20, 'AVAILABLE', true, NOW(), 0, 88.00, 17, 8, 605, 0),

-- EPP (cat 18) — Proveedor 13 (Ropa de Protección)
('EPP-CAS-033', 'Casco de Seguridad Industrial HDPE Blanco',
 'Casco de polietileno de alta densidad, ala completa, ranura para suspensión, cumple NOM-115.',
 185.00, 100, 25, 'AVAILABLE', true, NOW(), 0, 105.00, 18, 13, 605, 0),

('EPP-GUAC-034', 'Guantes de Carnaza para Soldador Par',
 'Guantes de carnaza de primera calidad, protección hasta 300°C, longitud 35cm, talla L.',
 145.00, 150, 30, 'AVAILABLE', true, NOW(), 5, 82.00, 18, 13, 605, 0),

-- Lubricantes y Químicos (cat 19) — Proveedor 7 (Químicos y Lubricantes)
('LUBR-ACE20-035', 'Aceite Lubricante Motor 20W-50 Galón',
 'Aceite mineral para motor convencional, viscosidad 20W-50, galón 3.78L, API SL.',
 380.00, 120, 30, 'AVAILABLE', true, NOW(), 0, 220.00, 19, 7, 605, 0),

('LUBR-SOL-036', 'Solvente Dieléctrico Limpiametales 500mL',
 'Solvente limpiametales dieléctrico, aerosol 500mL, penetra óxido, no corroe plástico ni goma.',
 195.00, 200, 40, 'AVAILABLE', true, NOW(), 0, 118.00, 19, 7, 605, 0),

-- Equipos de Almacenamiento (cat 21) — Proveedor 20 (Distribuidora Logística)
('ALMAC-RACK5-037', 'Rack Metálico Selectivo 5 Niveles 1000kg',
 'Estantería metálica industrial 5 niveles, capacidad 1000kg por nivel, 200x40x250cm, galvanizado.',
 8500.00, 8, 2, 'AVAILABLE', true, NOW(), 0, 5500.00, 21, 20, 605, 0),

('ALMAC-PAL-038', 'Pallet de Plástico 1200x1000 mm',
 'Tarima plástica rígida 1200x1000mm, carga dinámica 1500kg, carga estática 5000kg, HDPE.',
 850.00, 30, 8, 'AVAILABLE', true, NOW(), 0, 520.00, 21, 20, 605, 0),

-- Climatización (cat 23) — Proveedor 16 (Climatización Térmica)
('CLIM-AC12-039', 'Minisplit Inverter 12000 BTU WiFi',
 'Aire acondicionado minisplit inverter, 12000 BTU, WiFi, filtro HEPA, compresor rotativo, R-32.',
 14500.00, 15, 4, 'AVAILABLE', true, NOW(), 2, 9500.00, 23, 16, 605, 0),

-- Soldadura y Corte (cat 29) — Proveedor 14 (Soldadura Industrial)
('SOLD-ELE-040', 'Electrodos Acero Suave 6011 Caja 5kg',
 'Electrodos de soldadura para acero suave tipo 6011, caja de 5kg, diámetro 3/32",   apto SMAW.',
 380.00, 60, 15, 'AVAILABLE', true, NOW(), 0, 225.00, 29, 14, 605, 0),

('SOLD-MAS-041', 'Careta para Soldar Fotosensible',
 'Careta de soldadura con filtro fotosensible automático, grado de oscuridad 9-13, visor 98x43mm.',
 950.00, 40, 10, 'AVAILABLE', true, NOW(), 3, 580.00, 29, 14, 605, 0),

-- Medición y Calibración (cat 30) — Proveedor 19 (Medición Industrial)
('MEDI-VERN-042', 'Vernier Digital 200mm Resolución 0.01mm',
 'Calibrador digital acero inoxidable, rango 0-200mm, resolución 0.01mm, IP54, batería incluida.',
 680.00, 30, 8, 'AVAILABLE', true, NOW(), 2, 410.00, 30, 19, 605, 0),

-- PRODUCTOS FUERA DE STOCK — para pruebas de filtro de estado
('ELEC-PRO-043', 'Proyector Laser 4K 3500 Lúmenes',
 'Proyector láser nativo 4K, 3500 lúmenes ANSI, contraste 3000000:1, HDMI 2.0, sin luz UV.',
 45000.00, 0, 2, 'OUT_OF_STOCK', true, NOW(), 0, 30000.00, 1, 1, 605, 0),

('COMP-SSD1T-044', 'SSD NVMe 1TB PCIe 4.0 7000MB/s',
 'Unidad de estado sólido NVMe M.2 2280, PCIe 4.0 x4, lectura 7000MB/s, escritura 6500MB/s.',
 2800.00, 0, 5, 'OUT_OF_STOCK', true, NOW(), 0, 1800.00, 2, 4, 605, 0),

('BLAN-SEC10-045', 'Secadora de Ropa 10kg Carga Frontal',
 'Secadora eléctrica carga frontal 10kg, motor inverter, 14 programas, sensor de humedad.',
 11500.00, 0, 2, 'OUT_OF_STOCK', true, NOW(), 0, 7800.00, 4, 2, 605, 0),

-- PRODUCTOS DESCONTINUADOS — para pruebas de filtro de estado
('ELEC-TV32-046', 'Televisor LED 32" HD Smart TV (Modelo 2022)',
 'Televisor anterior, 32 pulgadas HD 1366x768, sistema Smart TV propietario descontinuado.',
 4500.00, 5, 0, 'DISCONTINUED', true, NOW(), 0, 2800.00, 1, 1, 605, 0),

('COMP-USB2-047', 'Hub USB 2.0 de 7 Puertos (Descontinuado)',
 'Concentrador USB 2.0 de 7 puertos con fuente de poder, sustituido por versión USB 3.0.',
 185.00, 3, 0, 'DISCONTINUED', true, NOW(), 0, 90.00, 2, 4, 605, 0),

-- PRODUCTOS CON STOCK BAJO (current_stock < minimum_stock) — para pruebas de low-stock
('HELEC-SIE-048', 'Sierra Circular 7-1/4" 1800W',
 'Sierra circular 7-1/4 pulgadas, 1800W, 5800 RPM, guía paralela, bolsa de polvo.',
 2850.00, 3, 8, 'AVAILABLE', true, NOW(), 0, 1750.00, 7, 12, 605, 0),

('PINT-BAR5G-049', 'Barniz marino 5 galones para exteriores',
 'Barniz marino poliuretano 5 galones, resistente rayos UV, agua y hongos, acabado brillante.',
 2100.00, 2, 10, 'AVAILABLE', true, NOW(), 0, 1300.00, 9, 6, 605, 0),

('SEGV-NVR16-050', 'NVR 16 Canales 4K PoE 16 Puertos',
 'Grabador de red NVR 16 canales 4K, 16 puertos PoE integrados, 2 bahías HDD, H.265+.',
 8500.00, 1, 4, 'AVAILABLE', true, NOW(), 0, 5500.00, 12, 11, 605, 0);

COMMIT;

-- Verificación final
SELECT
  (SELECT COUNT(*) FROM categories) AS total_categorias,
  (SELECT COUNT(*) FROM suppliers)  AS total_proveedores,
  (SELECT COUNT(*) FROM products)   AS total_productos,
  (SELECT COUNT(*) FROM products WHERE status = 'AVAILABLE')    AS disponibles,
  (SELECT COUNT(*) FROM products WHERE status = 'OUT_OF_STOCK') AS sin_stock,
  (SELECT COUNT(*) FROM products WHERE status = 'DISCONTINUED') AS descontinuados,
  (SELECT COUNT(*) FROM products WHERE current_stock < minimum_stock AND status = 'AVAILABLE') AS stock_bajo;
