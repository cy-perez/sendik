# Reglas de negocio

Fuente única de verdad para el comportamiento del sistema. Si una regla no está
aquí, no se implementa: se pregunta y se agrega.

Cada regla tiene identificador. Úsalo en el código y en las pruebas:
`// RN-012` y `deberia_cumplir_RN_012_...`.

## Cuentas y acceso

- **RN-001** Un correo electrónico corresponde a una sola cuenta.
- **RN-002** La cuenta no se activa hasta verificar el correo. Sin verificar solo
  se permite navegar; no publicar ni comprar.
- **RN-003** El enlace de verificación caduca a las 24 horas y es de un solo uso.
- **RN-004** El enlace de recuperación de contraseña caduca a los 30 minutos, es
  de un solo uso e invalida todas las sesiones abiertas al usarse.
- **RN-005** Contraseña de 10 caracteres como mínimo. Se rechaza si aparece en la
  lista de contraseñas filtradas conocidas. No se exigen símbolos obligatorios:
  la longitud protege más que la complejidad artificial.
- **RN-006** Cinco intentos fallidos de inicio de sesión bloquean el acceso a esa
  cuenta durante 15 minutos, contados desde el último intento.
- **RN-007** El token de acceso dura 15 minutos. El de refresco dura 30 días,
  rota en cada uso y se revoca toda la familia si se detecta reutilización. No
  cuenta como reutilización que un token vuelva a llegar dentro de los segundos
  siguientes a su rotación mientras el que salió de ella siga sin usarse: eso es
  una carrera entre dos pestañas del mismo navegador, que comparten la cookie. En
  ese caso se rechaza la petición pero no se revoca ni se avisa (ADR-0014), **y se
  responde con un código propio** para que el cliente sepa que su sesión sigue
  viva y no la cierre (ADR-0030). Sin ese código las dos situaciones eran
  indistinguibles desde fuera y una carrera echaba a la persona: la regla evitaba
  el aviso falso y la revocación, no el cierre de sesión.
- **RN-008** Solo mayores de 18 años. Se declara en el registro y se confirma en
  la verificación de identidad.
- **RN-009** El usuario puede cerrar su cuenta en cualquier momento. Si tiene
  pedidos en curso, el cierre queda pendiente hasta que se resuelvan.

## Vendedor

- **RN-010** Solo persona natural. Un documento de identidad, un vendedor.
- **RN-011** Para publicar hay que estar verificado: identidad, selfie y cuenta
  bancaria a nombre del mismo documento.
- **RN-012** La cuenta bancaria debe pertenecer al titular de la cédula
  registrada. Si no coincide, la verificación se rechaza.
- **RN-013** El sello de vendedor verificado se pierde si la verificación se
  revoca; sus publicaciones activas siguen visibles pero no puede crear nuevas.
- **RN-014** Un vendedor rechazado puede reintentar. Máximo tres intentos; el
  cuarto exige revisión manual.
- **RN-059** Transiciones válidas de la verificación del vendedor, sobre los seis
  estados del glosario:

  | Desde | Hacia | Quién lo provoca |
  |---|---|---|
  | `NOT_STARTED` | `IN_PROGRESS` | La persona inicia el proceso |
  | `IN_PROGRESS` | `IN_PROGRESS` | Completa o corrige un dato. Se guarda el avance y se retoma donde iba |
  | `IN_PROGRESS` | `PENDING_REVIEW` | Envía la solicitud completa |
  | `PENDING_REVIEW` | `VERIFIED` | El moderador aprueba |
  | `PENDING_REVIEW` | `REJECTED` | El moderador rechaza con motivo |
  | `REJECTED` | `IN_PROGRESS` | La persona corrige y reintenta, dentro del límite de RN-014 |
  | `VERIFIED` | `REVOKED` | El moderador revoca (RN-013) |
  | `REVOKED` | `IN_PROGRESS` | La persona vuelve a intentarlo |

  Ninguna otra transición existe. En particular: de `PENDING_REVIEW` no se sale
  hacia atrás por voluntad de la persona —una solicitud enviada se revisa, porque
  si no habría forma de retirar una cédula ya vista— y a `NOT_STARTED` no se vuelve
  nunca, porque el estado inicial es la ausencia de intentos y esa ya no es cierta.

  Como en RN-045, ninguna transición se pierde: cada una queda registrada con
  fecha, actor y motivo.

- **RN-060** Un moderador no puede aprobar ni rechazar su propia solicitud de
  verificación. Quien revisa y quien es revisado tienen que ser dos personas.
  La comprobación es del servidor: esconder el botón no es la regla, porque los
  endpoints de revisión se pueden llamar sin pasar por la interfaz.

  No es una sospecha sobre nadie: es que el sello de verificado es lo que
  responde por una transacción ante quien compra, y un sello que alguien puede
  otorgarse a sí mismo no responde por nada.

  Un moderador sí puede verificarse como vendedor —RN-010 no lo prohíbe—; lo
  que no puede es ser quien decida sobre su propia solicitud. La resuelve otro.

