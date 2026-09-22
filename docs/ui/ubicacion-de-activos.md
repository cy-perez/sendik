# Dónde vive cada archivo de marca y de interfaz

Los paquetes de marca y de interfaz traen tres clases de archivo y cada una va a
un sitio distinto. Confundirlas es el error habitual: terminan 40 PNG dentro del
build de producción y el favicon devolviendo 404.

| Clase | Ejemplo | Dónde |
|---|---|---|
| Código que compila | `tokens.css`, `tipografia.css`, `marca.css`, `fuentes.css` | `frontend/src/styles/` |
| Activo que el navegador pide por URL | favicons, manifiesto, logos SVG, `.woff2` | `frontend/public/` |
| Documentación y original | manual, informe de contraste, el generador | `docs/` |

## Ya ubicado

```
frontend/src/styles/
  fuentes.css     @font-face de los dos .woff2. Del proyecto
  tokens.css      copia publicada. NO se edita: la sobrescribe publicar.py
  tipografia.css  del proyecto. Única fuente de verdad del tipo
  marca.css       del proyecto. Componentes y ajustes de modo oscuro
frontend/src/styles.css   importa los cuatro, en ese orden

frontend/public/
  favicon.ico, favicon.svg, apple-touch-icon.png, favicon-*.png
  icon-192.png, icon-512.png, icon-512-maskable.png, site.webmanifest
  og-image.png, twitter-card.png
  hero.webp       la foto del hero de la portada, 1200×900 (HU-004, 22-09-2026)
  logo-horizontal.svg, logo-mono-negativo.svg, isotipo.svg, isotipo-negativo.svg
  fuentes/        inter-latin, inter-latin-ext, archivo-latin,
                  archivo-latin-ext (.woff2) y sus licencias

docs/ui/     el kit construido: index.html (guía visual), tokens.css,
             contraste.md, tipografia.md, LEEME.md, ENTREGA.md, los SVG que usa
             la guía y las tipografías autoalojadas
docs/ui/generador/   el origen: tokens.json, construir.py, verificar.py,
             publicar.py, y kit_ui.py / fuentes.py / la plantilla, que llegan de
             diseño
docs/marca/  manual.md, tokens de marca, revision-visual.html y el paquete de
             identidad completo
```

En Angular, todo lo que está en `public/` se sirve desde la raíz del sitio. Por
eso el favicon se pide como `/favicon.ico` y el logo se usa como
`<img src="/logo-horizontal.svg" alt="Sendik">`. El logo no se redibuja en
código: son 3KB de vector y se ve nítido en cualquier pantalla.

## Ojo: hay dos `tokens` y no son lo mismo

El sistema tiene dos niveles y cada uno tiene su archivo. Confundirlos rompe la
paleta entera.

| Archivo | Qué contiene | Se carga en el sitio |
|---|---|---|
| `docs/marca/tokens.json` | Los colores aprobados de marca: tinta, bronce, sus dos tonos | No |
| `docs/marca/tokens.css` | Los mismos, como variables. Referencia para piezas gráficas y para la app | No |
| `docs/ui/generador/tokens.json` | El mapa de roles: qué color de marca cumple cada función de interfaz. **Aquí se edita** | No |
| `docs/ui/tokens.css` | Salida del generador, con los estados y el modo oscuro derivados | No |
| `frontend/src/styles/tokens.css` | Copia publicada de la anterior | Sí |

La cadena va en un solo sentido:

```
docs/marca/tokens.json            colores aprobados (tinta, bronce claro y oscuro)
        ↓
docs/ui/generador/tokens.json     mapa de roles (primario, acento, exito, aviso, error)
        ↓  construir.py
docs/ui/tokens.css                variables finales, con hover, pressed y modo oscuro
        ↓  publicar.py
frontend/src/styles/tokens.css    lo que sirve el sitio
```

Cada eslabón hacia abajo lo escribe un script. Los dos últimos son **salida**: si
se editan a mano, la siguiente ejecución los sobrescribe y el trabajo se pierde.

Un color nuevo se agrega arriba y baja. Nunca al revés, y nunca se escribe
directo en el archivo final: es generado.

Lo único que la interfaz agregó sobre la marca es el color de **aviso**, que la
marca no traía. Tiene matiz propio —24° frente a los 35° del bronce— justamente
para que «aviso» y «verificado» no se confundan.

## Una hoja generada y tres del proyecto

Antes las tres hojas se generaban y se publicaban juntas, porque el kit anterior
las entregaba acopladas. El de Sendik entrega **solo `tokens.css`**, así que
`tipografia.css`, `marca.css` y `fuentes.css` pasaron a ser del proyecto y se
editan directamente en `frontend/src/styles/`.

No es solo simplificación. Mantener la cadena vieja exigía parchear a mano unos
treinta fragmentos de la plantilla del kit dentro de `construir.py`, cada uno
atado al texto exacto de esa plantilla. Eso hacía que **una entrega nueva de
diseño no se pudiera integrar sin rehacer los parches**, que es exactamente lo
que pasó al llegar Sendik.

