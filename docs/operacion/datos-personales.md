# Tratamiento de datos personales

Sendik guarda datos que la ley colombiana clasifica como sensibles: numero de
documento, imagen del rostro y cuenta bancaria. Aplica
la **Ley 1581 de 2012** y el Decreto 1074 de 2015. Este documento es la regla
operativa; no sustituye asesoria juridica.

Responsable del tratamiento: Sendik, NIT 1054994043-9, Medellin, Colombia.

## Principio de partida

No se pide un dato que no tenga un uso concreto y ya definido. Cada campo nuevo
que almacene informacion de una persona debe poder responder tres preguntas
antes de escribirse en una migracion:

1. Para que se usa, en una frase.
2. Cuanto tiempo se conserva y que lo borra.
3. Quien puede leerlo.

Si alguna no tiene respuesta, el campo no se crea.

## Clasificacion

| Nivel | Datos | Trato |
|---|---|---|
| Publico | Nombre de vendedor, ciudad, publicaciones | Visible en el sitio |
| Interno | Correo, telefono, fecha de nacimiento, historial de pedidos, favoritos, carrito, direcciones de entrega | Solo el titular y la operacion |
| Sensible | Documento de identidad, selfie, cuenta bancaria | Cifrado, acceso restringido y auditado |
| Secreto | Contrasenas, tokens | Nunca legibles, ni por la operacion |

## Reglas tecnicas

- Las contrasenas se guardan con Argon2id. No se guardan, ni cifradas, ni
  reversibles.
- Los documentos de identidad y las selfies van a almacenamiento privado, nunca
  a un bucket publico. Se sirven solo con enlace firmado de caducidad corta.
- El numero de cuenta bancaria se cifra en la base de datos. En pantalla se
  muestran unicamente los ultimos cuatro digitos.
- **Los registros nunca contienen** contrasenas, tokens, numeros de documento,
  cuentas bancarias ni la imagen de una selfie. Tampoco parcialmente, tampoco en
  nivel `debug`, tampoco en el mensaje de una excepcion.
- **El texto que alguien busca tampoco se conserva.** La aplicacion no lo escribe en
  ningun registro -tampoco en el mensaje de un error, y `SearchText` no sabe imprimirse-,
  pero viaja en la direccion, y el registro de peticiones de Cloud Run guarda la direccion
  entera junto a la IP. Viajaba ademas en la cabecera `Referer` de cada recurso que carga
  esa pagina, que es la forma silenciosa: la pagina se pide una vez y su direccion se
  repite en todas las demas peticiones. Se cierra por los dos lados: `Referrer-Policy:
  strict-origin` no manda la ruta ni dentro del mismo sitio, y una exclusion del proyecto
  descarta la entrada antes de almacenarla, mire donde mire el texto
  (`docs/operacion/despliegue.md`, paso 1). Conservarlo seria tener un historial de
  busquedas: un dato nuevo, sin finalidad autorizada, que HU-014 decidio expresamente no
  tener.
- **La direccion de entrega se cifra en reposo aunque este clasificada como
  Interno.** Es la unica fila de ese nivel que lo esta, y el motivo no es que sea
  mas delicada que el telefono: es que `docs/arquitectura/modelo-datos.md` ya decia
  que la copia que el pedido hara de ella -`orders.shipping_address`- va cifrada, y
  cifrar la copia dejando la fuente en claro no protege nada. Van cifrados los seis
  campos libres -quien recibe, telefono, linea, complemento, indicaciones y codigo
  postal- en un solo documento. Quedan en claro el municipio, la marca de
  predeterminada y las fechas (HU-016, V21).

  **El municipio queda en claro porque es clave foranea** a la division
  politico-administrativa, y una clave foranea no puede colgar de un criptograma;
  no porque se consulte por el, que no se consulta. La consecuencia conviene
  decirla: en un volcado de esa tabla, en que municipio vive cada cuenta se lee sin
  ninguna clave. Es el mismo dato que la ciudad del perfil, que ademas es publica.
- Las respuestas de la API devuelven solo los campos que la pantalla necesita.
  Un endpoint de perfil publico no incluye correo ni telefono.
- Los datos de verificacion no viajan al frontend una vez aprobada la
  verificacion: basta el estado y la fecha.
