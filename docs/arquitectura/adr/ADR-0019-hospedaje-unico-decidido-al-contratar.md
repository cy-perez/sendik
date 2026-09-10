# ADR-0019 — Un solo hospedaje, elegido al contratar el dominio

**Fecha:** 2026-08-21 · **Estado:** aceptada · **Sustituye a:** ADR-0009
**La decisión que dejó abierta se cerró en:** ADR-0024

> **Actualización del 26 de agosto de 2026.** Esta ADR dejó una cosa sin decidir a
> propósito —dónde se sirve el frontend— y puso la condición para decidirla: al
> contratar el dominio. Ya se contrató `sendik.co`, y **ADR-0024 responde: Cloud
> Run**, con GoDaddy como registrador y DNS. Todo lo demás de esta ADR sigue
> vigente, incluida la lista de requisitos de más abajo, que es justamente lo que
> permitió elegir sin volver a deducir nada.

## Contexto

`ADR-0009` decidió hospedaje escalonado: frontend en Vercel durante el prototipo y
migración a Cloud Run al lanzar. Dos decisiones posteriores la dejaron sin sentido,
y ninguna de las dos la contradecía sola:

- **El despliegue está aplazado** hasta que el proyecto esté lo más completo
  posible (`docs/operacion/entornos.md`). Eso elimina la etapa de prototipo
  hospedada, que era la mitad de ADR-0009 y toda la razón de tener dos etapas.
- **Vercel queda descartado** como proveedor. El hospedaje del sitio se contrata
  junto con el dominio, y el proveedor se elige en ese momento.

Sin etapa de prototipo hospedada, el escalonamiento no tiene qué escalonar: no hay
una primera etapa de la que migrar. Y con Vercel fuera, tampoco hay proveedor
intermedio que justificara la migración.

## Decisión

**Una sola etapa de hospedaje, y su proveedor se decide al contratarlo.**

Hasta el lanzamiento, el frontend se ejecuta en local —`npm start`, o el servidor
de SSR con `npm run serve:ssr`— y se prueba en local, integrado contra los
servicios de GCP en capa gratuita que haga falta. No hay sitio publicado.

Lo que **no** cambia y sigue decidido:

| Pieza | Proveedor | Estado |
|---|---|---|
| Backend | Cloud Run | Decidido, sin cambio |
| Imágenes | Cloud Storage | Decidido (ADR-0018) |
| Secretos | Secret Manager | Decidido |
| Base de datos | PostgreSQL gestionado | Decidido el motor, no el proveedor |
| **Hospedaje del sitio** | **Por definir** | **Se elige al contratar el dominio** |

Es decir: GCP sigue siendo el proveedor de los servicios. Lo que queda abierto es
dónde se sirve el frontend con renderizado en servidor.

## Lo que el hospedaje tenga que ser, tendrá que cumplir esto

Esta lista existe para que la elección se pueda hacer después sin volver a
deducirla:

- **Ejecución de Node 22** para el renderizado en servidor. No es un sitio
  estático: `server.mjs` resuelve el idioma y el tema antes de pintar
  (`frontend/CLAUDE.md`), así que un hospedaje de archivos no sirve.
- **Latencia hacia Colombia comparable a `us-east1`**, que es donde está el
  backend. La configuración anterior fijaba la región `iad1` de Vercel justamente
  por eso: para que la llamada del renderizado en servidor a la API no cruzara el
  continente.
- **Configuración por variable de entorno**, sin valores quemados y sin variables
  propias del proveedor.
- **Despliegue desde la integración continua**, publicando el artefacto que ya
  pasó la verificación, no uno que el proveedor construya por su cuenta más tarde.
  El motivo es el de siempre: lo que se publica tiene que salir del mismo commit
  que se probó, con las versiones exactas del `package-lock.json`.
- **Que no altere ni elimine las cabeceras que manda la aplicación.** Es lo único
  que hace falta pedirle sobre ellas, por lo que sigue.

### Las cabeceras y la caché ya no son requisito del hospedaje: son código

`frontend/vercel.json` era el único sitio donde existían las cuatro cabeceras de
seguridad del sitio y las dos políticas de caché propias. Al borrarlo pasaron a
estar escritas aquí como requisito, y eso era mejor que perderlas pero seguía
siendo la misma trampa: configuración de un proveedor que hay que reimplementar en
cada mudanza, y ya hubo una.

Están implementadas en el servidor de SSR, en `frontend/src/server.ts`:

