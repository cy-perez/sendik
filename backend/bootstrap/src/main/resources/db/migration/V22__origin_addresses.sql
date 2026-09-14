-- La direccion de origen del vendedor. HU-017.
--
-- Desde donde despacha quien vende. RN-039 la necesita para cotizar y RN-078
-- para que el vendedor sea el remitente de la guia. Hoy no la usa nadie:
-- guardarla no cotiza, no comprueba cobertura de recogida y no promete nada.
--
-- LA CLAVE PRIMARIA ES LA CUENTA, y ahi se separa de `shipping_addresses`,
-- donde hay identificador propio porque una persona puede tener varias. Aqui
-- hay una por cuenta --RN-039 habla de "la ciudad de origen del vendedor", en
-- singular-- y la clave primaria sobre `user_id` es lo que convierte el
-- criterio 3 en algo que la base hace cumplir: guardar otra reemplaza, y dos
-- pestanas escribiendo a la vez dejan una, no dos. El `ON CONFLICT (user_id)`
-- del adaptador vive de esta restriccion.
--
-- SIN NOMBRE NI TELEFONO. El remitente es el titular de la cuenta (RN-078) y
-- sus datos estan en `users`. Que el perfil tenga telefono lo exige el caso de
-- uso al guardar; la base no puede exigirlo sin una restriccion entre tablas
-- que ademas impediria quitar el telefono despues, y eso esta anotado en la
-- historia como caso a cerrar cuando exista la guia.
--
-- EL MUNICIPIO ES CLAVE FORANEA Y NO TEXTO (RN-100), con ON DELETE RESTRICT,
-- por lo mismo que en V21: el DANE suprime municipios marcandolos inactivos y
-- hay origenes de gente real que los apuntan. El nombre se lee uniendo.
--
-- LO QUE SE ESCRIBE VA CIFRADO, en un solo campo, como en V21: dentro de
-- `details_cipher` va un documento JSON con la linea, el complemento, las
-- indicaciones para la recogida y el codigo postal. Fuera del cifrado quedan
-- el municipio --clave foranea, no puede colgar de un criptograma-- y las
-- fechas. La consecuencia es la misma que alli: en un volcado se lee en que
-- municipio despacha cada cuenta sin ninguna clave, que es el dato que la
-- clasificacion de datos-personales.md pone en nivel Publico.
--
-- SIN ON DELETE CASCADE desde `users`, como en V16, V19 y V21: una cuenta no
-- se borra, se anonimiza conservando su fila. Lo que si borra esta fila es el
-- cierre de cuenta, explicitamente y en la misma transaccion (RN-102).
--
-- SIN INDICE APARTE: la unica consulta es por cuenta, y la clave primaria ya
-- es ese indice.
--
-- `users.city` NO SE TOCA. Con origen, la ciudad del perfil es el municipio de
-- esta fila y el caso de uso deja `city` en nulo al guardar; se lee uniendo y
-- no se copia (ADR-0042). Lo escrito a mano antes se conserva tal cual hasta
-- que cada cuenta guarde su origen: no hay migracion de datos.
CREATE TABLE origin_addresses (
    user_id             uuid        PRIMARY KEY REFERENCES users (id),
    municipality_code   text        NOT NULL REFERENCES municipalities (code) ON DELETE RESTRICT,
    details_cipher      text        NOT NULL,
    details_key_version smallint    NOT NULL,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,

    CONSTRAINT origin_addresses_key_version_valid CHECK (details_key_version > 0)
);

COMMENT ON TABLE origin_addresses IS
    'La direccion de origen del vendedor de HU-017. Una por cuenta: la clave primaria es la cuenta';

COMMENT ON COLUMN origin_addresses.municipality_code IS
    'Codigo DANE de cinco digitos. El departamento son sus dos primeros y por eso no se guarda aparte: RN-100';

COMMENT ON COLUMN origin_addresses.details_cipher IS
    'JSON cifrado con la linea, el complemento, las indicaciones para la recogida y el codigo postal';