- Los entornos de desarrollo nunca reciben datos reales de personas. Si hace
  falta volumen, se generan datos sinteticos.
- **Los favoritos son dato personal** y se tratan como tal (HU-011, RN-070). Dicen
  que le interesa a una persona identificada, asi que son suyos y de nadie mas: no
  los ve el vendedor de lo marcado, no existen en agregado y no hay contador
  publico. Van en la descarga de datos y **el cierre de cuenta los borra**, no los
  anonimiza: a diferencia de la fila de `users`, aqui no queda nada que conservar.

- **El carrito tambien es dato personal**, y por una razon mas fuerte que los
  favoritos (HU-015): aquellos dicen que algo le interesa a una persona
  identificada; este dice ademas que estuvo a punto de comprarlo, que es una
  intencion de compra. Va en la descarga de datos y **el cierre de cuenta lo
  borra**, igual que a los favoritos y por lo mismo.

  **El carrito que vive en el navegador no lo es, y no puede entrar en la
  descarga: Sendik no lo tiene.** Mientras no se fusione con una cuenta no hay
  nadie identificado a quien asociarlo, y en cuanto se fusiona pasa a ser filas de
  `cart_items` con dueno. Esa es la frontera exacta, y conviene tenerla escrita
  porque es la unica pieza de la plataforma que guarda algo de alguien sin
  saber de quien es.

  De la descarga sale el identificador de la publicacion y la fecha, no el titulo
  ni el precio con el que entro: el titulo es del vendedor y cambia, y el precio
  es un dato de la publicacion en un instante, no de quien la agrego.

- **Ningun dato personal se pide sin uso concreto.** La ciudad y el telefono del
  perfil son opcionales, nacen vacios y se quitan dejando el campo en blanco: no
  hacen falta para tener cuenta. La foto de perfil tambien es opcional y se quita
  cuando la persona quiera; al reemplazarla y al cerrar la cuenta, el archivo
  anterior se borra del almacen.

## Aviso de privacidad

El **aviso de privacidad** no es la politica de tratamiento y no la sustituye. La
politica es el documento completo y publico, vive en su ruta y se versiona; el aviso
es el parrafo corto que se muestra **en el momento de recoger el dato**, para que
quien lo entrega sepa quien lo va a tratar, para que y como se echa atras. El regimen
pide informar las finalidades **antes** de recoger, y una politica enlazada a la que
nadie entra no informa nada.

Lo implementa `sendik-privacy-notice`, en `shared/ui/form`, con **dos variantes**
porque los datos no son los mismos:

| Variante | Donde | Que dice de mas |
|---|---|---|
| `cuenta` | Registro, encima de las casillas | — |
| `verificacion` | Antes de empezar la verificacion de vendedor | Que **nadie esta obligado** a entregar documento, rostro y cuenta bancaria, y para que sirven exactamente |

La segunda es la que cumple la linea de mas abajo sobre explicar la finalidad de la
verificacion en el momento de pedirla. La variante se elige a mano y no se deduce de
la ruta: quien anada una pantalla que pida datos tiene que decidir cual le toca, y
equivocarse por omision daria el aviso mas flojo justo donde hace falta el otro.

El responsable, su NIT, **su direccion** y el canal salen de la configuracion, nunca
del texto: son datos de negocio (`docs/operacion/configuracion.md`). Si faltan, la
linea se omite en vez de escribir un valor inventado. La direccion se sumo el 8 de
septiembre de 2026, cuando la auditoria vio que iba escrita a mano en el archivo de
traduccion mientras `COMPANY_ADDRESS` existia sin usarse: el articulo 15 del Decreto
1377 de 2013 pide los **datos de contacto** del responsable y no solo su nombre.

**Lo que la auditoria dejo abierto y no se ha hecho:** el articulo 6, numeral 2, del
mismo decreto exige **obtener el consentimiento expreso** para los datos sensibles, no
solo informar de que no son obligatorios. Hoy la verificacion informa —el aviso lo hace
bien— y despues ofrece un boton «Empezar», que no es una manifestacion de
consentimiento; y `consents` solo admite `TERMS` y `PRIVACY`, asi que tampoco habria
donde guardar la prueba que pide el articulo 8. Es trabajo de HU-002 y esta descrito en
`entrega-aviso-de-privacidad-2026-09-08.md`.

## Consentimiento

