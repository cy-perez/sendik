# Entrega — Textos legales de Sendik, versión `2026-09-05`

**Titular:** Cristhian Yurday Pérez Hoyos, persona natural · **NIT:** `1054994043-9`
**Versión de los documentos:** `2026-09-05`
**Estado:** **publicada en `dev` el 5 de septiembre de 2026**, por decisión expresa

> **Publicada sin revisión de abogado colegiado.** Es una decisión tomada a
> sabiendas, no un descuido, y queda anotada aquí porque cambia el riesgo: al dejar
> de ser `borrador-local` desaparece el aviso de «sin valor legal» que la página
> mostraba, de modo que el texto se presenta como vigente. Los cinco puntos de
> criterio profesional de más abajo siguen abiertos, y el numeral 14.1 —las
> excepciones al retracto— sigue vacío a propósito.

## Qué se entrega

Seis archivos nuevos en `frontend/public/legal/`, siguiendo el mecanismo de
`docs/operacion/textos-legales.md`. No se editó ni se borró ninguno de los
`borrador-local`, porque hay evidencia de consentimiento que apunta a ellos.

| Documento | Español | Inglés |
|---|---|---|
| Términos y condiciones | `terms.2026-09-05.es.html` | `terms.2026-09-05.en.html` |
| Política de tratamiento de datos | `privacy.2026-09-05.es.html` | `privacy.2026-09-05.en.html` |
| Política de cookies | `cookies.2026-09-05.es.html` | `cookies.2026-09-05.en.html` |

La política de devoluciones, retracto y garantías va **dentro de los términos**
—numerales 13 a 16—, como decide `textos-legales.md`. No se creó documento
aparte.

## Aviso de privacidad y casillas — hechos el 5 de septiembre de 2026

**El aviso de privacidad** es ahora un componente, `sendik-privacy-notice`, y no una
pagina: se entrega donde se pide el dato, que es lo que lo distingue de la politica.
Va en el registro, encima de las casillas —informar es previo a consentir— y antes de
empezar la verificacion de vendedor, con una variante propia que dice expresamente que
nadie esta obligado a entregar documento, rostro y cuenta bancaria.

**El texto de las dos casillas** se reescribio para que la autorizacion sea expresa e
informada, no una frase de tramite:

- «He leído y acepto los términos y condiciones.»
- «Autorizo de forma libre, previa y expresa el tratamiento de mis datos personales
  para las finalidades descritas arriba y en la política de tratamiento de datos.»

«Descritas arriba» apunta al aviso que ahora tiene encima, que es lo que convierte la
casilla en informada.

**Sigue sin haber tercera casilla de comunicaciones comerciales**, y es deliberado:
sin mecanismo de baja no puede enviarse publicidad, y recoger un consentimiento que no
se puede ejercer es peor que no recogerlo.

---

## Campos por completar

**Resueltos el 6 de septiembre de 2026 en la versión `2026-09-06`**, todos menos
`[[DÍAS HÁBILES DE DESEMBOLSO]]` y los cinco de criterio profesional. La tabla se deja
como estaba porque describe la versión `2026-09-05`, que sigue publicada y consentida.

De lo resuelto, tres valores no los puso el negocio sino la norma o el sistema, y por
eso se dejan anotados con su fuente:

| Campo | Valor | De dónde sale |
|---|---|---|
| Plazo de respuesta a PQR | 15 días hábiles | Artículo 58 de la Ley 1480 de 2011, reclamación directa |
| Conservación contable | 10 años | Artículo 28 de la Ley 962 de 2005, que derogó el término de 20 años del artículo 60 del Código de Comercio |
| Retención de registros técnicos | 30 días | **Lo que hace el sistema**: el bucket `_Default` de Cloud Logging en `sendik-col` tiene `retentionDays: 30`. No hay plazo legal que fijar aquí; lo que hay es un principio de minimización, y el documento dice lo que de verdad ocurre |

Los días de preaviso —quince— **no son un plazo legal**: no existe uno. El artículo 38
de la Ley 1480 prohíbe en los contratos de adhesión las cláusulas que permiten
modificar el contrato unilateralmente, así que lo que protege no es la cifra sino que
el cambio rija solo hacia adelante y que quien no lo acepte pueda cerrar su cuenta.
Ambas cosas ya estaban en el numeral 23.

Todo lo marcado con `[[ ]]` en los seis archivos. Ninguno se inventó.