- **RN-069** El sello se revoca por uno de estos cinco motivos, y por ninguno más.
  Lista cerrada, por lo mismo que la del rechazo: el motivo se traduce, se le muestra
  a la persona y se puede medir cuál se usa más.

  | Motivo | Código | Cuándo se usa |
  |---|---|---|
  | El documento no corresponde a quien lo presentó | `DOCUMENT_NOT_ITS_HOLDER` | Se comprueba, después de otorgado el sello, que la persona del documento no es la de la cuenta |
  | La cuenta bancaria no es del titular | `BANK_ACCOUNT_NOT_HOLDER` | RN-012, detectado después de aprobar |
  | Publicó lo prohibido de forma reiterada | `REPEATED_PROHIBITED_LISTINGS` | RN-024, y más de una vez: una sola publicación se baja, no cuesta el sello |
  | Lo pidió la propia persona | `HOLDER_REQUEST` | Deja de vender por voluntad propia. No es cerrar la cuenta, que es RN-009 y se hace sin pedirle permiso a nadie |
  | Ya no cumple los requisitos | `REQUIREMENTS_NO_LONGER_MET` | Último recurso, cuando lo ocurrido no es ninguno de los cuatro anteriores |

  **No es la lista del rechazo y no se mezclan**, igual que no se mezclan la de
  publicación y la de verificación (glosario). Rechazar juzga una solicitud que aún
  no se aprobó; revocar se lo quita a alguien que ya vende. Reutilizar `RejectionReason`
  aquí significa decirle «fotos ilegibles» a quien pierde el sello por otra cosa.

  **Los motivos describen hechos, no delitos.** Ninguno dice «fraude» ni
  «suplantación», y no es un eufemismo. Es la misma decisión que ya tomó el motivo
  genérico de rechazo en HU-002: Sendik no consulta ninguna fuente judicial,
  `docs/operacion/datos-personales.md` no tiene categoría para una calificación así, y
  el motivo se guarda y viaja en un correo a la persona. Decir lo que se comprobó es
  exacto y se puede sostener; nombrar el delito es una acusación que este sistema no
  está en condiciones de hacer.

  `REQUIREMENTS_NO_LONGER_MET` es el último recurso y no el primero. Si se convierte en
  el motivo de la mayoría de las revocaciones, lo que falta es un motivo, no una nota:
  se agrega a esta lista.

  El motivo es obligatorio y la nota es opcional. La nota se rige por lo mismo que en
  el rechazo: viaja a la persona y nunca lleva información judicial ni datos de un
  tercero.

  Revocar **no baja las publicaciones** de esa persona. Lo dice RN-013 y se le advierte
  a quien revoca antes de confirmar: lo que estaba visible sigue visible, y retirarlo es
  otra decisión, una por una, con los motivos de RN-024.

  Como en RN-059, la revocación queda registrada con fecha, actor, motivo y nota.

## Publicación

- **RN-015** Toda publicación pasa por moderación antes de ser visible. Sin
  excepciones ni listas blancas.
- **RN-016** Se exigen cuatro tomas canónicas: frontal, lateral derecha, lateral
  izquierda y posterior. Sin las cuatro no se puede enviar a revisión. En
  tecnología declarada sellada son las cuatro del empaque (RN-065).
- **RN-017** Para la secuencia 360 se capturan **ocho** tomas a 45 grados. Las
  cuatro canónicas son las de 0, 90, 180 y 270 grados: se extraen de la misma
  secuencia, no se toman aparte. **Única excepción:** la tecnología declarada
  sellada, que se queda en las cuatro canónicas y no ofrece visor (RN-065).
- **RN-018** Todas las tomas se recortan a proporción 3:4 en el cliente antes de
  subirlas, con el producto centrado. Es la proporción del catálogo y no es
  negociable: si cada foto llega con la suya, la rejilla se rompe.
- **RN-019** Resolución mínima de 900 x 1200 px por toma. Por debajo, el
  formulario no deja continuar.
- **RN-020** El precio se expresa en pesos colombianos, sin decimales, mínimo
  10.000 y máximo 20.000.000. Fuera de ese rango exige revisión manual, que es
  distinto de estar prohibido: el formulario **no bloquea** —cuando una regla
  quiere bloquear lo dice con todas sus letras, como hace RN-019— sino que la
  publicación queda marcada para revisión más atenta y el moderador la ve
  destacada. La aclaración se decidió el 24 de agosto de 2026, al escribir
  HU-007, porque la frase sola admitía las dos lecturas.
- **RN-021** El vendedor declara condición, talla y medidas reales en
  centímetros. Las medidas son obligatorias: son la causa número uno de
  devolución en moda de segunda mano. Qué talla se pide lo decide la categoría:
  la tecnología usa talla única, porque un televisor no tiene talla, y sus
  medidas son las del aparato.
- **RN-022** Una publicación rechazada indica siempre el motivo y qué corregir.
  Se puede editar y reenviar a revisión.
- **RN-023** Una publicación vendida no se edita ni se reactiva.
- **RN-024** Publicaciones prohibidas: réplicas o falsificaciones, ropa interior
  usada, productos que no sean moda ni tecnología, prendas con daño no declarado,
  y cualquier producto de tecnología que no sea nuevo (RN-064).
- **RN-025** Existencia siempre igual a 1. Un producto, una publicación.
- **RN-064** Sendik vende dos cosas y con distinta condición admisible:

  | Familia | Condiciones admisibles |
  |---|---|
  | Moda | Nuevo, como nuevo, buen estado, con detalles |
  | Tecnología | **Solo nuevo** |

  Lo usado se vende únicamente en moda. Un celular, un televisor o un computador
  de segunda no se publican en Sendik, y la comprobación no es de la interfaz: el
  dominio rechaza cualquier condición distinta de nueva en una categoría de
  tecnología.

  La razón es que las dos ventas no se parecen. En moda, el desgaste se ve en la
  foto y se declara con medidas: el comprador juzga mirando. En tecnología, lo
  que falla no se fotografía —la batería, el sensor, la pantalla que se apaga a
  los dos meses— y ninguna toma a 45 grados lo muestra. Sin capacidad de probar
  el aparato, un catálogo de tecnología de segunda es un catálogo de disputas, y
  el respaldo que promete Sendik no lo puede sostener.

- **RN-065** Un producto de tecnología puede declararse **sellado**, y esa
  declaración es lo único que habilita las imágenes de referencia (RN-066). Una
  publicación de tecnología sellada exige las cuatro tomas canónicas **del
  empaque tal como está en poder del vendedor**, y no las ocho de RN-017 ni el
  visor giratorio: no hay nada que girar y ocho fotos de una caja no le dicen
  nada a nadie.

  Recibir abierto lo que se declaró sellado es producto no conforme (RN-050).

  Un producto de tecnología que no se declara sellado se fotografía como
  cualquier otro: ocho tomas y sin imágenes de referencia.

- **RN-066** Las imágenes de referencia son fotos del producto que no tomó el
  vendedor, y se rigen por tres límites que no se negocian:

  1. **Solo en tecnología declarada sellada.** En moda no existen. El sitio
     promete que lo que se ve es la pieza exacta que se recibe, y una foto de
     catálogo en una prenda de segunda convierte esa frase en publicidad
     engañosa.
  2. **Nunca sustituyen a las tomas reales.** Se suman a las cuatro del empaque,
     y una publicación hecha solo de imágenes de referencia no se puede enviar a
     revisión. Sin una foto real no hay prueba de que el producto exista.
  3. **Siempre rotuladas como referencia**, en la ficha y en el carrusel, en los
     dos idiomas. Un comprador tiene que poder distinguir de un vistazo qué foto
     tomó el vendedor y cuál es del fabricante.

