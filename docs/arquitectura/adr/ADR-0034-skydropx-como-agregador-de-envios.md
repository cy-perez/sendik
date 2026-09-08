# ADR-0034 — Skydropx Colombia como agregador de envíos

**Fecha:** 2026-09-08 · **Estado:** aceptada

## Contexto

La Fase 3 tiene que cotizar envíos, emitir guías y seguirlas. Hasta hoy el alcance
y RN-038 decían que el cotizador consultaría a **Envía, Coordinadora e
Interrapidísimo**, cada una por su cuenta, y que mostraría valores **aproximados**
rotulados como tales. Nada de eso se llegó a construir: la Fase 3 no ha empezado.

Dos cosas obligan a decidir ahora, antes de escribir la primera línea de ese
código.

**La primera es que el envío cambia de sitio en el precio.** Se decidió que el
producto tiene un **precio base** y que el **costo de envío lo paga el comprador**
como una cifra aparte. Eso convierte la cotización en un precio que alguien va a
pagar, y un precio que se cobra no puede ser aproximado: el artículo 26 de la Ley
1480 dice que el consumidor solo está obligado a pagar el precio anunciado. La
regla vieja quedó inservible en el momento de tomar esa decisión.

**La segunda es un descuadre que llevaba dos días publicado.** La política de
tratamiento de datos, en su versión `2026-09-06`, ya declaraba ante el público que
los pedidos los entrega «**Skydropx Colombia** y las transportadoras que habilite».
Ese nombre **no aparecía en ninguna otra parte del proyecto**: ni en las reglas de
negocio, ni en el alcance, ni en la configuración, ni en las entregas legales, que
en su momento anotaron el hueco como «Envía, Coordinadora, Interrapidísimo, las que
se contraten». Un marcador `[[TRANSPORTADORAS CONTRATADAS]]` se cerró con un nombre
que nadie escribió en ningún otro sitio.

Así que la decisión ya estaba tomada de hecho, en el único documento que el público
lee, y sin registro. Esta ADR es lo que la convierte en una decisión de verdad.

## Opciones

**Integración directa con cada transportadora.** Es lo que decía la regla anterior.
Da la relación comercial más corta y la tarifa sin intermediario. A cambio son tres
contratos, tres claves, tres formatos de respuesta y tres integraciones que
mantener, cada una con su propio modo de fallar, para una persona desarrollando.
Y no resuelve el seguimiento: son tres formatos más de eventos.

**Un agregador — Skydropx Colombia.** Una integración cubre cotización, emisión de
guía y seguimiento con todas las transportadoras que el agregador tenga habilitadas.
Añadir una transportadora deja de ser trabajo de Sendik. A cambio se acepta un
intermediario más en la cadena, una dependencia que es punto único de fallo, y un
margen sobre la tarifa.

**No cotizar: tarifa plana por zonas.** La más simple de todas y la que no depende
de nadie. Se descartó porque una tarifa plana o cobra de más en la mayoría de los
envíos o pierde dinero en los caros, y sin volumen no hay forma de calibrarla. Con
existencia 1 por publicación (RN-025) y prendas de tamaños muy distintos, el error
sería grande y constante.

## Decisión

**Skydropx Colombia como agregador**, detrás de un puerto del contexto `shipping`
definido en `application`, y **una sola integración** en lugar de una por
transportadora.

Con ella van dos decisiones que la acompañan y que no son técnicas:

- **El remitente es el vendedor** (RN-078). Sendik emite la guía por su cuenta, no
  en nombre propio.
- **Lo cotizado es lo que se cobra, y la diferencia con el costo real la asume
  Sendik** (RN-077).

## Motivo

La integración directa perdió por el mismo argumento que ya decidió ADR-0012 con el
correo y ADR-0005 con los pagos: **una persona no mantiene tres integraciones de lo
mismo**. Tres transportadoras son tres veces el trabajo de una para un resultado que
el comprador no distingue, porque lo que ve es una lista de opciones con precio y
plazo, y le da igual quién la compuso.

