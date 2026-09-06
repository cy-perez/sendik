# Redacción jurídica, cláusulas abusivas y evidencia

## Contenido

1. [Cómo se escribe una cláusula](#como-se-escribe-una-clausula)
2. [Cláusulas abusivas](#clausulas-abusivas)
3. [Aceptación electrónica y evidencia](#aceptacion-electronica-y-evidencia)
4. [Control de versiones](#control-de-versiones)
5. [Revisar un documento ajeno](#revisar-un-documento-ajeno)

---

## Cómo se escribe una cláusula

El lenguaje jurídico enrevesado no protege a nadie. En Colombia, además, la
ambigüedad se interpreta a favor del consumidor, así que escribir oscuro
perjudica a quien redacta. Y una cláusula que el cliente no entiende no cumple
el deber de información.

**Una obligación por cláusula.** Si una cláusula tiene tres deberes distintos,
son tres numerales. Así se pueden citar, discutir y modificar por separado.

**Sujeto, verbo, obligación.** "El Proveedor entregará el producto en el plazo
indicado en la confirmación del pedido" funciona. "Se procederá a la entrega del
producto en los términos previamente estipulados" no dice quién ni cuándo.

**Números y plazos siempre explícitos.** Cuenta en días hábiles o calendario y
dilo. "Días" a secas es una disputa futura. Indica también desde cuándo corre el
plazo: desde la entrega, desde la solicitud, desde el pago.

**Nada de "razonable", "oportuno", "a la mayor brevedad"** sin un número al
lado. Si el negocio no puede comprometerse a un número, ese es el problema real.

**Evita la doble negación y las remisiones en cadena.** Una cláusula que remite
a otra que remite a un anexo es una cláusula que nadie va a aplicar bien.

**Define solo lo que hace falta.** Las definiciones existen para no repetir o
para fijar un sentido particular, no para lucir técnico.

**Consistencia terminológica.** Si es "el Cliente", es siempre "el Cliente".
Alternar con "el usuario", "el comprador" y "el consumidor" crea dudas sobre si
son la misma persona.

**Español de Colombia y sin extranjerismos innecesarios.** "Reembolso", no
"refund". Si un anglicismo es el término de la industria, defínelo una vez.

---

## Cláusulas abusivas

En contratos de adhesión con consumidores, el Estatuto del Consumidor establece
cláusulas **ineficaces de pleno derecho**: no es que sean discutibles, es que no
producen efecto. Los arts. 42 y 43 de la Ley 1480 traen la regla general y la
enumeración; **verifica el listado literal vigente** antes de dar una respuesta
concluyente.

La regla general: es abusiva la cláusula que produce un **desequilibrio
injustificado** en perjuicio del consumidor o que afecta el tiempo, modo o lugar
en que puede ejercer sus derechos.

Familias típicas de cláusula que no se deben incluir:

- Limitar o exonerar la responsabilidad del productor o proveedor por los daños
  o por la garantía legal.
- Invertir la carga de la prueba en perjuicio del consumidor.
- Trasladar al consumidor cargas o costos que corresponden al proveedor.
- Permitir al proveedor modificar unilateralmente el contrato o el precio ya
  pactado, o terminarlo sin causa mientras el consumidor no puede.
- Restringir o condicionar el derecho de retracto o la garantía legal más allá
  de lo que permite la ley.
- Obligar al consumidor a renunciar a acciones, a acudir a foros que le
  dificulten reclamar, o a someterse a jurisdicción extranjera.
- Presumir manifestaciones de voluntad por el silencio del consumidor.
- Imponer la renovación automática sin posibilidad real y sencilla de cancelar.
- Permitir la cesión del contrato sin que el consumidor lo sepa, cuando eso lo
  perjudica.

**Ponerlas no es neutro.** Además de ser ineficaces, muestran mala fe y
deterioran la posición del negocio en toda la reclamación. Cuando el cliente
pida una cláusula de este tipo, explica por qué no la vas a incluir y propón la
alternativa legítima que sí protege el interés que hay detrás. Casi siempre la
hay: si lo que preocupa es el abuso de devoluciones, la vía no es negar el
retracto sino documentar bien las exclusiones legales y el estado del producto
al recibirlo.

---

## Aceptación electrónica y evidencia

La validez del contrato electrónico está resuelta por la Ley 527 de 1999. El
problema práctico no es la validez sino **la prueba**. Cuando llega la
reclamación, hay que poder demostrar tres cosas: qué texto se mostró, que el
usuario lo aceptó, y cuándo.

Recomendaciones de implementación que conviene entregar junto con el documento:

- **La aceptación debe ser una acción afirmativa**: una casilla que el usuario
  marca. No sirve la casilla premarcada ni el "al continuar navegando aceptas".
- **Casillas separadas** para términos y condiciones, autorización de
  tratamiento de datos y suscripción a comunicaciones comerciales.
- **El documento debe ser accesible antes de pagar**, no después. Un enlace
  visible en el checkout, no enterrado en el pie de página.
- **Guardar por cada aceptación**: identificador del usuario o del pedido,
  versión del documento aceptado, fecha y hora, y el mecanismo de aceptación.
- **Versionar los documentos** y conservar las versiones anteriores accesibles.
  Lo que importa en una disputa es qué decía el documento el día de la compra,
  no lo que dice hoy.
- **No modificar retroactivamente.** Las modificaciones rigen hacia el futuro y
  deben anunciarse.
- Si hay contratos firmados entre empresas, la firma electrónica es válida;
  para operaciones de mayor valor conviene evaluar firma digital certificada.
  **Punto para revisión de abogado.**

Cuando el sitio ya está construido, traduce esto a requisitos concretos de
producto: qué campos guardar, en qué tabla, qué debe renderizar el checkout.
Ese puente entre el documento y el sistema es la parte que un abogado
normalmente no entrega y que aquí sí se puede entregar.

---

## Control de versiones

Todo documento legal publicado lleva:

- **Versión** (por ejemplo, 1.2) y **fecha de entrada en vigor**, visibles en el
  documento, no en un comentario del código.
- **Registro de cambios** conservado internamente: qué cambió, cuándo y por qué.
- **Aviso de modificación** cuando el cambio afecta derechos de usuarios ya
  registrados.

Sin esto, la cláusula de "podemos modificar estos términos" no se puede aplicar.

---

## Revisar un documento ajeno

Cuando el encargo es revisar en lugar de redactar, el orden que rinde:

1. **Régimen.** ¿Es relación de consumo o entre empresas? Muchos documentos
   fallan aquí y el resto del análisis cambia según la respuesta.
2. **Ineficacias.** Busca cláusulas abusivas. Van primero porque son las que
   pueden hacer perder el caso completo.
3. **Faltantes.** Qué obligación legal no está cubierta. Suele ser más grave que
   lo que está mal escrito.
4. **Incoherencias internas.** Plazos que se contradicen entre secciones,
   términos definidos de dos maneras, remisiones a numerales que no existen.
5. **Incoherencias con la operación.** El texto contra lo que el negocio hace de
   verdad.
6. **Redacción.** Lo último. Es lo que más se nota y lo que menos importa.

Entrega el documento corregido **y** la lista de cambios con su razón. Clasifica
cada hallazgo como *crítico* (ineficaz o incumple la ley), *importante* (riesgo
real o vacío) o *mejora* (claridad). Sin esa jerarquía, el cliente arregla la
coma y deja la cláusula ineficaz.