- El registro exige aceptacion expresa y separada de los terminos y de la
  politica de tratamiento de datos. Una sola casilla para las dos cosas no es
  consentimiento valido.
- La casilla no viene marcada por omision.
- **Cada casilla enlaza al documento que acepta.** Un consentimiento tambien
  tiene que ser informado, y no lo es si la persona no puede leer el texto. El
  enlace va fuera de la etiqueta y abre en pestana nueva: dentro de la etiqueta,
  pulsarlo marcaria la casilla ademas de abrir el documento, y se aceptaria sin
  haber leido con un solo gesto.
- La pagina del documento muestra su version en pantalla. Es lo que permite
  comprobar, meses despues, que el texto que alguien acepto es el que se le
  enseño.
- Se guarda la evidencia: version del documento aceptado, fecha, hora y direccion
  IP, esta ultima como hash y nunca en claro: sirve igual como prueba de que la
  aceptacion vino de algun sitio, sin guardar la direccion.
- **El hash reduce la exposicion, pero no es anonimato, y conviene tenerlo escrito.**
  Es SHA-256 **sin clave** sobre un espacio de 2^32 direcciones: recorrerlo entero es
  cuestion de minutos, asi que para quien tenga un volcado de la base ese campo es la
  direccion. Pasar a HMAC con clave esta abierto —arrastra gestion de clave y la
  comparabilidad de las constancias ya guardadas— y hasta entonces el campo se trata
  como dato personal a todos los efectos, no como uno anonimizado.
- **Y esa direccion es la de quien acepta, no una que se pueda elegir.** Hasta el
  10 de septiembre de 2026 se tomaba la primera entrada de `X-Forwarded-For`, que
  la escribe quien llama: la constancia guardaba lo que el cliente dijera de si
  mismo, o nada si mandaba una entrada vacia. Ahora se cuenta desde el final, que
  es la parte que escribe la infraestructura (ADR-0038). Ninguna evidencia real
  quedo comprometida: `prod` no se ha desplegado nunca y lo registrado en `dev` es
  de pruebas.
- **El texto de las casillas y el del aviso viven en Transloco y no se versionan.** Es
  una limitacion conocida y conviene tenerla por escrito: la evidencia apunta a la
  version de la politica, no a la frase exacta que se mostro junto a la casilla ni al
  aviso que la persona leyo encima. Mientras esas frases solo remitan al documento, la
  cadena se sostiene; el dia que digan algo que el documento no dice, deja de
  sostenerse. Si el texto de una casilla **o del aviso** cambia de fondo, se publica una
  version nueva del documento aunque el documento no cambie, y asi la evidencia vuelve a
  apuntar a algo comprobable.

  **El aviso entra en esta regla por el articulo 16 del Decreto 1377 de 2013**, que
  obliga a conservar el modelo del aviso «mientras se traten datos personales conforme
  al mismo». El modelo se conserva —el repositorio guarda cada redaccion que existio—,
  pero sin esta regla nada ata esa historia al consentimiento de una persona concreta.
  Versionar el aviso como un documento propio seria mas fuerte y sigue sobre la mesa:
  `entrega-aviso-de-privacidad-2026-09-08.md`.
- **No hay tercera casilla de comunicaciones comerciales, y es a proposito.** Sin
  mecanismo de baja no puede enviarse publicidad (Ley 1581 y Ley 2300 de 2023), y una
  casilla que recoge un consentimiento que no se puede ejercer es peor que no
  tenerla: acumula autorizaciones para algo que no existe.
- La finalidad de la verificacion de identidad se explica en el momento de
  pedirla, no solo en la politica.

## Derechos del titular

La persona puede conocer, actualizar, rectificar y suprimir sus datos, y revocar
la autorizacion. Operativamente:

- Existe un canal de contacto visible para ejercerlos.
- El plazo de respuesta a una consulta es de diez dias habiles; el de un reclamo,
  quince habiles, prorrogables una vez.
- La cuenta admite eliminacion. Eliminar no significa borrar todo: las ordenes y
  facturas se conservan por obligacion contable y tributaria, pero se
  desvinculan del perfil y se anonimizan los datos que no sean necesarios.
