-- El carrito. HU-015, RN-089 a RN-097.
--
-- No hay tabla `carts`. Un carrito es "las filas de esta persona", igual que los
-- favoritos de V16: una tabla de cabecera no guardaria ni un dato que no se
-- deduzca de las filas, y obligaria a crearla antes de la primera.
--
-- Una fila por par persona-publicacion, y la clave primaria ES el par. No es solo
-- una unicidad: es que el producto es unico y su existencia siempre 1 (RN-091),
-- asi que no hay cantidad que guardar ni una segunda linea posible del mismo
-- producto. Quien tenga dos camisas iguales tiene dos publicaciones.
--
-- Y esa misma restriccion es lo que hace idempotente al criterio 4 sin leer antes
-- de escribir. Entre un "si no existe, guarda" y la escritura cabe la peticion de
-- la otra pestana. El adaptador se apoya en la clave con ON CONFLICT DO NOTHING.
--
-- No hay `updated_at`: un producto no se modifica en el carrito, entra y sale.
--
-- Las dos claves foraneas van sin ON DELETE CASCADE, como en `favorites` y por lo
-- mismo: ninguna de las dos filas de las que cuelga se borra nunca -- una
-- publicacion se archiva y una cuenta se anonimiza conservando su fila. Lo que si
-- borra estas filas es el cierre de cuenta, explicitamente, porque son dato
-- personal: dicen que le interesa a alguien identificado y ademas que estuvo a
-- punto de comprarlo (docs/operacion/datos-personales.md).
CREATE TABLE cart_items (
    user_id     uuid           NOT NULL REFERENCES users (id),
    listing_id  uuid           NOT NULL REFERENCES listings (id),
    created_at  timestamptz    NOT NULL,
    added_price numeric(12, 0) NOT NULL,
    PRIMARY KEY (user_id, listing_id)
);

COMMENT ON TABLE cart_items IS
    'Lo que alguien reunio y todavia no ha comprado. Agregar no reserva nada: RN-089';

COMMENT ON COLUMN cart_items.created_at IS
    'Cuando entro al carrito. Ordena la lista y decide que se conserva cuando la fusion no cabe entera (criterio 10)';

-- El precio con el que entro, y no sirve para cobrar.
--
-- Existe solo para poder avisar de que el precio cambio (criterio 18). Lo que se
-- cobra es siempre el vigente de la publicacion (RN-093), y el precio se congela
-- al crear el pedido y no antes (RN-030).
--
-- numeric(12,0) como todo el dinero del proyecto: pesos sin decimales, jamas
-- float. Es RN-029 dicho en el esquema.
--
-- Es NOT NULL a proposito aunque solo sirva para un aviso: una fila sin el no
-- podria responder la pregunta "cambio de precio?" y tendria que contestar que no
-- sin saberlo, que es peor que no tener la fila.
COMMENT ON COLUMN cart_items.added_price IS
    'Con cuanto costaba entro. Para avisar del cambio (criterio 18), nunca para cobrar: RN-093';

-- El carrito de alguien, escrito como indice. Criterio 14 y el orden de los grupos.
--
-- Las tres columnas en el mismo orden que la consulta: filtra por persona, ordena
-- por el gesto y desempata por publicacion. El desempate no es de adorno:
-- `created_at` se repite -- dos toques seguidos, y siempre con un reloj fijo en
-- pruebas -- y sin el, dos lecturas del mismo carrito podrian devolver las filas
-- en distinto orden y la pantalla barajaria los grupos sin que nada hubiera
-- cambiado.
--
-- La clave primaria no sirve para esto: es (user_id, listing_id), asi que ordena
-- por identificador de publicacion y no por fecha.
--
-- No es parcial, al reves que el indice del catalogo, y por una razon mas fuerte
-- que en `favorites`: alli el estado que RN-071 exige vive al otro lado del join;
-- aqui directamente no se filtra por estado, porque RN-094 quiere lo no disponible
-- a la vista.
CREATE INDEX idx_cart_items_recent
    ON cart_items (user_id, created_at DESC, listing_id DESC);

COMMENT ON INDEX idx_cart_items_recent IS
    'El carrito de HU-015: filtra por persona y ordena por la fecha en que entro cada producto';