- **RN-067** Si un producto de tecnología trae garantía del fabricante, el
  vendedor declara que la trae y por cuántos meses. **Quien responde por esa
  garantía es el vendedor**, no Sendik, y así se dice en la ficha.

  Sendik ofrece el Respaldo y nada más: el pago retenido y la ventana de reclamo
  de RN-050 a RN-058. La garantía legal que la Ley 1480 de 2011 fija para un
  producto nuevo existe además de eso y no la sustituye ninguna regla de aquí.

  **La ficha ya lo enuncia desde el 8 de septiembre de 2026.** La regla decía que
  hasta la revisión de abogado ningún texto del sitio la enunciaría, por ser «la misma
  condición que bloquea los tres documentos legales»; esa condición se levantó por
  decisión expresa el 5 de septiembre, cuando los tres se publicaron sin abogado
  colegiado. El texto de la ficha no decide nada por su cuenta: repite en llano el
  numeral 15.2 de los términos vigentes y enlaza al documento (RN-057). Está en
  `docs/producto/textos-web.md`, bajo `catalog.detail.warranty.*`.

  **Lo que sigue necesitando criterio profesional** es si repartir así la
  responsabilidad es sostenible para una plataforma que además cobra comisión. No lo
  cierra ninguna redacción y vive con los demás puntos abiertos en
  `docs/operacion/entrega-textos-legales-2026-09-08b.md`.
- **RN-061** Transiciones válidas de la publicación, sobre los siete estados del
  glosario:

  | Desde | Hacia | Quién lo provoca |
  |---|---|---|
  | — | `DRAFT` | El vendedor crea la publicación |
  | `DRAFT` | `DRAFT` | Guarda o corrige un dato. Se guarda el avance y se retoma donde iba |
  | `DRAFT` | `PENDING_REVIEW` | Envía a revisión con todo completo |
  | `DRAFT` | `ARCHIVED` | Descarta el borrador |
  | `PENDING_REVIEW` | `DRAFT` | El vendedor retira la solicitud antes de que se decida |
  | `PENDING_REVIEW` | `PUBLISHED` | El moderador aprueba |
  | `PENDING_REVIEW` | `REJECTED` | El moderador rechaza con motivo |
  | `REJECTED` | `DRAFT` | El vendedor retoma para corregir (RN-022) |
  | `REJECTED` | `ARCHIVED` | El vendedor desiste |
  | `PUBLISHED` | `PENDING_REVIEW` | El vendedor edita contenido moderable (RN-062) |
  | `PUBLISHED` | `PAUSED` | El vendedor pausa |
  | `PUBLISHED` | `SOLD` | El sistema, con el pago aprobado (RN-035) |
  | `PUBLISHED` | `ARCHIVED` | El vendedor archiva, o el moderador la baja por RN-024 |
  | `PAUSED` | `PUBLISHED` | El vendedor reanuda |
  | `PAUSED` | `PENDING_REVIEW` | El vendedor edita contenido moderable (RN-062) |
  | `PAUSED` | `ARCHIVED` | El vendedor archiva, o el moderador la baja por RN-024 |

  Ninguna otra transición existe. `SOLD` y `ARCHIVED` son terminales: de la
  primera lo dice RN-023 y de la segunda, que archivar es la forma de retirar algo
  para siempre. Reanudar una publicación pausada **no** pasa por moderación,
  porque pausar no cambió nada de lo que se aprobó.

  A diferencia de RN-059, de `PENDING_REVIEW` **sí** se vuelve atrás por voluntad
  de quien envió: allí no se puede porque una cédula ya vista no se retira, y aquí
  lo único que se retira es la foto de una prenda. Si el moderador ya decidió, la
  decisión se mantiene y quien retira recibe un conflicto.

  Como en RN-045, ninguna transición se pierde: cada una queda registrada con
  fecha, actor y motivo.

- **RN-062** Editar una publicación visible la devuelve a moderación solo si
  cambia lo que describe la prenda. Son campos moderables el título, la
  descripción, la marca, la categoría, la condición, la talla, las medidas, el
  color y cualquiera de las tomas: cambiar uno la manda a `PENDING_REVIEW` y deja
  de ser visible hasta que se apruebe otra vez. No lo son el precio ni el peso y
  las dimensiones de envío: cambiarlos no altera lo que un moderador aprobó y la
  publicación sigue visible.

  Es la lectura conjunta de RN-015 y RN-030, que sueltas se contradicen: la
  primera exige moderación antes de ser visible y la segunda da por hecho que el
  precio cambia en una publicación viva. Se modera lo que describe la prenda, no
  lo que cuesta. Congelar el precio al crear el pedido, que es lo que protege al
  comprador, lo sigue haciendo RN-030.

- **RN-063** Un moderador no puede aprobar ni rechazar su propia publicación.
  Quien revisa y quien es revisado tienen que ser dos personas, y la comprobación
  es del servidor.

  Es RN-060 aplicada al catálogo y por el mismo motivo: la moderación es lo que
  responde ante el comprador de que lo publicado es lo que dice ser, y una
  publicación que su propio dueño aprueba no responde por nada. Un moderador sí
  puede vender —nada lo prohíbe—; lo que no puede es decidir sobre lo suyo.

- **RN-068** En el catálogo público se ve **solo lo que está `PUBLISHED`**. Los
  otros seis estados de RN-061 no existen para quien mira: ni el borrador, ni lo
  que espera revisión, ni lo rechazado, ni lo pausado, ni lo vendido, ni lo
  archivado. Tampoco para su propio dueño con la sesión abierta: el catálogo
  enseña lo mismo a todo el mundo, y el vendedor ve lo suyo en su panel.

  Es la regla que contesta «¿por qué desapareció mi publicación?», y hasta ahora
  no estaba escrita en ninguna parte: RN-061 enumera los siete estados y sus
  transiciones, pero nunca dijo cuáles son públicos.

  **Lo vendido desaparece, y es una decisión, no un descuido.** Conservarlo con un
  sello «Vendido» daría señal de que la plataforma mueve producto y mantendría vivo
  el enlace que alguien compartió, pero obliga a decidir cuánto tiempo se queda y a
  responder algo distinto a quien no es el dueño. Se prefirió lo simple de explicar:
  si no se puede comprar, no está. Cambiar esto es cambiar esta regla.

  La consecuencia para quien implementa es que un enlace a algo que dejó de estar
  publicado responde **lo mismo que un identificador que no existe**. No se
  distingue desde fuera, por lo mismo que el 404 de una publicación ajena: decir
  «esto existía» ya es decir algo.

