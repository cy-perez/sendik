# Textos legales

Cada archivo se llama `<documento>.<version>.<idioma>.html`, donde `<documento>`
es `terms`, `privacy` o `cookies`.

La versión del nombre **tiene que coincidir** con la variable de entorno
correspondiente: `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION` y
`LEGAL_COOKIES_VERSION`. Es lo que ata el texto que se muestra al que quedó
guardado como evidencia del consentimiento. Ver `docs/operacion/datos-personales.md`.

## Publicar un texto nuevo

1. Agrega el archivo con la versión nueva, en español y en inglés. **No edites ni
   borres el anterior:** hay personas que aceptaron esa versión y su evidencia
   apunta a ese archivo.
2. Cambia la variable de entorno a la versión nueva, en el backend y en el
   frontend, con el mismo valor en los dos.

No hace falta desplegar código.

## Dónde va la política de devoluciones

Dentro de `terms`, no en un documento propio. Un documento nuevo exige su
variable de versión, su ruta y su entrada en `RUTAS_LEGALES`, y hoy no hay nada
que lo justifique: el derecho de retracto y el reintegro por producto no conforme
(RN-050 a RN-058) son dos secciones de los términos.

Si más adelante se separa, se agrega `returns` con su `LEGAL_RETURNS_VERSION`
siguiendo el mismo mecanismo. La decisión está anotada en
`docs/producto/alcance.md`.

Lo que sí es obligatorio: **la redacción del retracto la revisa un abogado antes
de publicarse**, y ninguna página informativa enuncia sus plazos por su cuenta.
Los plazos concretos viven aquí, en un solo sitio versionado, y las páginas
enlazan (RN-057).

## Estado actual

**Publicada la versión `2026-09-08b` el 8 de septiembre de 2026**, en términos y en
política de tratamiento de datos, en los dos idiomas. Recoge el cambio de modelo de envío:
precio base y costo de envío como cifras separadas, con el flete a cargo del comprador, y
la cotización con un agregador en vez de con tres transportadoras (ADR-0034).

Se escribió y se publicó el mismo día, pero **no en el mismo acto**: los archivos entraron
con el commit de la introducción de Skydropx y `LEGAL_TERMS_VERSION` y
`LEGAL_PRIVACY_VERSION` de `dev` se movieron a `2026-09-08b` después, que es el paso 2 de
más arriba y el que de verdad publica. Comprobado contra el entorno, no contra este
documento. El detalle de lo verificado y de lo que quedó abierto está en
`entrega-textos-legales-2026-09-08b.md`.

**La letra en el nombre de versión es deliberada.** La convención es la fecha y el 8 de
septiembre ya tenía versión publicada, así que una segunda del mismo día no puede
llamarse igual sin sobrescribir archivos a los que apunta la evidencia de un
consentimiento. El formato `<documento>.<version>.<idioma>.html` admite el sufijo sin
cambiar nada.

**Publicada la versión `2026-09-08` el 8 de septiembre de 2026**, en términos y en
política de tratamiento de datos. **La política de cookies se queda en `2026-09-06`**:
no tenía ningún vacío y su variable es independiente.

La versión `2026-09-08` **no deja un solo marcador `[[ ]]` en ninguno de los dos
idiomas**, que es la diferencia con todas las anteriores. De los seis vacíos que
arrastraba la `2026-09-06`, tres se cerraron yendo al texto de la norma, uno se cerró
reescribiendo la cláusula, y los dos que siguen abiertos **salieron del documento
publicado**: eran notas dirigidas a un abogado y las leía cualquier usuario. Viven
ahora en `entrega-textos-legales-2026-09-08.md`, que es donde se registra qué se
verificó, contra qué fuente y qué falta.

**La versión `2026-09-06` corrigió antes** los datos del negocio que estaban en `[[ ]]`
y quedaron a la vista del público durante unas horas —teléfono, horario, plazo de
respuesta, preaviso, cobertura, pasarela, transportadoras, proveedor de base de datos y
los plazos de conservación—. Lo verificado entonces está en
`entrega-textos-legales-2026-09-05.md`.

**Aviso de coherencia.** Entre el 6 y el 8 de septiembre las variables
`LEGAL_*_VERSION` de `dev` siguieron apuntando a `2026-09-05`, de modo que la versión
`2026-09-06` estuvo en el repositorio sin que la viera nadie. Publicar un archivo no es
publicarlo: **el paso 2 de este documento —mover la variable— es el que publica**.

La versión `2026-09-05` y los `borrador-local` **siguen en el repositorio y no se
tocan**: hay consentimientos cuya evidencia apunta a ellos.

Con la versión nueva **desaparece el aviso de «sin valor legal»**, porque
`esBorrador()` compara exactamente contra `borrador-local`. Es la consecuencia
buscada de publicar, y conviene tenerla presente: **los textos no los ha revisado un
abogado colegiado.** Lo que queda por resolver antes de que eso deje de ser un riesgo
está en `docs/operacion/entrega-textos-legales-2026-09-05.md`, y son cinco puntos de
criterio profesional más los campos `[[ ]]` que siguen sin dato —nombre completo,
teléfono, pasarela, transportadoras y plazos de operación—.

Resueltos ya: el NIT (`1054994043-9`, calculado con el algoritmo de la DIAN sobre la
cédula), la figura jurídica (persona natural, lo que además cierra la duda del
Registro Nacional de Bases de Datos: no obliga) y el desfase entre la ventana del
Respaldo y el retracto, que fijó RN-075.

Falta todavía el **aviso de privacidad** y el **texto de las casillas de
autorización**, que son documentos aparte y no se han escrito.

## Formato

HTML suelto, sin `<html>` ni `<body>`: se inserta dentro de la página, que ya
pone el título y la versión. Usa `h2`, `h3`, `p`, `ul`, `ol` y `a`; la
maquetación la aporta `legal-page.css`.

El contenido pasa por el desinfectante de Angular, así que cualquier `script` o
atributo ejecutable se descarta al mostrarlo.