El hook `proteger-archivos.mjs` bloquea la escritura sobre lo generado
—`tokens.css` en sus dos copias, `index.html`, `contraste.md`, `tipografia.md`,
`LEEME.md`—, sobre los tres scripts que entrega diseño y sobre todo
`docs/marca/`. En el mensaje indica dónde se edita de verdad.

**La excepción son los binarios.** Los favicons, los iconos, los cuatro SVG y los
dos `.woff2` que usa la web sí están dos veces: el original en `docs/marca/dist/`
y `docs/ui/fuentes/`, que es el archivo, y la copia en `frontend/public/`, que es
lo que el servidor entrega. No hay forma de que Angular sirva algo desde `docs/`.
Si cambia el logo, se pide el paquete nuevo a diseño y se vuelven a copiar los
mismos archivos; están listados arriba para que la copia sea mecánica.

## Divergencias conocidas con el kit

Tres. Las dos primeras son decisiones del proyecto; la tercera es un defecto
del generador que aquí se corrige.

**Proporción de la foto de producto: 3:4, no 1:1.** El kit de Sendik fija 1:1 con
mínimo de 800×800. Aquí manda **ADR-0010** y su razonamiento no ha cambiado: una
prenda colgada es más alta que ancha, el visor de ocho tomas comparte canal de
imágenes con el catálogo, y un solo recorte evita tarjetas de alturas distintas.
Vive en `--relacion-foto`, en `marca.css`. Si algún día se adopta el 1:1, es una
ADR nueva, no un cambio de token.

**Respaldo tipográfico para Archivo.** El generador clasifica el rol «display»
como serif y le pone Georgia detrás. Archivo es una sans: con Georgia de
respaldo, el salto al cargar cambia de género tipográfico y se ve como un error.
`tipografia.css` sobrescribe la familia con un respaldo sans ajustado.

⚠️ **El generador documenta el peso fuerte como 700 y son 600.** `kit_ui.py`
lo tiene quemado y no lo lee de `tokens.json`: sale así en la columna Peso de
`tipografia.md` y en las muestras de las dos tablas de `index.html`. La fila de
familias de la guía llegaba a contradecirse sola, pintando Archivo a 700 con la
columna de pesos diciendo 600. El peso correcto es **600**: es el único que la
marca define para Archivo y el máximo que usa Inter (manual de marca, ADR-0011),
y `tokens.css` sí lo deriva bien. Lo corrige `corregir_peso_fuerte()` en
`construir.py` después de cada generación, por lo mismo que el caso de las
itálicas: `kit_ui.py` es entregable de diseño y un arreglo dentro de él se pierde
con la entrega siguiente. **Está por reportar a diseño**, junto con `fuentes.py`.
El día que el generador lea el peso de `tokens.json`, la función deja de
encontrar nada que cambiar y se puede borrar.

## Lo que NO va al frontend

Se queda en `docs/marca/` como archivo: los iconos de iOS y Android
(`dist/app/`), las piezas de redes distintas de og-image y twitter-card
(`dist/social/`), los PNG y WebP de respaldo del logo (`dist/raster/`) y los SVG
que no usa la web (vertical, mono positivo, con eslogan, icono de app, banner).

Los iconos de aplicación se necesitarán cuando exista la app móvil. Copiarlos
ahora al frontend solo agrega peso al repositorio y confusión sobre cuál es el
bueno.

## Las fuentes

Dos familias y cuatro archivos: cada familia en subconjunto latino y latino
extendido. Al ser variables, cada archivo cubre el rango de pesos entero, así que
el sistema usa tres pesos —400, 500 y 600— sin tres descargas.

No hay familia monoespaciada. La había, solo para que los precios alinearan en
columna, y el manual de Sendik resuelve ese caso con
`font-variant-numeric: tabular-nums` sobre Inter.

Se autoalojan, no se cargan desde el CDN de Google: una petición menos a un
tercero, un punto de fallo menos y ningún dato del visitante saliendo del sitio.

⚠️ **Los archivos NO son los que trajo el kit.** El kit entregó las variantes
**itálicas** de las dos familias, y con ellas el sitio entero se veía inclinado
aunque el CSS dijera `font-style: normal`. El defecto está en `fuentes.py`, que
toma `variables[:1]` sobre la lista de google/fonts y ahí
`Inter-Italic[opsz,wght].ttf` ordena antes que `Inter[opsz,wght].ttf`. Está por
reportar a diseño. Los archivos actuales salieron de la API de Google Fonts, que
además los entrega subconjuntados: 155 KB frente a los 586 KB del kit. Detalle
completo en `frontend/public/fuentes/LEEME.md`, y la regresión fijada en
`frontend/e2e/tipografia.spec.ts`.