- **RN-074** **La identidad de quien modera no se le muestra a quien vende.** La
  bitácora la guarda —auditar exige saber quién decidió— y lo que no se hace es
  devolverla: ni el nombre, ni el correo, ni el identificador, en ninguna
  respuesta de la API ni en ninguna pantalla.

  Una decisión de moderación es de Sendik, no de la persona que la firmó.
  Ponerle nombre convierte una discrepancia con la plataforma en una
  discrepancia con alguien, y quien modera pasa a cargar personalmente con un
  rechazo que aplicó una regla escrita. Es la misma razón por la que el aviso de
  rechazo se manda a nombre de Sendik.

  Tampoco sale por el rastro la **nota** que acompaña a una decisión.

  Y aquí hace falta una precisión, porque la frase corta engaña: hoy
  `moderation_events.notes` guarda **la misma nota que el vendedor ya recibe**
  por otro camino. Al rechazar, esa nota viaja a `listings.rejection_note`, sale
  en `GET /api/v1/listings/{id}` como `rejectionNote` y va en el correo de
  rechazo, que es justo lo que RN-022 quiere. Lo que dice esta regla es que **el
  rastro no la repite**, no que exista un campo donde se pueda escribir algo que
  el vendedor no vaya a leer. **No lo hay.** Quien escriba ahí pensando que es
  privado se equivoca.

  Separar de verdad las dos notas —una para el vendedor y una para Sendik— es una
  decisión que nadie ha tomado. Mientras no se tome, se escribe una sola y se
  escribe para que la lea el vendedor.

  Hasta HU-013 esto se estaba deduciendo de RN-046, que habla de otra cosa: de
  quién puede *leer* la cédula y la selfie, no de a quién se le atribuye una
  decisión. Se escribió al hacer visible el rastro de moderación, que es la
  primera pantalla donde la pregunta se puede llegar a hacer.

  **Se cumple no trayendo el dato, no escondiéndolo.** La consulta del rastro no
  selecciona `actor_id` ni `notes`, el tipo de dominio no los lleva y el DTO de
  la API no tiene campo para ellos. Filtrar en el borde dejaría la regla a merced
  de que nadie escriba un campo de más.

## Favoritos

Las cuatro nacen con HU-011 y son las respuestas a lo que HU-009 dejó por
escrito sin responder: si un favorito sobrevive a que la publicación se archive
o se venda, y si hay tope.

- **RN-070** Los favoritos son **privados**. Son de quien los marca y no los ve
  nadie más: ni el vendedor de la publicación marcada, ni en agregado, ni
  convertidos en una cifra.

  De aquí salen dos cosas que por eso no existen: no hay contador público de
  cuánta gente marcó una publicación, y no se puede compartir la lista. Un
  contador es una señal pública derivada de un dato privado, y quien la ve la
  interpreta como demanda.

- **RN-071** En la lista de favoritos se ve **solo lo que está `PUBLISHED`**. Es
  RN-068 aplicada a la lista propia, sin excepción por ser de uno.

  Lo que deja de estar publicado desaparece de la lista **sin borrarse**: si el
  vendedor vuelve a publicarlo, vuelve a verse. Nada se borra al pausar ni al
  archivar; simplemente deja de casar con el filtro.

  Que quien mire su lista no entienda por qué algo se fue es el precio aceptado a
  cambio de no inventar estados intermedios en una lista personal.

- **RN-072** **Nadie marca como favorita su propia publicación.** No significa
  nada, y el día que exista cualquier señal derivada de los favoritos sería la
  forma más barata de inflarla.

  Se comprueba en el servidor. Esconder el control no es la regla, por lo mismo
  que en RN-060 y RN-063: la petición se puede mandar sin pasar por la interfaz.

- **RN-073** **No hay tope de favoritos por cuenta.** Se decidió a propósito:
  cualquier número que se ponga es arbitrario, molesta a quien lo alcanza y no
  protege de nada que un tope alto detenga.

  Lo que sí hay que vigilar es que la tabla crece sin techo, porque RN-071
  conserva las filas de lo que ya no se puede volver a ver. Si crece más rápido
  de lo previsto, lo que se reabre no es el tope sino si se limpian las filas
  cuya publicación quedó `SOLD` o `ARCHIVED` hace mucho.

## Búsqueda

Las ocho nacen con HU-014, la primera historia de la Fase 3, y no existía ninguna
antes: hasta el 8 de septiembre de 2026 el catálogo solo se navegaba.

- **RN-081** En la búsqueda se ve **solo lo que está `PUBLISHED`**. Es RN-068
  otra vez, y se escribe aparte porque la búsqueda es un **segundo camino hasta
  el mismo dato**: una regla que solo viva en la consulta del catálogo no protege
  a la consulta de la búsqueda. Tampoco para el propio dueño con la sesión
  abierta.

- **RN-082** El texto que alguien escribe se busca en el **título y en la
  marca**, y **no en la descripción**.

  La descripción es texto libre, largo y sin más control que la revisión del
  moderador. Que decida quién aparece primero convierte rellenarla de palabras en
  la forma más barata de salir en todas las búsquedas, y hoy no hay ni regla ni
  control que lo frene. Sin poder calibrar la relevancia por campo —ADR-0035
  explica por qué no se puede— un campo largo y libre domina el orden.

  **La consecuencia se acepta y se dice:** quien busque una palabra que solo está
  en la descripción no encuentra ese producto. La regla se cambia el día que
  exista con qué frenar el abuso, no antes.

- **RN-083** La búsqueda por texto es **sobre todo el catálogo**. Escribir en la
  caja no hereda la categoría que se estaba navegando.

  Es lo que evita el vacío engañoso: quien busca «tenis» sin darse cuenta de que
  estaba dentro de Accesorios leería «no hay» donde sí hay. La categoría no
  desaparece como idea, cambia de sitio: sigue disponible como filtro, visible y
  quitable.

