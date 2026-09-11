-- La libreta de direcciones de entrega. HU-016, RN-098 a RN-104.
--
-- A donde quiere alguien que le llegue lo que compra, a nombre de quien y con
-- que telefono. Guardarla no cotiza nada ni comprueba cobertura (RN-103): el
-- envio espera al agregador y esta tabla no.
--
-- UNA ENTIDAD CON IDENTIDAD PROPIA, y ahi se separa de `favorites` y de
-- `cart_items`, donde la clave primaria es el par persona-publicacion. Aqui no
-- hay par: la misma persona puede guardar dos direcciones identicas en todos sus
-- campos --la casa y la de un regalo a la misma casa, con otro nombre de quien
-- recibe-- y son dos filas distintas. Por eso `id` propio.
--
-- EL MUNICIPIO ES CLAVE FORANEA Y NO TEXTO (RN-100). De eso vive que se pueda
-- cotizar: escrito a mano, "Bogota" y "bogota" son dos destinos y ninguno tiene
-- tarifa. El departamento no se guarda porque no hace falta: los dos primeros
-- digitos del codigo del municipio son los suyos, sin excepcion en las 1122
-- filas de V20, asi que se deriva. Guardarlo seria poder contradecirlo.
--
-- ON DELETE RESTRICT, y no es defensa contra un borrado que vaya a ocurrir: es
-- la afirmacion de que no puede ocurrir. Cuando el DANE suprima un municipio,
-- V20 manda marcarlo inactivo y no borrarlo, justamente porque hay direcciones
-- de gente real que lo apuntan. Esta restriccion es lo que convierte esa regla
-- en algo que la base hace cumplir.
--
-- Y es tambien lo que hace innecesario copiar aqui el nombre del municipio: la
-- fila de `municipalities` no desaparece nunca, asi que el nombre siempre se
-- puede leer uniendo. Un municipio renombrado por el DANE devuelve el nombre
-- nuevo, que es lo correcto y no un defecto.
--
-- LO QUE SE ESCRIBE VA CIFRADO, y en un solo campo. Dentro de `details_cipher`
-- va un documento JSON con el nombre de quien recibe, el telefono, la linea de
-- direccion, el complemento, las indicaciones y el codigo postal. Fuera quedan
-- el municipio, la marca de predeterminada y las fechas, que son lo unico por lo
-- que esta tabla se consulta.
--
-- Uno y no seis pares de columnas `_cipher`/`_key_version` como en V8: alli cada
-- dato sensible se lee por separado --el numero de documento tiene ademas su
-- huella y sus ultimos cuatro-- y aqui los seis campos se leen y se escriben
-- siempre juntos, porque juntos son una direccion. Seis pares serian doce
-- columnas que nunca se consultan por separado. Es ademas la forma que
-- `docs/arquitectura/modelo-datos.md` ya preve para `orders.shipping_address`,
-- que es la copia que el pedido hara de esto.
--
-- Se cifra aunque la clasificacion de `docs/operacion/datos-personales.md` ponga
-- la direccion en nivel Interno y no en Sensible. El motivo: la copia del pedido
-- ya estaba escrita como cifrada, y cifrar la copia dejando la fuente en claro no
-- protege nada. Ademas, cuando quien recibe no es el titular, aqui hay el nombre
-- y el telefono de un tercero que nunca abrio una cuenta (RN-104).
--
-- NO HAY TABLA DE CABECERA. Una libreta es "las filas de esta persona", como el
-- carrito y los favoritos. La unica invariante que una cabecera podria guardar
-- --cual es la predeterminada-- la guarda mejor el indice unico parcial de abajo.
--
-- LA CLAVE FORANEA A `users` VA SIN ON DELETE CASCADE, como en V16 y V19: una
-- cuenta no se borra, se anonimiza conservando su fila. Lo que si borra estas
-- filas es el cierre de cuenta, explicitamente y en la misma transaccion que
-- anonimiza (RN-102). Eso no es limpieza de cortesia: es lo que sostiene lo que
-- `datos-personales.md` afirma sobre la ventana de quince minutos del token de
-- acceso, que es aceptable solo porque cuando se abre ya no queda dato personal
-- que alcanzar.
CREATE TABLE shipping_addresses (
    id                  uuid        PRIMARY KEY,
    user_id             uuid        NOT NULL REFERENCES users (id),
    municipality_code   text        NOT NULL REFERENCES municipalities (code) ON DELETE RESTRICT,
    details_cipher      text        NOT NULL,
    details_key_version smallint    NOT NULL,
    is_default          boolean     NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,

    CONSTRAINT shipping_addresses_key_version_valid CHECK (details_key_version > 0)
);

COMMENT ON TABLE shipping_addresses IS
    'La libreta de direcciones de entrega de HU-016. No se comparte con nadie hasta que hay pago aprobado: RN-098';

COMMENT ON COLUMN shipping_addresses.municipality_code IS
    'Codigo DANE de cinco digitos. El departamento son sus dos primeros y por eso no se guarda aparte: RN-100';

COMMENT ON COLUMN shipping_addresses.details_cipher IS
    'JSON cifrado con quien recibe, telefono, linea, complemento, indicaciones y codigo postal';

-- Exactamente una predeterminada por persona, y lo garantiza la base.
--
-- Es el criterio 13 escrito como restriccion. Sin el, marcar dos direcciones a la
-- vez desde dos pestanas deja una libreta con dos predeterminadas y la pantalla
-- eligiendo cual pinta; peor, el dia que exista el pedido, eligiendo a donde se
-- manda. Una comprobacion en la aplicacion no lo puede cerrar: entre leer cual es
-- la actual y escribir la nueva cabe la otra peticion.
--
-- Es unico PARCIAL --solo sobre las filas con `is_default`-- porque un unico
-- normal sobre (user_id) prohibiria tener mas de una direccion.
--
-- Lo que este indice NO exige es que haya alguna: una libreta vacia no tiene
-- predeterminada, y ese es el caso del criterio 12. Que haya exactamente una
-- mientras haya alguna (RN-099) lo completa el caso de uso, que reasigna al
-- borrar la que lo era.
CREATE UNIQUE INDEX idx_shipping_addresses_default
    ON shipping_addresses (user_id) WHERE is_default;

COMMENT ON INDEX idx_shipping_addresses_default IS
    'Exactamente una predeterminada por persona: RN-099, criterio 13';

-- La libreta de alguien, escrita como indice. Filtra por persona, ordena por
-- cuando se guardo y desempata por identificador.
--
-- El orden no es de adorno: el criterio 11 manda que al borrar la predeterminada
-- pase a ser "la mas reciente de las que quedan", asi que la consulta que lee la
-- libreta tiene que traerla ya ordenada por ese mismo criterio. Y el desempate
-- importa por lo mismo que en V16 y V19: `created_at` se repite --dos direcciones
-- guardadas seguidas, y siempre con un reloj fijo en pruebas-- y sin el, dos
-- lecturas de la misma libreta podrian barajar las tarjetas.
--
-- No es parcial y no tiene por que serlo: aqui no hay ningun estado por el que
-- filtrar. Una direccion esta o no esta.
CREATE INDEX idx_shipping_addresses_owner
    ON shipping_addresses (user_id, created_at DESC, id DESC);

COMMENT ON INDEX idx_shipping_addresses_owner IS
    'La libreta de HU-016: filtra por persona y ordena por cuando se guardo cada direccion';
