# Entrega — Auditoría del aviso de privacidad y de las casillas

**Fecha:** 8 de septiembre de 2026
**Qué se auditó:** el componente `sendik-privacy-notice` en sus dos variantes —`cuenta`
y `verificacion`— y el texto de las dos casillas del registro, en español y en inglés.
**Método:** skill `cerrar-vacios-legales`.
**Origen:** el aviso y las casillas se escribieron el 5 de septiembre de 2026, **antes de
que la skill existiera**, y son lo único de la tanda legal que nunca pasó por el barrido.

> **No son textos que falten: son textos que nadie había revisado.** Hasta hoy,
> `textos-legales.md` y `alcance.md` decían que el aviso y las casillas estaban por
> escribir. Llevaban tres días hechos. Esas dos líneas se corrigen con esta entrega.

## Fuente

Decreto 1377 de 2013, que reglamenta la Ley 1581 de 2012, leído en el Régimen Legal de
Bogotá —`alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=53646`—, en HTML crudo y
decodificando de ISO-8859-1, no a través de ninguna herramienta que resuma. Consultado el
**8 de septiembre de 2026**.

---

## Fase 1 · Inventario

### Barrido directo: contra el contenido mínimo del artículo 15

El artículo 15 enumera cuatro numerales y añade un párrafo para datos sensibles. Cada
línea se comprobó en los dos idiomas y en las dos variantes.

| Lo que exige el artículo 15 | ¿Estaba? |
|---|---|
| 1. Nombre o razón social **y datos de contacto** del responsable | **A medias.** Nombre y NIT sí; de contacto, solo el correo, y dentro de la línea de derechos. La ciudad iba escrita a mano en el archivo de traducción |
| 2. El tratamiento y su finalidad | Sí, y distinto en cada variante |
| 3. Los derechos que le asisten al titular | Sí, en versión corta —conocer, actualizar, rectificar, suprimir y retirar la autorización— con enlace a la política, que sí trae la lista completa del artículo 8 de la Ley 1581 |
| 4. Los mecanismos para conocer la política y sus cambios sustanciales | Sí. El enlace cubre el «en todos los casos» del numeral, y el numeral 16 de la política publicada dice cómo se comunican los cambios |
| Párrafo final: con datos sensibles, señalar **expresamente el carácter facultativo** | Sí, y es lo mejor resuelto del aviso: la variante `verificacion` abre con «No estás obligado a entregar estos datos» |

### Barrido inverso: afirmaciones sin respaldo

| Afirmación del aviso | ¿Qué la respalda? |
|---|---|
| «No los vendemos ni los cedemos con fines publicitarios» | La política, numeral 10: «Sendik no vende ni cede sus datos personales a terceros con fines comerciales o publicitarios» |
| «No se muestran a otras personas ni se usan para nada más» | RN-046 y la política. Comprobado además contra `dev` el 5 de septiembre: ninguna lectura devuelve más de los cuatro últimos dígitos |
| «Medellín, Colombia» | `COMPANY_ADDRESS`, que existe en configuración y **no era de donde salía** |
| «sin saber quién vende el respaldo no significaría nada» | Es la misma frase de la política, numeral 6. En inglés decía otra cosa |

---

## Fase 2 · Lo que se cerró

### 1 · La dirección del responsable — Clase 1

**Dónde:** `legal.notice.controller`, en los dos idiomas, y `privacy-notice.html`.

El artículo 15, numeral 1, pide «nombre o razón social **y datos de contacto** del
responsable del tratamiento». El aviso daba nombre y NIT, y como dato de ubicación una
ciudad —«Medellín, Colombia»— **escrita dentro de la cadena de traducción**, mientras
`COMPANY_ADDRESS` estaba configurada con la dirección real y la página «Sobre Sendik» sí
la usaba.

Eran dos fallos en una línea:

- **Legal.** La dirección del responsable es un dato de contacto y no estaba.
- **Del proyecto.** Un valor de negocio quemado en el código, que `CLAUDE.md` prohíbe y
  que el propio comentario del componente decía no hacer: «el responsable, su
  identificación y el canal salen de la configuración y nunca del texto». La dirección se
  había quedado fuera de esa lista.

Y con una consecuencia práctica: el día que la empresa cambie de sede, el aviso miente en
dos idiomas y nadie se entera, porque cambiar `COMPANY_ADDRESS` no habría tocado nada.

**Cerrado.** La dirección sale de la configuración, en frase aparte para que pueda faltar
sin dejar una coma colgando, y con prueba de las dos ramas: que se toma de la
configuración —comprobado con una dirección distinta de la de la configuración de prueba,
que es lo que detectaría una recaída— y que la frase desaparece entera si no hay dirección.

### 2 · «the guarantee» en inglés — glosario, no norma

**Dónde:** `legal.notice.sensitive`, solo en `en.json`.

La versión inglesa del aviso llamaba **`the guarantee`** al Respaldo. El glosario lo
prohíbe expresamente —«Garantía, seguro, para nombrar lo que ofrece Sendik → Respaldo»— y
desde RN-067 la colisión es peor que una cuestión de estilo: la ficha de un dispositivo ya
dice `Manufacturer warranty` para lo que responde **el vendedor**. En inglés la misma
pantalla acabaría usando dos casi sinónimos para las dos cosas que RN-067 existe para no
mezclar.