- **RN-084** **Ningún resultado se adelanta por haberlo pagado.** El orden depende
  solo de lo que se buscó y del orden que se pidió. No hay resultados
  patrocinados, ni destacados, ni posición comprada.

  Se escribe ahora y no cuando el problema exista, porque «suscripción o
  destacados de pago» está en `alcance.md` como decisión sin tomar. El día que se
  tome, hay que **cambiar esta regla a la vista de todos** en vez de añadir una
  ordenación que nadie note.

- **RN-085** **La marca no es un filtro: es texto buscable.** `brand` es opcional
  y de texto libre, porque mucha prenda de segunda no tiene marca legible, y un
  filtro sobre texto libre no filtra: «Nike», «nike» y «NIKE» serían tres marcas
  distintas en el menú.

  Es la misma razón por la que el color sí es lista cerrada. Para que la marca
  llegue a ser filtro tendría que dejar de ser texto libre, y eso es otra
  decisión, con su migración y su efecto sobre lo ya publicado.

- **RN-086** Cuando una búsqueda no casa con nada, **se dice, y no se rellena**.
  No se muestran resultados aproximados, ni «quizás te interese», ni productos
  que no cumplen lo que se pidió.

  Y se enseña qué filtros están puestos, con la salida para quitarlos: sin eso, un
  filtro activo e invisible se lee como «Sendik no tiene nada».

- **RN-087** La talla se filtra **dentro de su sistema de talla**. Una M por letra
  y una 38 numérica no son comparables y no se mezclan en un mismo filtro.

  El sistema se copia al producto cuando se crea, precisamente para que cambiar la
  categoría después no reinterprete una talla ya declarada; filtrar ignorando el
  sistema desharía esa garantía en la pantalla.

- **RN-088** El orden por omisión es **relevancia cuando hay texto** y
  **publicación más reciente cuando no lo hay**, que es el orden del catálogo.
  Además se ofrecen precio de menor a mayor y de mayor a menor.

  Son cuatro y no más. Ordenar por popularidad o por favoritos queda excluido por
  RN-070: los favoritos son privados y no existe cifra pública derivada de ellos,
  y un orden por favoritos es esa cifra por la puerta de atrás.

## Carrito

Nueve reglas que nacieron con HU-015. Hasta el 10 de septiembre de 2026 este
documento no mencionaba la palabra carrito, igual que no mencionaba la búsqueda
antes de HU-014 ni los favoritos antes de HU-011.

- **RN-089** **El carrito no reserva nada.** Agregar un producto no lo aparta ni
  lo saca del catálogo: la publicación se marca vendida cuando el pago queda
  aprobado (RN-035) y no antes. Dos personas pueden llevar el mismo producto en
  su carrito y solo una lo compra.

  Es la regla que hace honesto todo lo demás, y la que obliga a RN-094 a existir:
  si el carrito apartara, nada de lo que hay dentro podría dejar de estar
  disponible.

- **RN-090** **Un carrito admite varios vendedores y se agrupa por vendedor.**
  Cada grupo será un pedido, porque un pedido es la compra de uno o varios
  productos **a un mismo vendedor** (glosario). La división se le enseña al
  comprador desde el carrito y no se le descubre al pagar: serán dos pedidos y
  dos envíos, y eso cambia lo que va a pagar.

- **RN-091** **En el carrito un producto entra una sola vez y sin cantidad.** El
  producto es único y su existencia es siempre 1: si un vendedor tiene dos
  iguales, son dos publicaciones. No hay selector de cantidad en ninguna
  pantalla, y la identidad de una línea del carrito es el par persona-producto.

- **RN-092** **Nadie agrega al carrito su propia publicación.** Es RN-072
  aplicada a comprar en vez de a guardar, y por una razón más fuerte: comprarse a
  sí mismo movería dinero y comisión en círculo. Se comprueba en el servidor.

  Mientras el carrito es anónimo no hay contra quién comparar, así que lo propio
  puede entrar en él. El momento en que aparece alguien con nombre es el ingreso,
  y es allí donde esta regla se aplica por primera vez a esas filas.

- **RN-093** **El precio del carrito es el vigente, y solo se congela al crear el
  pedido** (RN-030). Si cambió desde que se agregó, se avisa; lo que manda es el
  de ahora. El precio con el que entró se guarda **solo para poder avisar**,
  nunca para cobrar.

  Un carrito que sumara precios viejos prometería un valor que nadie va a cobrar.

- **RN-094** **Lo que deja de estar disponible se queda a la vista, apagado,
  fuera del subtotal y con un solo motivo.**

  Es la excepción deliberada a RN-071, y la diferencia es el momento: un favorito
  que desaparece es un misterio menor; un carrito que adelgaza en silencio justo
  antes de pagar es otra cosa.

  El límite se lo pone RN-068: se dice que ya no está disponible y **no se dice
  por qué**. Distinguir «se vendió» de «lo pausó su vendedor» publicaría el
  movimiento del catálogo y las decisiones de un vendedor a cualquiera que apunte
  un identificador en su carrito.

  Lo que vuelve a estar `PUBLISHED` vuelve a contar, sin que nadie lo agregue de
  nuevo: la disponibilidad no es un dato del carrito sino una lectura del estado
  de la publicación en el momento de mirar.

- **RN-095** **El carrito sin sesión vive en el navegador, se fusiona por unión al
  entrar y no cambia de dueño.** La unión no pierde lo que traía el navegador ni
  borra lo que ya había en la cuenta.

  **La fusión la dispara una persona y no el sistema**, y eso es una decisión con
  su ADR (ADR-0037): siendo el carrito anónimo, el servidor no puede distinguir
  «vuelve el mismo» de «llega otro» en un navegador compartido. Se pregunta una
  vez. Al fusionar, el carrito del navegador se consume y se borra.

- **RN-096** **El carrito no muestra total.** Solo subtotales de precio base por
  vendedor, y dice que el envío se calcula al comprar.

  RN-076 obliga a enseñar tres cifras separadas y sumadas —precio base, costo de
  envío y total— y hoy solo existe la primera. Un carrito que rotulara «total» una
  suma sin envío mentiría sobre lo que se va a pagar. El total con sus tres cifras
  nace cuando exista el costo de envío.