- **En Fase 1 el cierre anonimiza en el acto**, no a los treinta dias. El plazo
  existe para resolver pedidos en curso y todavia no hay pedidos: no queda nada
  que la ley obligue a conservar, asi que esperar solo dejaria datos vivos. La
  fila se vacia en vez de borrarse (identificador, fecha de creacion y estado
  sobreviven, y ya no apuntan a nadie), el correo se sustituye por uno del
  dominio reservado `.invalid` para que la persona pueda volver a registrarse, y
  se borran contrasena, roles y enlaces pendientes. Cuando existan pedidos habra
  que bifurcar segun RN-009 y revisar si la fecha de nacimiento, que hoy se
  conserva, vuelve a identificar al cruzarse con un historial de compras.
- **Actualizar y rectificar se hace desde la propia cuenta**, sin pedirselo a
  nadie: nombre, ciudad y telefono se editan y se guardan en el acto.
- **El correo se cambia con verificacion previa.** Pedir el cambio no reemplaza
  nada: se manda un enlace al correo nuevo y la direccion de la cuenta solo
  cambia cuando alguien lo abre. Sin ese paso, un error de una letra dejaria a la
  persona fuera de su cuenta sin forma de volver.
- **Al cambiar el correo se avisa al anterior.** Es lo que evita el peor caso:
  quien robe una sesion cambia la direccion y saca al titular de su cuenta en
  silencio. Si la direccion pedida ya tiene cuenta, se avisa a su titular y a
  quien lo pidio se le responde exactamente igual que si estuviera libre: lo
  contrario convertiria el formulario en un detector de cuentas.
- El token de acceso ya emitido sigue siendo valido hasta quince minutos despues
  del cierre: es un JWT y ADR-0003 acepta esa ventana. **Lo que la hace aceptable
  no es su duracion sino lo que hay al otro lado.** El cierre anonimiza en la misma
  transaccion, asi que cuando la ventana se abre ya no queda dato personal que ese
  token pueda alcanzar; y revoca la familia de refresco entera, asi que la ventana
  no se puede prolongar: se agota sola y no hay forma de renovarla. Quien la tiene,
  ademas, es quien acaba de cerrar su propia cuenta.

  Este archivo afirmo hasta el 4 de septiembre de 2026 que «las rutas que devuelven
  o tocan datos responden 401 en cuanto la cuenta deja de existir», y **no es
  cierto**: el decodificador valida firma, caducidad y emisor
  -`JwtValidators.createDefaultWithIssuer`- y no consulta si la cuenta sigue viva.
  Contradecia ademas la frase anterior, que dice lo correcto.

  Se corrige el texto en vez de implementar lo que prometia, y el motivo importa:
  cumplirlo costaria una consulta por cada peticion autenticada, de forma
  permanente, para cerrar una ventana acotada que ya no expone ningun dato
  personal; y acortarla es cambiar la duracion del token de acceso, que es ADR-0003
  y pide su propia decision. **Ante una autoridad, describir el control que existe
  es mas fuerte que prometer uno que no.**
- La politica de tratamiento de datos es un enlace visible en el pie de pagina.
  Es obligatorio y es lo primero que revisa una autoridad.

## Conservacion

| Dato | Plazo |
|---|---|
| Cuenta activa | Mientras exista la cuenta |
| Favoritos | Mientras exista la cuenta. El cierre los borra en el acto |
| Direcciones de entrega | Mientras exista la cuenta. El cierre las borra en el acto, en la misma transaccion que anonimiza (RN-102) |
| Documentos de verificacion | Mientras el vendedor este activo y cinco anos mas |
| Ordenes y facturas | Diez anos, por obligacion contable |
| Registros tecnicos con IP | Seis meses |
| Cuenta eliminada | Anonimizada en el acto en Fase 1; treinta dias cuando existan pedidos |

## La direccion del comprador y el envio

Anotado el 8 de septiembre de 2026, al decidirse la integracion con el agregador
de envios (ADR-0034).

Para cotizar y para entregar, **el nombre, la direccion y el telefono del
comprador salen de Sendik** y llegan a dos sitios: al agregador y a la
transportadora que este habilite para ese envio. Los dos son encargados.

Tres consecuencias que hay que respetar al construirlo:

- **Cotizar no necesita la direccion completa.** Basta la ciudad o el codigo
  postal de destino. La direccion exacta se manda solo al emitir la guia, que es
  cuando el pago ya esta aprobado. Es el principio de finalidad aplicado a un
  caso concreto, y coincide con RN-048: la direccion completa se revela cuando
  hay pago aprobado, no antes.
