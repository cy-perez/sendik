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

**Publicada la versión `2026-09-05` el 5 de septiembre de 2026.** Las tres variables
apuntan a ella en `dev`, en los seis archivos y en los dos idiomas. Los
`borrador-local` **siguen en el repositorio y no se tocan**: hay consentimientos cuya
evidencia apunta a ellos.

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