- **RN-097** **El carrito admite hasta 20 productos**, a diferencia de los
  favoritos, donde RN-073 decidió a propósito que no hubiera tope.

  La diferencia no es de gusto: el carrito sin sesión lo arma el navegador y **el
  servidor lo recibe entero en la fusión**, así que sin tope hay un cuerpo de
  tamaño arbitrario que alguien manda sin haber entrado.

  **Por qué veinte.** Porque un carrito lleno cabe en una sola lectura —el tope de
  un tramo de catálogo es cincuenta y su tamaño por omisión veinticuatro—, de modo
  que leer un carrito entero nunca necesita paginar, y una suma paginada no es una
  suma. Es además el mismo veinte de las dos colas del moderador.

  El rechazo se ve y no se descarta nada en silencio. El número vive en dos
  sitios —el dominio del backend y el del frontend— y es a propósito: sin sesión
  no hay servidor que lo haga cumplir.

## Precio y comisión

- **RN-026** La comisión es del **5% sobre el precio base del producto**, a cargo
  del vendedor. El envío no entra en la base de cálculo, y desde RN-076 esa frase
  tiene consecuencia visible: el costo de envío es una cifra aparte que el
  comprador ve y paga, y sobre la que Sendik no cobra nada.
- **RN-027** El comprador paga: **precio base + costo de envío**. La comisión no
  se le suma; se descuenta del desembolso al vendedor. Cómo se le muestran esas
  cifras y qué obliga a mostrarlas lo fija RN-076.
- **RN-028** El redondeo de la comisión es al peso más cercano, con la mitad
  hacia arriba. Se guarda el valor calculado, nunca se recalcula al mostrarlo.
- **RN-029** Todo cálculo de dinero se hace con decimales exactos, jamás con
  números de punto flotante.
- **RN-030** El precio se congela al crear el pedido. Un cambio posterior de
  precio en la publicación no afecta pedidos existentes.

## Pago

- **RN-031** El recaudo lo hace Wompi. Sendik no recibe ni custodia dinero de
  terceros.
- **RN-032** Medios habilitados: PSE, Nequi, tarjeta débito y crédito,
  Bancolombia a la mano y Addi. El pago contraentrega no está habilitado.
- **RN-033** La división del pago separa el valor del vendedor y la comisión de
  Sendik en la propia pasarela.
- **RN-034** El pago se libera al vendedor cuando **el comprador confirma la
  entrega**, o cuando vence la ventana de reclamo sin que confirme ni reporte
  (RN-051, RN-052). Quien confirma es el comprador, no la transportadora: la
  guía prueba que el paquete llegó, no que dentro venga lo que se publicó. Los
  días exactos que tarda el desembolso en llegar a la cuenta del vendedor se
  definen en Fase 3.
- **RN-035** La publicación se marca vendida cuando el pago queda aprobado, no
  cuando se inicia el intento.
- **RN-036** El estado del pago se confirma siempre contra la pasarela, nunca
  contra lo que diga el navegador del comprador.
- **RN-037** Todo evento recibido de la pasarela se verifica por firma y se
  procesa de forma idempotente: el mismo evento dos veces produce un solo efecto.

## Envío

Las cuatro primeras se reescribieron el 8 de septiembre de 2026, al decidirse la
integración con **Skydropx Colombia** (ADR-0034). Lo que cambió no es el proveedor
sino la posición del envío en el precio: **el comprador paga el flete y lo ve como
una cifra aparte antes de pagar**, y una cifra que alguien va a pagar no puede
seguir siendo «aproximada».

- **RN-038** La cotización de envío se obtiene de **Skydropx Colombia**, que es un
  agregador: Sendik le pide las opciones y él consulta a las transportadoras que
  tenga habilitadas. **Sendik no se integra con ninguna transportadora una por
  una.**

  Al comprador se le muestran las opciones que devuelva el agregador, cada una con
  su transportadora, su costo y su plazo estimado, y elige. Si vuelve una sola, se
  muestra una sola.

  La regla decía antes que el cotizador consultaba a Envía, Coordinadora e
  Interrapidísimo y mostraba valores **aproximados** rotulados como tales. Ninguna
  de las dos cosas sobrevive. La primera porque tres integraciones son tres
  contratos, tres claves y tres formatos que hay que mantener. La segunda porque
  **un valor aproximado deja de ser legítimo en el momento en que el comprador lo
  paga**: el artículo 26 de la Ley 1480 dice que el consumidor solo está obligado a
  pagar el precio anunciado. Lo desarrolla RN-077.

- **RN-039** La cotización se calcula con **el peso y las dimensiones declaradas
  por el vendedor** (RN-021, RN-062), **la ciudad de origen del vendedor** y **la
  dirección de destino del comprador**.

  El origen es nuevo en la regla y no es un detalle: sin él no hay tarifa. Estaba
  implícito mientras la cotización era orientativa y deja de poder estarlo cuando
  es la cifra que se cobra.

- **RN-040** **Sin cotización no hay compra, y se dice.** Si el agregador no
  responde, o no devuelve ninguna opción para ese destino, el comprador ve que el
  envío no se pudo cotizar y el pedido no avanza al pago.

  Es lo contrario de lo que decía esta regla, que prometía que la cotización nunca
  bloqueaba la compra. Aquello era cierto mientras el envío no se cobraba aparte:
  se podía seguir sin él. Ahora el flete es parte del total, y **las únicas salidas
  a un cotizador caído son dejar comprar sin saber el precio o inventarse una
  cifra**. La primera incumple el deber de informar el precio total antes de la
  transacción; la segunda es peor. Se prefiere no vender ese minuto.

  Lo que no se hace nunca es cobrar un envío estimado y ajustarlo después: eso lo
  cierra RN-077.

- **RN-041** La cotización elegida se guarda con el pedido: el identificador que
  devolvió el agregador, la transportadora, el servicio, el costo, el plazo
  estimado, el momento de la consulta y la respuesta cruda.

  Se guarda por dos razones y la segunda es nueva. La primera, poder auditar la
  diferencia con el costo real. La segunda, que **esa diferencia ahora la paga
  Sendik** (RN-077), así que dejó de ser un dato de curiosidad para ser la medida
  de cuánto cuesta la promesa. Si el descuadre agregado crece, lo que se revisa es
  RN-077, y para revisarlo hay que tenerlo medido.