| Marca | Qué falta | Dónde |
|---|---|---|
| `[[TELÉFONO DE ATENCIÓN]]` | Teléfono real y atendido | Términos 1 y 18, Privacidad 1 |
| `[[ESCALA O CRITERIO DE REPRESENTACIÓN]]` | Exigencia de la Ley 2439 sobre imágenes | Términos 8.1 |
| `[[CIUDADES O COBERTURA REAL]]` | Cobertura de envío | Términos 12 |
| `[[NOMBRE DE LA PASARELA DE PAGO]]` | Wompi, si se confirma | Términos 11, Privacidad 10 |
| `[[TRANSPORTADORAS CONTRATADAS]]` | Envía, Coordinadora, Interrapidísimo, las que se contraten | Privacidad 10 |
| `[[PROVEEDOR DE BASE DE DATOS]]` y `[[PAÍS]]` | Neon o Supabase, y dónde está alojada | Privacidad 10 |
| `[[DÍAS HÁBILES DE DESEMBOLSO]]` | Sigue sin decidirse, ver hallazgo 5 | Términos 17 |
| `[[TÉRMINO DE GARANTÍA PARA PRODUCTO USADO]]` | Ver punto de abogado 3 | Términos 15.1 |
| `[[HORARIO REAL DE ATENCIÓN]]`, `[[PLAZO REAL DE RESPUESTA A UNA PQR]]` | Deben ser los que se cumplen, no los deseados | Términos 18 |
| `[[AÑOS DE CONSERVACIÓN CONTABLE]]`, `[[PLAZO REAL DE RETENCIÓN DE REGISTROS]]` | Plazos reales | Privacidad 13 |
| `[[DÍAS DE PREAVISO]]` | Antelación para anunciar cambios | Términos 23 |
| `[[FECHA DE PUBLICACIÓN]]` | La misma en los seis archivos | Cierre de los tres documentos |
| `[[CONFIRMAR MECANISMO Y MOMENTO DE ENTREGA DE LA FACTURA]]` | Facturación electrónica de la comisión | Términos 17 |

---

## Hallazgos de coherencia

Lo que el documento promete y la operación o el sistema todavía no cumplen. Es
la parte que la skill llama Fase 4 y suele ser lo más valioso de la entrega.

### 1. El NIT no coincidía entre dos fuentes — **resuelto el 5 de septiembre de 2026**

`docs/operacion/datos-personales.md` decía **1054994043-1** y la variable
`COMPANY_TAX_ID` decía **1054994043-8**: mismo número base, dígito de verificación
distinto. **Los dos estaban mal.**

Aplicando el algoritmo de la DIAN —módulo 11 con pesos primos— sobre la cédula
`1054994043`, la suma es 827, el resto sobre 11 es 2 y el dígito es **9**. El
algoritmo se validó antes contra dos NIT públicos conocidos: Bancolombia
`890903938-8` y Ecopetrol `899999068-1`, los dos correctos.

**El NIT es `1054994043-9`**, y se corrigió en `datos-personales.md`, en
`configuracion.md`, en la variable de entorno y en los cuatro archivos legales que lo
llevan.

Queda confirmado además que Sendik opera como **persona natural**, lo que trae dos
consecuencias que ya están aplicadas: el campo pasó de «razón social» a «nombre
completo», y el numeral del Registro Nacional de Bases de Datos se cerró —una persona
natural no está obligada a inscribirlas—. Sigue pendiente el registro mercantil ante
la Cámara de Comercio y el RUT, que es lo que hace existir el NIT para facturar la
comisión.

### 2. Los documentos describen una operación que todavía no existe — **el más importante**

Los numerales 10 a 17 de los términos —compra, pago, envío, entrega, Respaldo,
retracto, garantías, comisión— describen la Fase 3, y la Fase 3 **no ha
empezado**. Hoy no hay carrito, ni pasarela, ni pedidos, ni transportadora, ni
liberación de pago.

Esto no es un error de redacción: es la advertencia central de la skill, que un
documento que describe la operación de otro negocio no protege sino que crea
obligaciones incumplibles. **Hay dos salidas y son excluyentes:**

- **A) Publicar ahora una versión reducida**, que cubra solo lo que existe
  —cuenta, verificación de vendedor, publicación, moderación, catálogo,
  favoritos— y dejar fuera los numerales 9 a 17. Permite abrir el registro con
  textos verdaderos, y obliga a una versión nueva cuando llegue la Fase 3.
