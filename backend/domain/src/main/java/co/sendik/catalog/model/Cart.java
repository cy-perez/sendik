package co.sendik.catalog.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que alguien reunio y todavia no ha comprado. HU-015.
 *
 * <p><strong>No se guarda: se arma al leer.</strong> Un carrito es «las filas de esta
 * persona», como los favoritos, y por eso no hay tabla {@code carts}. Una tabla de cabecera
 * no guardaria ni un dato que no se deduzca de las filas, y obligaria a crearla antes de la
 * primera.
 *
 * <p><strong>Y se arma en un solo sitio, que es toda la razon de que esta clase exista.</strong>
 * El carrito se lee por dos caminos —el de quien tiene sesion, contra la base, y el de quien
 * no la tiene, con los identificadores que trae su navegador— y los dos tienen que dar la
 * misma forma, los mismos grupos y los mismos subtotales. Con la suma escrita dos veces, una
 * en Java y otra en el navegador, divergir es cuestion de tiempo.
 *
 * <p>El carrito no reserva nada (RN-089), no tiene estados y no tiene transiciones: un
 * producto esta o no esta. La disponibilidad no es un estado suyo sino una lectura del
 * estado de la publicacion en el momento de mirar, y de ahi que el criterio 24 —vuelve a
 * publicarse, vuelve a contar— no escriba nada.
 */
public record Cart(List<CartGroup> grupos) {

    /**
     * RN-097: cuantos productos caben.
     *
     * <p><strong>Hay tope, al reves que en los favoritos</strong>, donde RN-073 decidio a
     * proposito que no lo hubiera. La diferencia no es de gusto: el carrito sin sesion lo
     * arma el navegador y el servidor lo recibe entero al fusionar, asi que sin tope hay un
     * cuerpo de tamano arbitrario que alguien manda sin haber entrado.
     *
     * <p><strong>Por que veinte.</strong> Porque un carrito lleno cabe en una sola lectura:
     * el tope de un tramo de catalogo es cincuenta y su tamano por omision veinticuatro, de
     * modo que leer un carrito entero nunca necesita una segunda peticion ni paginar. Una
     * suma paginada no es una suma. Es ademas el mismo veinte de las dos colas del moderador,
     * asi que no estrena un numero en el proyecto, y esta muy por encima de cualquier
     * carrito real de productos unicos de segunda.
     *
     * <p>Vive en el dominio y no junto al tope de paginacion del catalogo, que esta en la
     * capa de aplicacion: esto es una regla de negocio y las reglas de negocio viven aqui.
     */
    public static final int MAXIMO_DE_PRODUCTOS = 20;

    public Cart {
        Objects.requireNonNull(grupos, "Los grupos son obligatorios");

        grupos = List.copyOf(grupos);
    }

    /**
     * Arma el carrito agrupando por vendedor. RN-090.
     *
     * <p><strong>El orden de los grupos lo decide la linea mas reciente de cada uno</strong>,
     * y dentro de cada grupo las lineas van de la mas reciente a la mas antigua. Es el mismo
     * criterio del gesto que ordena los favoritos: lo ultimo que hiciste, primero. Sin un
     * orden definido, dos lecturas seguidas del mismo carrito podrian barajar los grupos y
     * quien mira creeria que algo cambio.
     *
     * <p>Un carrito vacio es un carrito con cero grupos, no un nulo ni una ausencia. La
     * pantalla vacia del criterio 19 se pinta desde el, no desde un caso especial.
     */
    public static Cart de(List<CartLine> lineas) {
        Objects.requireNonNull(lineas, "Las lineas son obligatorias");

        List<CartLine> recientesPrimero = new ArrayList<>(lineas);
        recientesPrimero.sort(Comparator.comparing(CartLine::agregadoEn).reversed());

        // LinkedHashMap y no groupingBy: el orden de aparicion es el orden de los grupos, y
        // groupingBy entrega un HashMap cuyo orden depende del hash del identificador.
        Map<SellerId, List<CartLine>> porVendedor = new LinkedHashMap<>();
        for (CartLine linea : recientesPrimero) {
            porVendedor
                    .computeIfAbsent(linea.vendedor(), vendedor -> new ArrayList<>())
                    .add(linea);
        }

        List<CartGroup> grupos = new ArrayList<>();
        porVendedor.forEach((vendedor, suyas) -> grupos.add(new CartGroup(vendedor, suyas)));

        return new Cart(grupos);
    }

    /** Cuantos productos lleva, disponibles o no. Es lo que cuenta contra RN-097. */
    public int cuantos() {
        return grupos.stream().mapToInt(grupo -> grupo.lineas().size()).sum();
    }

    public boolean estaVacio() {
        return grupos.isEmpty();
    }

    /**
     * Si hay mas de un vendedor y por tanto habra mas de un pedido. Criterio 15.
     *
     * <p>Lo decide el dominio y no la plantilla porque es la lectura de RN-090, no un detalle
     * de maquetacion: la pantalla pregunta si tiene que avisar, no cuenta grupos por su
     * cuenta.
     */
    public boolean seDividira() {
        return grupos.size() > 1;
    }
}