- **RN-076** El comprador ve **tres cifras separadas y sumadas**: el **precio
  base** del producto, el **costo de envío** y el **total**. El precio base es el
  que publica el vendedor y sobre el que se calcula la comisión (RN-026); el costo
  de envío es el de la opción elegida; el total es lo que se le cobra.

  El rango de RN-020 —mínimo 10.000 y máximo 20.000.000— se aplica **al precio
  base**, no al total. Y la comisión se sigue calculando solo sobre el precio base:
  RN-026 y RN-027 ya lo decían y no cambian.

  Esto no es una preferencia de diseño. El artículo 50, literal c), de la Ley 1480
  obliga a informar «el precio total del producto incluyendo todos los impuestos,
  costos y gastos que deba pagar el consumidor para adquirirlo» y añade que «en
  caso de ser procedente, se debe informar adecuadamente y por separado los gastos
  de envío». Separado **y** total: las dos cosas, no una.

  El literal d) del mismo artículo extiende el deber a dos momentos más, y los dos
  son trabajo de Fase 3: el **resumen previo** a cerrar la transacción, con el
  precio de cada producto, el total y los gastos de envío; y el **acuse del
  pedido**, que sale a más tardar el día calendario siguiente y vuelve a llevar el
  plazo de entrega, el precio exacto, los gastos de envío y la forma de pago.

  Sobre impuestos: el transporte nacional de carga **está excluido de IVA** por el
  artículo 476, numeral 9, del Estatuto Tributario, de modo que el costo de envío
  que se le muestra al comprador no lleva impuesto añadido.

- **RN-077** **El valor de envío que se le muestra al comprador antes de pagar es
  el que se le cobra, y no se recotiza nunca.** Si el costo real que factura el
  agregador resulta mayor, **la diferencia la asume Sendik**.

  El artículo 26 de la Ley 1480 no deja alternativa: «el consumidor solo estará
  obligado a pagar el precio anunciado». Cobrar después la diferencia no es una
  opción que se descartara por generosidad; es una que no existe.

  Que la asuma Sendik y no el vendedor **sí** es una decisión, tomada el 8 de
  septiembre de 2026, y tiene un coste que conviene ver escrito: el dato que
  descuadra una cotización es casi siempre el peso o las medidas, y los declara el
  vendedor (RN-021). El Código de Comercio se los imputaría a él —el artículo 1010
  hace responsable al remitente de la inexactitud de esas indicaciones—. Se eligió
  lo otro porque descontarlo del desembolso significa que **el vendedor no sabe
  cuánto va a cobrar hasta que alguien pese la caja**, y quien no puede predecir su
  ingreso no publica.

  Lo que sí se hace con un descuadre repetido es mirarlo por vendedor: si uno
  declara siempre de menos, eso es un problema de publicación y se trata como tal,
  no cobrándoselo al comprador.

- **RN-078** **El remitente es el vendedor.** Sendik genera la guía a través del
  agregador **por cuenta del vendedor**, no en nombre propio: no vende el servicio
  de transporte ni se interpone como transportador.

  Es la posición que sostiene el resto del documento. Sendik es un portal de
  contacto en el sentido del numeral 18 del artículo 5 de la Ley 1480 —añadido por
  el artículo 6 de la Ley 2439 de 2024—, y contratar el transporte en nombre propio
  lo convertiría en proveedor de ese servicio frente al consumidor, con la
  responsabilidad que eso arrastra. Emitir la guía por cuenta de otro no tiene ese
  efecto.

  Del contrato de transporte salen dos consecuencias que hay que respetar:

  1. **Partes del contrato son el transportador y el remitente** (Código de
     Comercio, art. 1008); el destinatario lo es cuando acepta el contrato. Quien
     le reclama a la transportadora por pérdida, avería o retardo es, por tanto,
     **el vendedor**. Que el comprador no tenga que hacerlo es justamente lo que
     cubre el Respaldo: su dinero sigue retenido (RN-054).
  2. **El valor declarado en la guía es el precio base congelado del pedido**
     (RN-030). El artículo 1031 fija la indemnización por pérdida total en «el
     valor declarado por el remitente para la carga afectada», así que declarar de
     menos para abaratar el flete deja a alguien pagando la diferencia, y por
     RN-054 ese alguien es Sendik, que ya le devolvió al comprador. El valor lo
     pone el sistema; no es un campo que el vendedor escriba.

- **RN-079** La guía y sus eventos de seguimiento se le muestran **al comprador y
  al vendedor**, desde el pedido, mientras el envío está en curso.

  **La fecha de entrega la fija el evento de entrega del seguimiento**, se guarda
  con su origen y **no se edita a mano**. Es el hecho que arranca los dos relojes
  que gobiernan el dinero: la ventana de reclamo de RN-051 y los cinco días
  hábiles de retracto de RN-075.

  Esto no contradice a RN-034, lo completa: **quien confirma la entrega sigue
  siendo el comprador**, porque la guía prueba que el paquete llegó y no que dentro
  venga lo publicado. Lo que aporta el seguimiento es la **fecha**, que es otra
  cosa y que hasta ahora no tenía de dónde salir. Sin ella, RN-052 —dar la entrega
  por confirmada al vencer la ventana— no tiene desde cuándo contar.

- **RN-080** Antes de pagar se le informa al comprador **el plazo estimado de la
  opción de envío que eligió**. Ese plazo es el que devuelve el agregador y se
  rotula como estimado, que es lo que es.

  Además, y con independencia de él, rige el **plazo máximo de treinta (30) días
  calendario** del artículo 50, literal h), de la Ley 1480, contados a partir del
  día siguiente a aquel en que el comprador comunicó su pedido. Si se supera, el
  comprador puede terminar el contrato y recuperar **todas las sumas pagadas, envío
  incluido, sin retención ni descuento**, y esa devolución tiene plazo propio:
  **máximo quince (15) días calendario**.

  El mismo literal le impone un deber **al portal de contacto**, no solo al
  vendedor: si el producto no está disponible, **hay que informarlo de inmediato**.
  Ese deber es de Sendik y es directo.

  **La cobertura es la que cubra el agregador**, y no se anuncia ninguna otra. En
  particular no se anuncia cobertura nacional mientras nadie la haya comprobado
  contra las transportadoras que Skydropx tenga habilitadas: lo anunciado es
  exigible, y una ciudad prometida a la que no llega nadie es un incumplimiento por
  escrito.

