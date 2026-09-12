# Decisiones de arquitectura

Una ADR registra una decisión y, sobre todo, **por qué** se tomó. Sirve para no
volver a discutir lo mismo dentro de seis meses y para que quien llegue después
entienda el razonamiento en lugar de suponerlo.

## Cuándo se escribe una

- Se elige entre dos o más opciones técnicas con consecuencias duraderas.
- Se agrega, cambia o elimina una dependencia importante.
- Se cambia la estructura de módulos o el grafo de dependencias.
- Se acepta deliberadamente una deuda técnica.

No se escribe una ADR para decisiones reversibles en una tarde.

## Cómo

Se copia `PLANTILLA.md`, se numera consecutivamente y no se borra nunca. Una
decisión que se revierte no se elimina: se marca como sustituida y la nueva ADR
la referencia.

## Índice

| ADR | Decisión | Estado |
|---|---|---|
| 0001 | Monorepo y arquitectura por capas estricta | Aceptada |
| 0002 | Gradle multi-módulo para hacer cumplir las capas | Aceptada |
| 0003 | Autenticación con JWT propio y refresco rotatorio | Aceptada |
| 0004 | Spring Data JDBC en lugar de JPA | Aceptada |
| 0005 | Wompi con división de pago | Aceptada con riesgo abierto |
| 0006 | Angular con renderizado en servidor desde el inicio | Aceptada |
| 0007 | TanStack Query pese a su estado experimental | Aceptada con condiciones |
| 0008 | Búsqueda con Typesense | Aceptada, se implementa en Fase 3 |
| 0009 | Hospedaje escalonado: Vercel primero, GCP después | Sustituida por ADR-0019 |
| 0010 | Ocho tomas a 45 grados y proporción 3:4 para el visor | Aceptada |
| 0011 | El tipo se aplica por rol, no por tamaño | Aceptada |
| 0012 | Resend como proveedor de correo transaccional | Aceptada |
| 0013 | Contraseñas filtradas con Have I Been Pwned y k-anonimato | Aceptada |
| 0014 | Ventana de gracia en la detección de reutilización del refresco | Aceptada |
| 0015 | Generación de identificadores: UUID v7 o v4 | Aceptada, v7 |
| 0016 | Accesibilidad automatizada con axe-core en las pruebas de extremo a extremo | Aceptada |
| 0017 | Una suite de extremo a extremo que cruza las dos mitades | Aceptada |
| 0018 | Almacenamiento de archivos: dos almacenes y subida por el backend | Aceptada |
| 0019 | Un solo hospedaje, elegido al contratar el dominio | Aceptada; su decisión abierta la cierra ADR-0024 |
| 0020 | Cifrado de datos sensibles: AES-GCM en la aplicación con HMAC indexado | Aceptada |
| 0021 | Guard de ruta por rol, y qué se renderiza en el servidor | Aceptada |
| 0022 | Cambio de identidad a Sendik y sustitución del sistema visual | Aceptada |
| 0023 | Transporte de correo compartido entre contextos | Aceptada |
| 0024 | El sitio se hospeda en Cloud Run; GoDaddy es el registrador y el DNS | Aceptada |
| 0025 | Datos remotos en el renderizado en servidor | Aceptada |
| 0026 | La cámara sube a `shared` y la nitidez se queda | Aceptada |
| 0027 | El borrador de captura vive en IndexedDB | Aceptada |
| 0028 | Las acciones del CI suben de versión mayor en bloque | Aceptada con riesgo abierto |
| 0029 | Volver al sitio después de ingresar: destino en la URL, intención en `sessionStorage` | Aceptada |
| 0030 | La carrera del refresco se nombra, y no cierra la sesión | Aceptada |
| 0031 | La entrega del correo transaccional se saca del contenedor: Cloud Tasks | Aceptada e implementada; comprobada de punta a punta en `dev` el 8 de septiembre de 2026 |
| 0032 | Tailwind como motor de estilos y fin del kit generado | **Revertida** el mismo día; ver ADR-0033 |
| 0033 | La reversión de la migración a Tailwind, y qué se recupera de ella | Aceptada. Deja Tailwind aplazado, no descartado |
| 0034 | Skydropx Colombia como agregador de envíos, y el envío a cargo del comprador | Aceptada |
| 0035 | La búsqueda arranca en PostgreSQL, detrás del puerto de ADR-0008 | Aceptada. Deja Typesense aplazado, no descartado |
| 0036 | El carrito vive en `catalog` y no en `order` | Aceptada |
| 0037 | El carrito sin sesión vive en el navegador y se fusiona preguntando | Aceptada |
| 0038 | La dirección del cliente se cuenta desde el final de `X-Forwarded-For` | Aceptada |
| 0039 | La dirección de entrega vive en `identity`, y se cifra | Aceptada |
| 0040 | El municipio se elige de una lista sembrada, no se escribe | Aceptada |
