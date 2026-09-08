# Regresión visual: quién mira la maquetación

Hasta el 8 de septiembre de 2026 **nadie la miraba**. La canalización comprobaba
que el HTML del servidor dijera lo que debía, que los pares de color cumplieran su
contraste, que el teclado llegara a todo y que axe no encontrara nada sobre WCAG
2.2 AA. Ninguna de esas cosas se rompe porque un ancho, un espaciado o una
alineación cambien.

El 7 de septiembre se fusionó una migración del sistema de diseño con **la
canalización entera en verde** y el sitio quedó descuadernado. Se revirtió esa
misma noche (ADR-0033). Esto es lo que faltaba para que no vuelva a pasar sin que
nadie se entere.

## Qué se compara

`frontend/e2e/maquetacion.spec.ts` toma la página completa de tres pantallas, en
los dos modos y en dos anchos:

| | portada | cómo funciona | registro |
|---|---|---|---|
| **escritorio** 1280×900 | claro, oscuro | claro, oscuro | claro, oscuro |
| **móvil** 390×844 | claro, oscuro | claro, oscuro | claro, oscuro |

Doce imágenes. Las tres pantallas cubren las tres formas distintas que tiene el
sitio —la portada con su franja de tinta y sus tarjetas, una página larga de
contenido y un formulario—; añadir más del mismo molde multiplica el
mantenimiento sin añadir cobertura.

El ancho de escritorio está **por encima de `--ancho-max`** a propósito, para que
se vea si el centrado deja de aplicarse. Y el modo oscuro se comprueba porque no
es la misma pantalla con otros colores: `marca.css` redefine cosas dentro de
`[data-tema='oscuro']`.

## Qué significa que se ponga roja

**No dice que algo esté mal. Dice que algo cambió.** Puede ser exactamente el
cambio que se buscaba. Lo que hace la prueba es obligar a mirarlo antes de
fusionar, que es lo que no ocurrió el 7 de septiembre.

Cuando falla, la canalización sube las imágenes de diferencia dentro del artefacto
`informes-frontend`, junto al informe de Playwright. Ahí se ve **la referencia, lo
que salió y las dos superpuestas**.

- **Si el cambio no era el que se buscaba**, ya está: se arregla el estilo.
- **Si el cambio era el buscado**, se regeneran las referencias y el commit las
  lleva. Eso convierte un cambio visual en algo que se revisa en un pull request
  en vez de en algo que se descubre en producción.

Lo que **no** se hace es subir el umbral para que pase. El umbral separa el
suavizado de las letras de un descuadre de verdad, y un descuadre mueve órdenes de
magnitud más píxeles que el suavizado.

## Cómo se regeneran las referencias

Las imágenes se generan **dentro del contenedor oficial de Playwright**, no en la
máquina de quien las regenera. El motivo es que la comparación es de píxeles: si
la referencia sale de Windows o de macOS y la comparación ocurre en el Linux de la
integración continua, las doce fallan siempre y la prueba se vuelve ruido que se
acaba ignorando.

La versión de la imagen **tiene que coincidir** con la de `@playwright/test` en
`package.json`. Si no coinciden, el navegador es otro y las referencias tampoco
valen.

```bash
docker run --rm \
  -v "$(pwd)/frontend:/host:ro" \
  -v "$(pwd)/frontend/e2e/__capturas__:/out" \
  -w /app -e CI=1 \
  mcr.microsoft.com/playwright:v1.62.1-noble bash -lc '
    cd /host && tar --exclude=./node_modules --exclude=./dist --exclude=./.angular -cf - . \
      | (mkdir -p /app && cd /app && tar -xf -)
    cd /app && npm ci && npm run build
    npx playwright test maquetacion --update-snapshots
    cp -r /app/e2e/__capturas__/. /out/
  '
```

En Git Bash sobre Windows hace falta `MSYS_NO_PATHCONV=1` delante, o las rutas de
dentro del contenedor se convierten en rutas de Windows y `docker` falla diciendo
que el directorio de trabajo no es absoluto.

## Por qué el sistema operativo va en el nombre de la carpeta

`playwright.config.ts` guarda las referencias en
`e2e/__capturas__/{platform}/`. Así, quien lance estas pruebas desde Windows o
macOS recibe **«falta la referencia»** en vez de una comparación contra imágenes
que no le corresponden. Es la diferencia entre una prueba que no se puede correr
en local y una que se pone roja mintiendo.

Solo se versionan las de `linux`, que son las que usa la integración continua.

## Lo que esto no cubre

- **Que el resultado sea bonito o que la jerarquía visual tenga sentido.** Eso lo
  mira una persona.
- **Las pantallas con sesión**: publicación, moderación, panel del vendedor, mi
  cuenta. Necesitan datos y autenticación, y hoy eso vive en `e2e-completo/`, que
  es otro flujo y otro coste. Si alguna se descuaderna, esto no lo ve.
- **El catálogo y la ficha de producto.** Dependen de datos que cambian, y una
  referencia de píxeles contra contenido variable falla por el contenido y no por
  la maquetación.

Las tres ausencias son deliberadas. Lo que cubre son las pantallas que ve
cualquiera sin cuenta, que son las que estaban rotas el 7 de septiembre.
