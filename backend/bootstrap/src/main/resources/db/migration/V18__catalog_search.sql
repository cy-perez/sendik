-- La busqueda del catalogo. HU-014, ADR-0035.
--
-- No agrega ninguna columna: se busca y se filtra sobre lo que el vendedor ya declara.
-- Lo unico que hace falta es poder consultarlo sin recorrer la tabla entera.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

-- ---------------------------------------------------------------------------
-- Acentos
-- ---------------------------------------------------------------------------
--
-- Criterio 5: quien busca «camison» tiene que encontrar «camison» y «camison» con
-- tilde. PostgreSQL no lo hace solo, y la salida es la extension `unaccent`.
--
-- ADR-0035 dejo esto como el unico punto que dependia de algo que no controlamos:
-- si el proveedor gestionado no permitiera la extension, habria que normalizar en una
-- columna aparte. Se comprobo contra la base de `dev` -- Neon, capa gratuita -- el 9 de
-- septiembre de 2026, antes de escribir esta migracion: la extension se instala y
-- `unaccent('camison')` con tilde devuelve `camison` sin ella.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- El envoltorio existe por una razon concreta y no por gusto: `unaccent(text)`, la forma
-- de un solo argumento, es STABLE y no IMMUTABLE, porque resuelve el diccionario por el
-- `search_path` y ese puede cambiar entre sesiones. Una funcion STABLE no se puede usar
-- en la expresion de un indice.
--
-- La forma de dos argumentos si es IMMUTABLE: se le dice exactamente que diccionario
-- usar y deja de depender del entorno. Envolverla nombrando el diccionario es lo que
-- hace que esta funcion pueda declararse IMMUTABLE **siendolo de verdad**, y no por
-- declararlo a la fuerza.
CREATE FUNCTION sendik_unaccent(text) RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

COMMENT ON FUNCTION sendik_unaccent(text) IS
    'Quita tildes de forma inmutable, para poder indexar la expresion. HU-014, criterio 5';

-- ---------------------------------------------------------------------------
-- El indice del texto buscable
-- ---------------------------------------------------------------------------
--
-- RN-082: el titulo y la marca, y no la descripcion. La descripcion es texto libre y
-- largo, y sin poder calibrar la relevancia por campo -- ADR-0035 explica por que no se
-- puede -- un campo asi domina el orden y convierte rellenarlo de palabras en la forma
-- mas barata de salir en todas las busquedas.
--
-- Los dos `coalesce` no son defensivos: `products` admite nulo en casi todo porque un
-- borrador se guarda a medias (criterio 5 de HU-007), y sin ellos el titulo nulo de un
-- borrador anularia la concatenacion entera.
--
-- **La expresion tiene que ser identica a la de la consulta**, o el planificador no usa
-- el indice y nadie se entera: la busqueda devuelve lo mismo, solo que recorriendo la
-- tabla. Vive en `PostgresSearchEngine` como constante por esa razon.
--
-- **No es parcial, y no puede serlo.** El de V14 filtra por `status` porque esa columna
-- esta en la misma tabla que indexa; aqui el estado vive en `listings` y el texto en
-- `products`, y un indice no puede mirar otra tabla. El filtro por `PUBLISHED` lo sigue
-- resolviendo el indice parcial de V14 al unir.
CREATE INDEX idx_products_search
    ON products
    USING gin (to_tsvector('spanish', sendik_unaccent(coalesce(title, '') || ' ' || coalesce(brand, ''))));

COMMENT ON INDEX idx_products_search IS
    'El texto buscable de RN-082: titulo y marca, sin tildes y con el diccionario spanish';

-- ---------------------------------------------------------------------------
-- Lo que NO se indexa, y por que
-- ---------------------------------------------------------------------------
--
-- No hay indice para condicion, color ni talla. Los tres son de lista cerrada y poca
-- selectividad -- quince colores sobre un catalogo pequeno reparten mucho -- asi que el
-- planificador prefiere filtrarlos despues de unir, y un indice que nunca se elige es
-- peso muerto en cada escritura.
--
-- Tampoco hay indice para el orden por precio. Ordenar por `p.price` con el filtro en
-- `l.status` obliga a ordenar en memoria hagamos lo que hagamos: son dos tablas y ningun
-- indice puede cubrir una columna de cada una. Con el catalogo que hay -- `prod` no se ha
-- desplegado nunca -- eso es gratis, y el momento de volver a mirarlo es exactamente la
-- primera senal de revision de ADR-0035: pasar de 5.000 publicaciones `PUBLISHED`.
--
-- Se anota aqui, y no se agregan «por si acaso»: un indice se crea cuando un plan lo
-- pide, no cuando parece que hara falta.
