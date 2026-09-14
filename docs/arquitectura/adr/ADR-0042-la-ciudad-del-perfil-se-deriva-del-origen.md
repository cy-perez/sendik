# ADR-0042 — La ciudad del perfil se deriva de la dirección de origen

**Fecha:** 2026-09-14 · **Estado:** aceptada

## Contexto

Desde HU-001 el perfil tiene `city`: texto libre, opcional y clasificado como dato
público. `City.java` defendía esa forma por escrito: «una lista obliga a mantenerla y
deja fuera a quien viva donde no se pensó; para lo que este dato hace, que es dar una
idea de dónde sale el producto, el texto libre basta».

HU-017 le da a esa idea un uso concreto. RN-039 cotiza el envío con «la ciudad de origen
del vendedor», y con «bogota», «Bogotá» y «Bogotá D.C.» no hay tarifa: son tres destinos
y ninguno casa con la división del DANE. HU-016 ya había resuelto lo mismo para el otro
extremo del envío con RN-100 —el municipio no se escribe, se elige— y HU-017 guarda el
origen con ese mismo municipio elegido.

Con eso la cuenta tendría dos ciudades: la escrita a mano en el perfil y el municipio
del origen. Alguien podría decir «Bogotá» y despachar desde Soacha, y la que se muestra
junto a sus publicaciones no sería la que cotiza.

## Opciones

1. **Conviven.** `city` sigue siendo lo que se muestra y el origen es otro dato. Dos
   verdades, y la pública puede contradecir a la que cobra.
2. **El municipio del origen la reemplaza, copiándose en `city`.** Una verdad, pero la
   copia envejece: si el DANE renombra el municipio, el perfil sigue diciendo el nombre
   viejo mientras el origen dice el nuevo.
3. **El municipio del origen la reemplaza, y se lee uniendo.** Con origen, `city` queda
   en nulo y la ciudad del perfil se compone al leer desde la fila del origen y la
   división. Sin origen, `city` sigue siendo el texto libre de siempre.

## Decisión

La opción 3. Mientras una cuenta no tiene dirección de origen, la ciudad del perfil es
lo que escribió y se edita como hasta hoy. Al guardar el origen, el texto libre se
descarta —`users.city` pasa a nulo—, la ciudad del perfil es **el municipio del origen
con su departamento al lado**, y no se edita desde el perfil: se cambia desde el origen.
Al borrar el origen la ciudad queda vacía, porque lo escrito antes no se guardó en
ninguna parte, y vuelve a poder escribirse a mano.

`GET /api/v1/users/me` lo dice con `cityEditable`, y la edición del perfil ignora la
ciudad que llegue cuando hay origen, para que no haya forma de volver a tener dos.

## Motivo

**Una sola verdad**, y la que cobra: el dato con el que se cotiza es el que se muestra.
**Se lee uniendo y no se copia** por lo mismo que HU-016 no copió el nombre del
municipio en la dirección de entrega: la fila de `municipalities` no desaparece nunca
—`ON DELETE RESTRICT`— y un municipio renombrado devuelve el nombre nuevo, que es lo
correcto.

**Lo escrito a mano se conserva hasta que haya origen** y no se migra ni se borra:
nadie pierde un dato público que escribió por un despliegue, y cruzar «bta» con la
lista del DANE acierta en unos y falla en otros sin que nadie lo revise.

**Con departamento al lado** porque hay más de un San Pedro, y es lo que ya hace la
libreta (criterio 24 de HU-016).

## Consecuencias

- `City.java` cambia su Javadoc: el texto libre es la forma solo mientras no hay origen.
- El perfil deja de traducirse directo desde `User` en el borde. `ReadProfileViewUseCase`
  arma `ProfileView` —la cuenta, la ciudad resuelta y si se edita— y `UsersController` lo
  usa en sus cuatro salidas. `ReadProfileUseCase` sigue existiendo y devolviendo `User`:
  es la puerta pública por la que `catalog` pregunta nombre y correo para sus avisos.
- `UpdateProfileUseCase` recibe el repositorio del origen y deja la ciudad en nulo cuando
  hay uno, aunque el cuerpo la mande.
- `ProfileResponse` gana `cityEditable`, y `/mi-cuenta` pinta la ciudad como texto con
  enlace a `/mi-direccion-de-origen` cuando es falso.
- La ciudad del perfil sigue clasificada como pública y sigue sin mostrarse en ninguna
  respuesta pública: el perfil público del vendedor no la lleva. Publicar el municipio
  es otra decisión, anotada en la historia.
- El día que exista el pedido, copiará el origen igual que copiará la dirección de
  entrega, por lo mismo que RN-030 congela el precio.

## Cuándo revisar

Si un vendedor real despacha desde más de un sitio, «una sola» se quedó corta y la
ciudad del perfil tendrá que elegir entre varias. Y cuando se decida mostrar el
municipio en el perfil público o en la ficha: entonces esta ciudad derivada es lo que
se publica, y la discrepancia de `datos-personales.md` —clasificada como pública, nunca
mostrada— se cierra.