El seguimiento inclina la balanza todavía más que la cotización. De él cuelga la
**fecha de entrega**, que es el hecho del que dependen la ventana de reclamo
(RN-051) y la liberación del pago (RN-075) —o sea, dinero—. Normalizar tres flujos
de eventos distintos hasta poder confiar en una sola fecha es bastante más trabajo
que cotizar contra tres APIs, y es un trabajo donde equivocarse cuesta caro.

Que el **remitente sea el vendedor** no es una preferencia: es lo que mantiene en pie
el resto del edificio. Sendik es un portal de contacto en el sentido del numeral 18
del artículo 5 de la Ley 1480 —añadido por el artículo 6 de la Ley 2439 de 2024— y
todo el numeral 2 de los términos y condiciones se apoya en eso. Contratar el
transporte en nombre propio y cobrárselo al comprador convertiría a Sendik en
proveedor de un servicio ante el consumidor, con la responsabilidad que arrastra.
Emitir la guía por cuenta de otro no tiene ese efecto.

Que la **diferencia la asuma Sendik** sí fue una elección entre dos posibles. La otra
era descontarla del desembolso al vendedor, que es quien declara el peso y las
medidas y a quien el artículo 1010 del Código de Comercio le imputa la inexactitud
de esas indicaciones. Se prefirió lo primero porque la segunda deja al vendedor sin
saber cuánto va a cobrar hasta que alguien pese la caja, y quien no puede predecir
su ingreso no publica. Cobrarle la diferencia al comprador no estaba sobre la mesa:
el artículo 26 lo prohíbe.

## Consecuencias

**Lo que se gana**

- Una integración en vez de tres, y una sola forma de fallar que entender.
- Añadir o quitar transportadoras deja de ser trabajo de Sendik.
- Una sola fuente para la fecha de entrega, que es de donde cuelga el dinero.
- La política de datos deja de ser el único documento que sabe quién entrega.

**Lo que se acepta perder**

- **Un punto único de fallo.** Si el agregador no responde, no hay cotización, y
  por RN-040 no hay compra. Antes la regla prometía que la cotización nunca
  bloqueaba la compra; esa promesa se retira, porque las alternativas —dejar
  comprar sin precio, o inventarse uno— son peores.
- **Margen.** El agregador cobra por estar en medio, y ese sobrecosto lo paga el
  comprador dentro del flete.
- **La diferencia de cotización sale del bolsillo de Sendik.** RN-041 la guarda
  precisamente para poder medir cuánto cuesta esa promesa.
- **Un encargado más que trata datos personales**: nombre, dirección y teléfono del
  comprador viajan al agregador y a la transportadora que entregue.
- **Una dependencia de la que todavía no se sabe lo suficiente.** Bajo qué entidad
  contrata, desde qué país, qué figura asume en su contrato y a nombre de quién
  factura el flete son datos que solo el proveedor puede dar, y **ninguno está
  confirmado**. Están anotados como pendientes en
  `docs/operacion/entrega-textos-legales-2026-09-08b.md`.

## Cuándo revisar

- Si el agregador se cae con frecuencia suficiente para que RN-040 deje de vender
  de forma perceptible. La salida entonces no es volver a tres integraciones, sino
  una segunda fuente de cotización.
- Si la diferencia agregada que Sendik asume por RN-077 crece hasta pesar sobre la
  comisión del 5%. Lo que se reabre es RN-077, no esta ADR, y lo primero que hay
  que mirar es si el descuadre se concentra en unos pocos vendedores.
- Si el margen del agregador hace que el flete que ve el comprador sea
  sensiblemente peor que el de contratar directo, y ya hay volumen para negociar.
- Si alguna de las respuestas pendientes del proveedor obliga a cambiar la figura
  —por ejemplo, que su contrato no admita emitir guías por cuenta de un tercero—.
  Eso tocaría RN-078, que es lo que sostiene la posición de portal de contacto.