## Pedido

- **RN-042** Un pedido corresponde a un solo vendedor. Comprar a dos vendedores
  genera dos pedidos.
- **RN-043** Un pedido sin pago aprobado en 60 minutos se cancela y la prenda
  vuelve a estar disponible.
- **RN-044** Transiciones válidas: `CREATED` a `PAYMENT_PENDING` a `PAID` a
  `PREPARING` a `SHIPPED` a `DELIVERED` a `RELEASED`. Se puede cancelar desde
  `CREATED` y `PAYMENT_PENDING`.
- **RN-045** Ningún estado retrocede. Toda transición queda registrada con
  fecha, actor y motivo.

## Datos personales

- **RN-046** La cédula, la selfie y la cuenta bancaria se guardan cifradas y solo
  las ve el proceso de verificación. Nunca salen en una respuesta de la API.
- **RN-047** El comprador ve del vendedor: nombre, ciudad, sello de verificado y
  reputación. Nada más.
- **RN-048** La dirección completa del comprador se revela al vendedor solo
  cuando el pago está aprobado.
- **RN-049** El usuario puede solicitar la eliminación de sus datos. Se conserva
  lo que la ley obligue a conservar por razones contables y fiscales, y se
  documenta qué es y por cuánto tiempo.

## Producto no conforme y reintegro

Estas reglas existen desde Fase 1 porque el sitio informativo las anuncia, y en
Colombia lo que se anuncia es exigible. El flujo operativo que las ejecuta
—bandeja de disputas, resolución y panel de moderación— llega en Fase 4; los
estados y las pantallas de ese flujo se definen allí y no aquí.

- **RN-050** Producto no conforme es el que no corresponde a lo publicado
  —modelo, talla declarada, medidas, condición o marca distintos—, el que llega
  con un daño que no se declaró (RN-021, RN-024), y el de tecnología que llega
  abierto habiéndose declarado sellado (RN-065). **No** son producto no
  conforme: que la talla no siente como se esperaba, que el color se vea distinto
  en pantalla, ni el simple arrepentimiento. Para eso está el derecho de retracto
  (RN-057), que es otra cosa.
- **RN-051** El comprador tiene **3 días hábiles** contados desde la entrega para
  reportar un producto no conforme. Es la ventana de reclamo. Confirmar la
  entrega la cierra: quien confirma da por buena la prenda.
- **RN-052** Si al vencer la ventana el comprador no ha confirmado ni reportado,
  la entrega se da por confirmada. Sin esta regla un comprador inactivo dejaría al
  vendedor sin cobrar de forma indefinida.

  **Dar la entrega por confirmada no libera el pago por sí solo.** Lo que libera
  el pago es RN-075, que exige además que haya vencido el plazo de retracto.
  Hasta el 5 de septiembre de 2026 esta regla decía que el pago se liberaba aquí
  mismo, y eso abría el agujero que RN-075 cierra.

- **RN-075** **El pago no se libera antes de que venza el derecho de retracto.**
  La liberación exige las dos cosas: que la entrega esté confirmada —por RN-051 o
  por RN-052— y que hayan pasado **cinco (5) días hábiles desde la entrega**, que
  es el plazo que la ley colombiana le da al comprador para retractarse.

  El motivo es aritmético y no de criterio. La ventana de reclamo dura tres días
  hábiles y el retracto cinco, así que **los días cuarto y quinto el comprador
  conserva un derecho legal sobre un dinero que ya se había liberado**. En esos
  dos días la promesa de RN-054 —«el reintegro sale de la retención, nunca del
  bolsillo del vendedor»— dejaba de ser cierta: no quedaba retención de la que
  sacarlo, y Sendik tendría que poner el dinero o perseguir al vendedor.

  Consecuencia asumida: **el vendedor cobra dos días hábiles más tarde** de lo que
  decía la regla anterior. Es el precio de que el respaldo sea verdad, y no hay
  forma de bajarlo sin que alguien asuma el riesgo del retracto tardío.

  Un retracto ejercido dentro de esos cinco días hábiles suspende la liberación
  igual que lo hace un reporte abierto (RN-053), y el reintegro se rige por el
  plazo legal: **máximo quince (15) días calendario** desde que se ejerce el
  derecho, contados una vez el comprador devuelve el producto y entrega los datos
  que se le piden para el reintegro.

  El retracto y el reporte de producto no conforme son cosas distintas y no se
  confunden: RN-050 define el segundo, RN-057 remite al primero.
- **RN-053** Un reporte abierto suspende la liberación del pago. La transición
  del pedido a `RELEASED` no ocurre mientras el reporte esté sin resolver, y el
  reporte no puede abrirse una vez liberado el pago.
- **RN-054** El reintegro sale de la retención, nunca del bolsillo del vendedor:
  como el pago no se ha liberado, el dinero que se le devuelve al comprador es el
  que la pasarela todavía retiene. Es lo que hace que el respaldo no dependa de
  que el vendedor colabore.

  Esta regla **solo se sostiene si la retención dura más que los derechos que
  puede ejercer el comprador**, y de eso responde RN-075. Vale tanto para el
  reintegro por producto no conforme como para el del retracto.
- **RN-055** Si el reporte se acepta, se le reintegra al comprador el valor del
  producto y el envío que pagó, y el flete de regreso lo asume el vendedor. La
  comisión de Sendik no se cobra sobre un pedido reintegrado.
- **RN-056** El reporte se abre desde el pedido, en la cuenta del comprador, y
  exige fotos de lo recibido. Es el único canal: un reclamo por correo o por
  redes se responde indicando dónde abrirlo, para que quede registro.
- **RN-057** El derecho de retracto que fija la ley colombiana para las compras
  por internet existe **además** de estas reglas y no lo sustituyen. Sus plazos,
  excepciones y quién asume el transporte se rigen por los términos y
  condiciones, y solo se publican con redacción revisada por abogado.
- **RN-058** Un mismo pedido admite un solo reporte. Reabrirlo tras una decisión
  exige revisión manual.