- **B) Publicar el documento completo solo al lanzar la venta**, junto con la
  Fase 3, y mantener `borrador-local` hasta entonces.

**Decidido el 5 de septiembre de 2026: se publica el documento completo (opción B),
pero sin esperar a la Fase 3.** Es la combinación que no estaba en la lista y la que
más riesgo deja abierto: hoy el sitio anuncia como vigentes unos términos que
describen compra, pago y envío que todavía no existen. **Mientras la Fase 3 no exista,
nadie puede comprar**, así que ninguna de esas cláusulas llega a aplicarse a una
operación real; el riesgo es de publicidad —anunciar una operación que no se presta— y
no de incumplimiento contractual. Queda anotado como lo que hay que revisar el día que
se abra el registro al público.

### 3. La ventana del Respaldo vencía antes que el retracto — **resuelto: RN-075**

Es el hallazgo más concreto y afecta al código, no solo al texto.

- **RN-051 y RN-052:** la ventana de reclamo es de **3 días hábiles** desde la
  entrega, y al vencer **el pago se libera** al vendedor.
- **La ley da 5 días hábiles** desde la entrega para retractarse.
- **RN-054** dice que el reintegro sale de la retención, «nunca del bolsillo del
  vendedor».

**Los días 4 y 5 el comprador conserva su derecho legal de retracto, pero el
dinero ya se liberó.** En esos dos días la premisa de RN-054 deja de ser cierta y
Sendik tendría que reintegrar de su propio bolsillo o perseguir al vendedor.

Se agrava con el reembolso: la Ley 2439 obliga a devolver en un máximo de **15
días calendario** desde que se ejerce el retracto, y ese plazo cobija a todos los
intervinientes.

**Resuelto el 5 de septiembre de 2026 con RN-075**, que separa dos fechas que
estaban pegadas: la entrega se confirma al vencer la ventana de reclamo, pero **el
pago no se libera hasta que pasan los cinco días hábiles del retracto**. RN-052 se
reescribió para no liberar por su cuenta y RN-054 quedó anotada como dependiente de
esto. Los términos lo dicen en el numeral 13.1.

Consecuencia asumida: **el vendedor cobra dos días hábiles más tarde**. No hay forma
de bajarlo sin que alguien asuma el riesgo del retracto tardío, y con Sendik operando
como persona natural ese alguien sería su patrimonio personal.

### 4. Plazos legales que ninguna regla de negocio recoge

No están en `docs/producto/reglas-negocio.md` y el sistema tendrá que
cumplirlos:

- **Entrega en 30 días calendario** como máximo, salvo aceptación expresa y
  previa de otro plazo. Hoy no hay ninguna regla de plazo de entrega.
- **Reembolso del retracto en 15 días calendario.**
- **Consultas de datos en 10 días hábiles y reclamos en 15**, con sus prórrogas.
  `datos-personales.md` ya los recoge; no hay nada en el sistema que los mida.

### 5. Decisiones aplazadas que el documento necesita

`alcance.md` las tiene anotadas como aplazadas a Fase 3, pero los términos no
pueden publicarse sin ellas: los **días hábiles de desembolso** al vendedor y la
**facturación electrónica de la comisión ante la DIAN**.

### 6. Publicidad sin mecanismo: hoy no puede enviarse

El registro tiene dos casillas —términos y datos—, separadas y sin premarcar, que
es lo correcto. **No hay una tercera para comunicaciones comerciales, ni
mecanismo de baja.** Sin las dos cosas no puede enviarse un solo correo
promocional, por la Ley 1581 y por la Ley 2300 de 2023, que además limita
horarios —lunes a viernes de 7 a 19, sábados de 8 a 15, nunca domingos ni
festivos—. La política lo dice y lo deja marcado.

### 7. El aviso de borrador se apaga con cualquier nombre de versión

`frontend/src/app/features/legal/domain/legal-document.ts:54` define
`esBorrador(version)` como una **igualdad exacta** contra `'borrador-local'`. En
el momento en que las variables apunten a `2026-09-05`, el aviso de «sin valor
legal» desaparece, aunque el texto siga sin revisar por un abogado.

Es correcto **si y solo si** el cambio de variable ocurre después de la revisión
legal. Si se quiere publicar el borrador para leerlo en el sitio antes de esa
revisión, hay que ensanchar `esBorrador` a algo como un prefijo `borrador-`.

