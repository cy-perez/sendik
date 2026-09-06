# Contratos comerciales

Los contratos entre empresas o con clientes profesionales **no son relaciones de
consumo**. Se rigen por el Código de Comercio y, en lo no previsto, por el
Código Civil. Eso cambia todo: hay mucha más libertad contractual, y cláusulas
que serían abusivas frente a un consumidor aquí son válidas y hasta
recomendables.

**Antes de redactar, determina en qué régimen estás.** Si una de las partes es
un consumidor final, aplica el Estatuto del Consumidor y buena parte de este
archivo no sirve.

## Contenido

1. [Esqueleto común](#esqueleto-comun)
2. [Prestación de servicios](#prestacion-de-servicios)
3. [Desarrollo de software](#desarrollo-de-software)
4. [Acuerdo de confidencialidad](#acuerdo-de-confidencialidad)
5. [Suministro y proveedores](#suministro-y-proveedores)
6. [Distribución y alianzas](#distribucion-y-alianzas)
7. [Tratamiento de datos entre empresas](#tratamiento-de-datos-entre-empresas)
8. [Banco de cláusulas](#banco-de-clausulas)

---

## Esqueleto común

Casi todo contrato comercial sigue esta estructura. Cámbiala cuando haya razón,
no por variar.

1. **Encabezado y partes.** Nombre, identificación, domicilio y representante
   legal de cada una, con la calidad en que actúa.
2. **Consideraciones.** Breves. Para qué se contrata y qué contexto importa.
   Sirven para interpretar el contrato; no son decorativas, pero tampoco un
   ensayo.
3. **Objeto.** Una frase. Qué se obliga a hacer cada parte.
4. **Alcance y entregables.** Lo que de verdad evita los pleitos. Qué está
   incluido, qué no, en qué formato y con qué criterio se da por recibido.
5. **Plazo.** Duración, inicio, prórrogas y si son automáticas.
6. **Precio y forma de pago.** Valor, impuestos, hitos, plazos de pago,
   consecuencias de la mora, ajustes por inflación en contratos largos.
7. **Obligaciones de cada parte.** En listas separadas.
8. **Confidencialidad.** Cláusula o contrato aparte.
9. **Propiedad intelectual.** Quién queda con qué. Ver más abajo.
10. **Protección de datos.** Si una parte trata datos por cuenta de la otra.
11. **Responsabilidad.** Límites, exclusiones, tope indemnizatorio.
12. **Terminación.** Causales, preaviso, efectos, qué pasa con lo entregado y lo
    pagado.
13. **Cláusula penal**, si aplica.
14. **Independencia de las partes.** Que no hay relación laboral ni sociedad.
15. **Cesión.** Si se puede ceder y con qué autorización.
16. **Notificaciones.** Direcciones y correos válidos.
17. **Solución de controversias.** Negociación directa, conciliación, y
    jurisdicción o arbitraje. El arbitraje es caro: no lo pongas por defecto en
    contratos pequeños.
18. **Ley aplicable, integridad del acuerdo, firmas.**

---

## Prestación de servicios

El riesgo central es doble: **la ambigüedad del alcance** y **la
laboralización**.

Sobre el alcance: define entregables, criterios de aceptación, plazo para
observaciones y qué ocurre si el cliente no responde. Sin eso, "el proyecto
nunca termina" es el final previsible.

Sobre lo laboral: un contrato de prestación de servicios que en la práctica
funciona con horario, subordinación y herramientas del contratante puede ser
declarado laboral, sin importar cómo se titule. La cláusula de independencia
ayuda, pero no salva una relación que en los hechos es laboral. **Punto para
revisión de abogado** cuando el servicio sea continuo, exclusivo y con horario.

Incluye también: alcance de las revisiones incluidas y precio de las
adicionales, propiedad de los entregables condicionada al pago total, y
causales de suspensión por mora.

---

## Desarrollo de software

Además del esqueleto de servicios:

- **Titularidad de los derechos patrimoniales.** Debe pactarse expresamente:
  quién queda con el código, cuándo se transfiere (típicamente contra pago
  total) y en qué medio se formaliza. Sin pacto expreso, la regla supletiva
  puede no ser la que las partes suponen. **Punto para revisión de abogado.**
- **Componentes de terceros y licencias libres.** Declarar qué librerías se usan
  y bajo qué licencia. Una dependencia con licencia copyleft en un producto
  cerrado es un problema que aparece tarde y caro.
- **Herramientas y conocimiento previo del desarrollador.** Lo que ya existía
  antes del proyecto no se transfiere; conviene decirlo y delimitarlo.
- **Garantía de corrección de defectos.** Plazo y qué cuenta como defecto frente
  a lo que es una funcionalidad nueva.
- **Mantenimiento y soporte.** Preferiblemente contrato aparte, con niveles de
  servicio realistas.
- **Entorno, accesos y credenciales.** Quién los provee y qué pasa al terminar.
- **Datos de producción.** Si el desarrollador accede a datos reales, es
  encargado del tratamiento y hace falta el acuerdo correspondiente.

---

## Acuerdo de confidencialidad

Corto y preciso. Los largos suelen ser inaplicables.

- **Definición de información confidencial.** Concreta. Si todo es confidencial,
  nada lo es.
- **Excepciones.** Información pública, ya conocida, desarrollada de forma
  independiente, o de revelación obligatoria por ley u orden de autoridad.
- **Obligaciones.** Uso limitado al propósito, custodia, restricción de acceso a
  quien necesita conocer.
- **Vigencia.** Del acuerdo y de la obligación de confidencialidad después de
  terminar, que suelen ser distintas.
- **Devolución o destrucción** al terminar.
- **Unilateral o mutuo.** Decídelo antes de escribir; cambia toda la redacción.
- **Consecuencias del incumplimiento.** Aquí una cláusula penal tiene sentido,
  porque el daño es difícil de probar.

---

## Suministro y proveedores

- Especificaciones del producto y control de calidad.
- Plazos de entrega, lugar y transferencia del riesgo.
- Precios, vigencia y condiciones de reajuste.
- Pedidos mínimos, plazos de reposición y desabastecimiento.
- **Garantías del proveedor y coordinación con la garantía legal frente al
  consumidor final.** Esto se olvida siempre: quien vende responde ante el
  cliente, y necesita poder repetir contra su proveedor.
- Devoluciones y producto no conforme.
- Uso de marca y material publicitario.
- Exclusividad o no exclusividad, dicha expresamente.

---

## Distribución y alianzas

- Territorio y canal.
- Exclusividad, si la hay, con contraprestación y metas.
- Precios sugeridos — cuidado: **imponer precios de reventa puede ser una
  práctica restrictiva de la competencia**. **Punto para revisión de abogado.**
- Metas comerciales y consecuencias de no cumplirlas.
- Uso de marca, con licencia limitada y revocable.
- Terminación y preaviso. Terminar sin preaviso una distribución de años genera
  reclamaciones por la inversión del distribuidor.

---

## Tratamiento de datos entre empresas

Cuando una parte trata datos personales por cuenta de la otra, hace falta un
acuerdo que precise: quién es responsable y quién encargado, las finalidades
autorizadas, las medidas de seguridad, la prohibición de usar los datos para
fines propios, la subcontratación, el deber de asistir al responsable en las
peticiones de titulares, el reporte de incidentes con plazo, y la devolución o
supresión al terminar.

Si además hay transferencia internacional, el acuerdo debe abordarla. **Punto
para revisión de abogado.**

---

## Banco de cláusulas

Formulaciones frecuentes. **Adáptalas siempre**; una cláusula pegada sin
adaptar suele contradecir otra del mismo contrato.

**Independencia.** Que el contrato no genera relación laboral, sociedad ni
representación, y que cada parte asume sus obligaciones laborales y de
seguridad social respecto de su personal.

**Cláusula penal.** Valor o porcentaje, si es sancionatoria o compensatoria, y
si es compatible con la indemnización de perjuicios. Es una diferencia
importante y hay que definirla.

**Límite de responsabilidad.** Tope, normalmente referido al valor del contrato
o de lo pagado en un periodo. Exclusión de daños indirectos y lucro cesante.
Válida entre empresas; **frente a un consumidor, no**.

**Fuerza mayor.** Definición, deber de aviso, suspensión de obligaciones y
terminación si se prolonga más de un plazo.

**No competencia.** Debe estar limitada en tiempo, territorio y actividad para
tener alguna posibilidad de sostenerse. Las genéricas y perpetuas no se aplican.
**Punto para revisión de abogado.**

**Cesión.** Prohibida sin autorización previa y escrita, con las excepciones que
se pacten.

**Notificaciones.** Direcciones físicas y correos designados, y deber de
informar los cambios.

**Integridad del acuerdo.** Que el contrato reemplaza los acuerdos previos y que
las modificaciones deben constar por escrito.

**Firma electrónica.** Que las partes aceptan suscribirlo por medios
electrónicos y reconocen su validez conforme a la Ley 527 de 1999.