| Qué | Valor |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | ~~`strict-origin-when-cross-origin`~~ `strict-origin` desde el 10 de septiembre de 2026 |
| `X-Frame-Options` | `DENY` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| Caché de `/fuentes/` | `public, max-age=31536000, immutable` |
| Caché de `/legal/` | `public, max-age=300` |

**La política de referente se endureció al integrar HU-014**, y queda anotado aquí
porque esta tabla es donde está escrito el requisito.
`strict-origin-when-cross-origin` recorta la dirección hacia terceros pero la manda
**entera dentro del mismo origen**, así que cada recurso de
`/catalogo?q=…` viajaba con el texto buscado en la cabecera `Referer`, y el registro
de peticiones de Cloud Run lo guarda junto a la IP: el historial de búsquedas que
esa historia decidió no tener, reconstruido por accidente. `strict-origin` manda
solo el origen en los dos casos. Nada del sitio lee el referente, así que no se
pierde nada. No abre una decisión nueva: es la misma de esta ADR, aplicada más
estrecha.

El middleware se registra antes que todo lo demás, así que las cabeceras van
también en las respuestas que no renderiza Angular: los archivos estáticos y el
500 de configuración incompleta, que son justo las que se olvidan. Y ahora se
pueden probar: `frontend/e2e/cabeceras.spec.ts` las comprueba sobre la respuesta
real del servidor, incluido el archivo estático y el 404. Como configuración de un
proveedor no las comprobaba nada; la única forma de saber si seguían puestas era
abrir el archivo.

Los cinco minutos de la caché de `/legal/` merecen su motivo: los documentos legales viven
en la misma carpeta pública que las fuentes, así que la regla general les daría un
año. Un texto legal con un año de caché es servir una versión que ya no rige
después de cambiarla.

Se conserva, y ahora es más importante que antes, la condición que ADR-0009 puso:
**nada específico del proveedor**. Sin funciones propias, sin su almacenamiento,
sin sus variables mágicas. Antes protegía una migración prevista; ahora protege una
elección que todavía no se ha hecho.

## Motivo

Porque un proveedor intermedio solo se paga con la migración que evita, y ya no
hay ninguna que evitar. Vercel entró en ADR-0009 por dos ventajas concretas
—despliegue inmediato de Angular con SSR y vistas previas por rama para revisar
avances desde el celular—, y las dos servían a una etapa de prototipo que ya no va
a existir: con el despliegue aplazado, no hay nada que previsualizar que no se vea
mejor en local. Lo que quedaba era su costo: una segunda configuración de
despliegue, una segunda cuenta que administrar y una migración pendiente.

Elegir el hospedaje al contratar el dominio también es cuando se puede elegir bien.
Hoy faltan los datos que deciden —qué tráfico hay, qué cuesta el balanceador
frente al hospedaje administrado, si conviene una sola factura—, y una decisión
tomada sin ellos se toma otra vez más tarde.

Frente a fijarlo ya en Cloud Run: sería la respuesta probable y no cuesta nada
dejarla abierta. Un mes antes del lanzamiento se compara con lo que haya, con
precios reales, y si es Cloud Run se escribe entonces. Comprometerlo hoy no
adelanta ningún trabajo, porque el trabajo que adelanta —escribir el despliegue
del frontend— es el mismo que habría que rehacer si la comparación diera otra cosa.

## Consecuencias

- `frontend/vercel.json` se borra. Sus decisiones no se pierden: las cabeceras y
  las dos políticas de caché son ahora código en el servidor de SSR, con pruebas;
  la región queda arriba como requisito del hospedaje.
- El trabajo `frontend` de `.github/workflows/despliegue.yml` se quita. El flujo
  publica solo el backend hasta que haya proveedor, y el interruptor de «no hay
  nada configurado» pasa a depender únicamente de `GCP_PROJECT_ID`.
- **El sitio no tiene ruta de despliegue mientras esto siga así**, y es
  deliberado. Escribirla exige saber contra qué, y el día que se sepa se escribe
  con los requisitos de arriba en la mano.
- Las variables `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` y el secreto `VERCEL_TOKEN`
  desaparecen de la lista de lo que hay que crear.
- ADR-0009 queda como registro de lo que se decidió el 15 de agosto de 2026 y por
  qué cambió. No se edita su contenido.

## Cuándo revisar

Al contratar el dominio y el hospedaje, que es cuando esta ADR deja de decir «por
definir» y se convierte en una decisión con nombre de proveedor. Ese día se escribe
el trabajo de despliegue del frontend y se comprueba la lista de requisitos uno por
uno.