### 8. La frase de garantía del fabricante ya está redactada

RN-067 estaba aplazada «a la tanda legal» y bloqueaba la ficha de tecnología. El
numeral 15.2 de los términos la resuelve: quien responde por la garantía del
fabricante es el vendedor, no Sendik. **Falta llevar esa frase a la ficha de
producto**, que es trabajo de frontend y sigue pendiente.

---

## Puntos para revisión de abogado

**1. El Respaldo frente a la condición de portal de contacto — el más grave**

- **Qué está en juego:** la Ley 2439 de 2024 definió el «portal de contacto» y la
  definición le calza a Sendik con exactitud. Un portal de contacto tiene menos
  obligaciones que un proveedor. Pero Sendik **retiene el dinero, decide sobre
  reclamos y reintegra**, y `CLAUDE.md` dice que «actúa como respaldo de la
  transacción».
- **Opción A:** insistir en el papel de intermediario y presentar el Respaldo como
  una cortesía contractual. Menos exposición, pero se acerca a describir una
  operación distinta de la real, y la publicidad obliga.
- **Opción B:** asumir que ese respaldo genera obligaciones propias y redactarlo
  como compromiso exigible. Más exposición, pero es lo que el negocio hace.
- **Qué se dejó escrito:** la opción B, moderada. El numeral 2 enumera las cuatro
  cosas por las que Sendik responde, y el 21 dice que no limita ningún derecho
  del consumidor. **Debe validarse si esto convierte a Sendik en responsable
  solidario.**

**2. Las excepciones al derecho de retracto en un catálogo de moda**

- **Qué está en juego:** el artículo 47 excluye del retracto, entre otros, los
  «bienes de uso personal». Si esa categoría cubre la ropa, **el retracto no
  aplica a la mayor parte del catálogo de Sendik**; si no la cubre, aplica a casi
  todo. Es la diferencia entre dos negocios distintos.
- **Qué se dejó escrito:** el numeral 14.1 **en blanco**, con instrucción expresa
  de transcribir la lista literal del texto vigente y de resolver esta duda antes
  de publicar. No se enumeraron las excepciones de memoria: enumerarlas mal es,
  según la propia guía, uno de los errores más caros.

**3. Garantía legal de un producto de segunda mano**

- **Qué está en juego:** la garantía legal de un año se predica del producto
  nuevo. Sendik vende moda de segunda entre particulares, y el régimen de
  garantía para bien usado vendido por un no comerciante no es evidente.
- **Qué se dejó escrito:** `[[ ]]` en el numeral 15.1. **No se inventó un plazo.**

**4. Transmisión internacional de datos**

- **Qué está en juego:** toda la infraestructura está en Estados Unidos
  —`us-east1`— y hay datos **sensibles**: cédula, selfie y cuenta bancaria. El
  régimen distingue transferencia de transmisión y exige requisitos según el país
  de destino.
- **Qué se dejó escrito:** el numeral 11 lo declara con franqueza y marca el punto.
  Debe confirmarse si hacen falta cláusulas contractuales modelo o declaración
  ante la SIC.

**5. Registro Nacional de Bases de Datos**

- Depende de la naturaleza jurídica y los activos, que es el hallazgo 1. Marcado
  en el numeral 15 de la política.

---

## Notas de implementación

- **Dónde van:** ya está resuelto. Las tres rutas existen y el resolutor sirve el
  texto dentro del HTML renderizado en servidor.
- **Para publicar:** añadir los archivos —hecho— y cambiar
  `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION` y `LEGAL_COOKIES_VERSION` a
  `2026-09-05` **en el backend y en el frontend, con el mismo valor**. No hace
  falta desplegar código. **No se cambiaron**: eso es una decisión que va después
  de la revisión legal.
- **Cuándo debe verlo el usuario:** antes de crear la cuenta, con las dos casillas
  separadas que ya existen, y antes de pagar cuando llegue la Fase 3.
- **Evidencia:** ya se guarda versión, fecha, hora e IP. Es justo lo que hace que
  cambiar de versión sin borrar la anterior sea obligatorio.
- **Los `borrador-local` no se tocan.** Hay consentimientos que apuntan a ellos.

---

Este borrador lo redactó un agente a partir del código y la documentación reales
del repositorio, no de una plantilla. **No es asesoría jurídica y no debe
publicarse sin la revisión de un abogado colegiado**, en particular por los cinco
puntos de la sección anterior.