- **La cadena tiene un eslabon que Sendik no elige.** Sendik contrata al
  agregador; quien entrega es una transportadora que el agregador habilita. Que
  el contrato de encargo alcance a ese segundo eslabon es una de las cosas que
  hay que confirmar con el proveedor, y hoy **no esta confirmada**.
- **La lista de encargados de la politica publica tiene que poder mantenerse.**
  Si las transportadoras cambian sin que Sendik se entere, una lista con nombres
  propios envejece sola. Por eso la politica nombra al agregador y describe a las
  transportadoras por su papel, en vez de enumerarlas.

### Lo que HU-016 decidio, y lo que dejo abierto

Anotado el 11 de septiembre de 2026, al construirse la libreta de direcciones.

**Hoy no hay encargado.** La direccion se guarda y no sale de Sendik: no hay
pedido, no hay cotizacion y no hay guia, asi que nada de esto llega todavia al
agregador ni a una transportadora (RN-098). Lo que dice esta seccion sobre los dos
eslabones empieza a aplicar el dia que se cotice, no antes.

**El dato de un tercero, y es la primera vez que el proyecto lo trata.** Cuando
quien recibe el paquete no es el titular de la cuenta -un regalo, la casa de los
padres, la porteria de una oficina-, lo que Sendik guarda es el nombre y el
telefono de alguien que nunca abrio una cuenta ni autorizo nada (RN-104).

La salida elegida es **una frase en el formulario y no una casilla**: quien guarda
la direccion declara que esta autorizado a dar esos datos y que informara del
tratamiento a quien corresponda. Se descarto la casilla con evidencia fechada, que
seria mas defendible ante la SIC, por dos motivos: una casilla por cada direccion
es friccion en el peor momento, y Sendik no puede verificar esa autorizacion de
ninguna de las dos formas, asi que la casilla daria una apariencia de control que
no existe. **Queda escrito para poder reabrirlo**, y el momento natural de hacerlo
es cuando el dato salga de verdad hacia un encargado.

**Lo que no se hace y conviene que este dicho:** no se geocodifica, no se valida
contra ningun servicio externo y no se comprueba que la direccion exista (RN-103).
Guardar una direccion no manda nada a ningun tercero.

**Y por eso el aviso de entrega no nombra a nadie.** La primera version decia que
los datos se entregan al agregador y a la transportadora, y que el vendedor ve la
direccion cuando el pago esta aprobado. Las dos cosas son ciertas del futuro y
ninguna esta en `privacy.2026-09-08b`: su numeral de destinatarios lista a Skydropx
y a las transportadoras, y **no dice que el vendedor reciba nombre, direccion ni
telefono del comprador** -que ademas no seria un encargo sino una transferencia-.
La regla de este documento es que el dia que el aviso diga algo que la politica no
dice, se publica una version nueva de la politica; como aqui no hay todavia ningun
flujo real, la salida barata es la contraria: el aviso dice lo que hoy es cierto
-que el dato no sale de Sendik- y anuncia que antes de pedir se dira a quien se
entrega.

**Lo que queda pendiente para el dia del pedido**, y conviene que este escrito para
no descubrirlo entonces: la politica necesita ganar al vendedor como destinatario,
decir bajo que figura, y decir si hay transferencia internacional -que depende de
las cuatro respuestas de Skydropx que siguen sin llegar-.

## Pendiente antes del lanzamiento

- Registro de bases de datos ante la SIC, si se superan los umbrales aplicables.
- Politica de tratamiento de datos y terminos de uso redactados y publicados.
- Aviso de privacidad en el formulario de registro y en el de verificacion.
- Contrato de encargo con cada proveedor que procese datos por cuenta de Sendik:
  pasarela, correo, almacenamiento, buscador **y envios**.
- Del proveedor de envios, cuatro datos que solo el puede dar y que ninguno esta
  confirmado: bajo que entidad y desde que pais contrata, si su contrato de
  encargo cubre a las transportadoras que habilita, y si su adenda de tratamiento
  cumple lo que exige el articulo 25 del Decreto 1377 de 2013. Estan en
  `entrega-textos-legales-2026-09-08b.md`.