**Cerrado:** `Sendik's backing`, que es la columna de código del glosario y se entiende sin
nota al pie.

---

## Fase 3 · Lo que sigue abierto

### A · Conservar el modelo del aviso — Clase 1, cerrado por regla

El **artículo 16** del mismo decreto:

> Los Responsables deberán conservar el modelo del Aviso de Privacidad que utilicen para
> cumplir con el deber que tienen de dar a conocer a los Titulares la existencia de
> políticas del tratamiento de la información y la forma de acceder a las mismas, mientras
> se traten datos personales conforme al mismo y perduren las obligaciones que de este se
> deriven.

El aviso vive en `es.json` y `en.json` y **no se versiona**. El modelo, en rigor, sí queda
conservado: el repositorio guarda cada redacción que existió. Lo que no había era el
vínculo entre esa historia y el consentimiento de una persona concreta, porque la
evidencia apunta a la versión de la **política**, no a la del aviso.

`datos-personales.md` ya tenía escrita esa limitación y su mitigación —si el texto cambia
de fondo, se publica versión nueva del documento aunque el documento no cambie— pero
**solo para las casillas**, y el aviso es la pieza que carga las finalidades. La regla se
extiende al aviso y con eso la cadena vuelve a cerrar.

**Lo que queda por decidir, y no es una obligación:** versionar el aviso como un documento
más, con su propia variable. Sería más fuerte que la regla. Hoy no hace falta.

### B · El consentimiento expreso para los datos sensibles — Clase 2, y pide código

**Es el hallazgo de esta auditoría.** El artículo 6 del decreto, numeral 2:

> Informar al titular de forma explícita y previa, además de los requisitos generales de la
> autorización para la recolección de cualquier tipo de dato personal, cuáles de los datos
> que serán objeto de Tratamiento son sensibles y la finalidad del Tratamiento, **así como
> obtener su consentimiento expreso**.

**Lo que Sendik hace hoy.** La pantalla de verificación muestra el aviso en su variante
sensible —que cumple el numeral 1 del mismo artículo, decir que no está obligado— y a
continuación un botón «Empezar». Informar está resuelto. **Obtener no.** No hay casilla, y
la tabla `consents` solo admite `TERMS` y `PRIVACY` por restricción de `V2__identity.sql`,
así que tampoco habría dónde guardar la prueba que exige el artículo 8.

**Por qué el botón no basta.** El artículo 7 admite la autorización por «conductas
inequívocas», y pulsar «Empezar» podría serlo para un dato corriente. Pero el artículo 6
no dice conducta inequívoca: dice **consentimiento expreso**, y son dos varas distintas en
el mismo decreto. Un botón que dice «Empezar» no expresa consentimiento sobre nada.

**Y el alcance lo fija la propia política publicada**, que es más amplia que la ley: trata
como sensibles «el número de documento de identidad, su imagen y la fotografía del rostro».
La Ley 1581 solo obligaría por el dato biométrico —la selfie—; lo demás es una promesa que
Sendik ya hizo por escrito y que por tanto le es exigible.

**Qué haría falta:** una casilla propia antes de empezar la verificación, un valor nuevo
de `ConsentDocument`, la migración que amplíe la restricción y la evidencia guardada como
la de los otros dos. Es trabajo de HU-002 y **no se hace aquí**: excede el alcance de una
auditoría de textos y toca dominio, esquema e interfaz.

### C · «Ninguna actividad podrá condicionarse» — Clase 3, para el abogado

El mismo artículo 6 cierra así:

> Ninguna actividad podrá condicionarse a que el Titular suministre datos personales
> sensibles.

Vender en Sendik está condicionado a entregar la selfie, que es el dato biométrico. El
aviso lo dice sin rodeos —«solo hacen falta si decides vender»— y la lectura que sostiene
el producto es que la actividad condicionada no es usar la plataforma, sino vender en ella:
se puede tener cuenta, navegar y comprar sin entregar nada sensible.

Es una lectura razonable y **no es una que yo pueda cerrar**. Va junto a los otros dos
puntos de criterio profesional que ya esperan —el alcance de la responsabilidad como portal
de contacto y el reparto del numeral 15.2—, porque los tres preguntan lo mismo: hasta dónde
llega la posición que Sendik dice ocupar.

### D · La misma frase en inglés sigue publicada — decisión de versión

`privacy.2026-09-08b.en.html` dice también `the guarantee`, en el numeral 6. Corregirlo es
editar un documento **publicado y consentido**, así que no se toca: se publica una versión
nueva o se espera a la siguiente tanda. Es una decisión de oportunidad, no de redacción, y
la traducción inglesa lleva su propia advertencia de que en caso de discrepancia prevalece
el español.

---

## Lo que no cambia

- **Las casillas del registro salen bien de la auditoría** y no se tocaron. La de datos
  personales dice «Autorizo de forma libre, previa y expresa», que es la fórmula del
  artículo 9 de la Ley 1581, y remite a las finalidades «descritas arriba», que es el aviso
  que tiene encima. Siguen siendo dos y separadas.
- **Sigue sin haber tercera casilla de comunicaciones comerciales**, por la razón de
  siempre: sin mecanismo de baja no puede enviarse publicidad.
- **Los textos siguen sin revisión de abogado colegiado.** Esta auditoría no cambia eso;
  reduce lo que hay que preguntarle al punto C.
