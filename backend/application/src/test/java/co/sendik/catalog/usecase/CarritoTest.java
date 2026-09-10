package co.sendik.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.dto.CartCommand;
import co.sendik.catalog.dto.CartItemState;
import co.sendik.catalog.dto.CartView;
import co.sendik.catalog.dto.MergeCartCommand;
import co.sendik.catalog.dto.MergeResult;
import co.sendik.catalog.exception.BuyerAccountClosedException;
import co.sendik.catalog.exception.CartFullException;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfCartForbiddenException;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartGroup;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El carrito. HU-015.
 *
 * <p>Lo que se prueba aqui es lo que deciden los casos de uso: que agregar es idempotente,
 * que las tres reglas rechazan —RN-068, RN-092 y RN-097—, que la lectura entrega
 * <strong>tambien</strong> lo no disponible, y que la fusion es union y no reemplazo. Que la
 * consulta ordene y agrupe bien contra PostgreSQL lo prueba {@code CartPersistenceTest}.
 *
 * <p>El reloj es fijo y la prueba lo mueve a mano: el orden de los grupos y el descarte del
 * criterio 10 dependen de la fecha en que entro cada producto, y con un reloj real dos
 * productos agregados seguidos tendrian fechas distintas por accidente.
 */
class CarritoTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final CategoryId CAMISAS = new CategoryId(UUID.randomUUID());
    private static final BuyerId ALGUIEN = new BuyerId(UUID.randomUUID());

    private CatalogoEnMemoria.Publicaciones publicaciones;
    private CatalogoEnMemoria.Carrito filas;
    private CatalogoEnMemoria.Cuentas cuentas;
    private CatalogoEnMemoria.Perfiles perfiles;

    private AddToCartUseCase agregar;
    private RemoveFromCartUseCase quitar;
    private ReadCartUseCase leer;
    private ReadCartItemStateUseCase estado;
    private PreviewCartUseCase anonimo;
    private MergeCartUseCase fusionar;
    private ExportCartUseCase exportar;
    private EraseCartUseCase borrar;

    private Instant reloj = AHORA;

    @BeforeEach
    void montar() {
        publicaciones = new CatalogoEnMemoria.Publicaciones();
        filas = new CatalogoEnMemoria.Carrito();
        cuentas = new CatalogoEnMemoria.Cuentas();
        perfiles = new CatalogoEnMemoria.Perfiles();
        reloj = AHORA;

        RelojMovible tiempo = new RelojMovible();
        leer = new ReadCartUseCase(filas, publicaciones, perfiles);
        agregar = new AddToCartUseCase(filas, publicaciones, cuentas, tiempo);
        quitar = new RemoveFromCartUseCase(filas);
        estado = new ReadCartItemStateUseCase(filas, publicaciones);
        anonimo = new PreviewCartUseCase(publicaciones, perfiles, tiempo);
        fusionar = new MergeCartUseCase(filas, publicaciones, cuentas, leer, tiempo);
        exportar = new ExportCartUseCase(filas);
        borrar = new EraseCartUseCase(filas);
    }

    @Nested
    class Agregar {

        @Test
        void deberia_guardar_el_producto_criterio_2() {
            Listing publicada = publicar();

            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));

            assertThat(filas.existe(ALGUIEN, publicada.id())).isTrue();
        }

        @Test
        void deberia_guardar_el_precio_con_el_que_entro() {
            Listing publicada = publicar();

            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));

            assertThat(exportar.execute(ALGUIEN))
                    .singleElement()
                    .extracting(CartItem::precioAlAgregar)
                    .isEqualTo(Money.dePesos(185_000));
        }

        /** Criterio 4: dos pestanas, un reintento. Ningun «si no existe, guarda». */
        @Test
        void deberia_ser_idempotente_criterio_4() {
            Listing publicada = publicar();
            CartCommand orden = new CartCommand(ALGUIEN, publicada.id());

            agregar.execute(orden);
            agregar.execute(orden);

            assertThat(exportar.execute(ALGUIEN)).hasSize(1);
        }

        /**
         * Y repetir no pisa el precio de entrada.
         *
         * <p>Si lo pisara, volver a pulsar sobre algo que subio de precio borraria justo el
         * aviso de que subio, que es el criterio 18.
         */
        @Test
        void no_deberia_actualizar_el_precio_de_entrada_al_repetir() {
            Listing publicada = publicar();
            CartCommand orden = new CartCommand(ALGUIEN, publicada.id());
            agregar.execute(orden);

            publicaciones.guardar(publicada.cambiarPrecio(Money.dePesos(200_000), AHORA));
            agregar.execute(orden);

            assertThat(exportar.execute(ALGUIEN))
                    .singleElement()
                    .extracting(CartItem::precioAlAgregar)
                    .isEqualTo(Money.dePesos(185_000));
        }

        @Test
        void deberia_rechazar_la_publicacion_propia_RN_092() {
            Listing publicada = publicar();
            BuyerId elVendedor = new BuyerId(publicada.sellerId().value());

            assertThatThrownBy(() -> agregar.execute(new CartCommand(elVendedor, publicada.id())))
                    .isInstanceOf(SelfCartForbiddenException.class);
        }

        @Test
        void deberia_rechazar_lo_que_no_esta_publicado_RN_068() {
            Listing pausada = publicar();
            publicaciones.guardar(pausada.pausar(AHORA));

            assertThatThrownBy(() -> agregar.execute(new CartCommand(ALGUIEN, pausada.id())))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        @Test
        void deberia_rechazar_lo_que_no_existe() {
            assertThatThrownBy(() -> agregar.execute(new CartCommand(ALGUIEN, ListingId.nuevo())))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        /**
         * La cuenta cerrada con token vivo. ADR-0003: el token sobrevive quince minutos.
         *
         * <p>Sin esto quedaria carrito escrito justo despues de ejercer el derecho de
         * supresion, y nada volveria a borrarlo porque el cierre ya paso.
         */
        @Test
        void deberia_rechazar_a_una_cuenta_ya_cerrada() {
            Listing publicada = publicar();
            cuentas.cerrar(ALGUIEN);

            assertThatThrownBy(() -> agregar.execute(new CartCommand(ALGUIEN, publicada.id())))
                    .isInstanceOf(BuyerAccountClosedException.class);
        }
    }

    @Nested
    class ElTope {

        /** RN-097, criterio 7. Se comprueba en el servidor. */
        @Test
        void deberia_rechazar_el_producto_veintiuno() {
            llenarElCarrito();
            Listing unaMas = publicar();

            assertThatThrownBy(() -> agregar.execute(new CartCommand(ALGUIEN, unaMas.id())))
                    .isInstanceOf(CartFullException.class);
        }

        /** El borde, por los dos lados: veinte vale y veintiuno no. */
        @Test
        void deberia_admitir_el_producto_veinte() {
            for (int i = 0; i < Cart.MAXIMO_DE_PRODUCTOS - 1; i++) {
                agregar.execute(new CartCommand(ALGUIEN, publicar().id()));
            }
            Listing laVeinte = publicar();

            assertThatCode(() -> agregar.execute(new CartCommand(ALGUIEN, laVeinte.id())))
                    .doesNotThrowAnyException();
            assertThat(exportar.execute(ALGUIEN)).hasSize(Cart.MAXIMO_DE_PRODUCTOS);
        }

        /**
         * Un carrito lleno sigue aceptando el reintento de lo que ya tiene dentro.
         *
         * <p>Sin la condicion que lo permite, el tope romperia la idempotencia del criterio 4
         * justo en el borde donde mas se nota: dos pestanas sobre el mismo producto, con el
         * carrito lleno, y la segunda responde que no cabe algo que ya esta dentro.
         */
        @Test
        void no_deberia_rechazar_el_reintento_de_algo_que_ya_esta_dentro() {
            List<Listing> veinte = llenarElCarrito();
            CartCommand yaEsta = new CartCommand(ALGUIEN, veinte.getFirst().id());

            assertThatCode(() -> agregar.execute(yaEsta)).doesNotThrowAnyException();
            assertThat(exportar.execute(ALGUIEN)).hasSize(Cart.MAXIMO_DE_PRODUCTOS);
        }

        @Test
        void no_deberia_contar_el_carrito_de_otra_persona() {
            llenarElCarrito();
            BuyerId otra = new BuyerId(UUID.randomUUID());
            Listing suya = publicar();

            assertThatCode(() -> agregar.execute(new CartCommand(otra, suya.id())))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Quitar {

        @Test
        void deberia_quitar_el_producto_criterio_3() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));

            quitar.execute(new CartCommand(ALGUIEN, publicada.id()));

            assertThat(filas.existe(ALGUIEN, publicada.id())).isFalse();
        }

        @Test
        void deberia_ser_idempotente() {
            Listing publicada = publicar();

            assertThatCode(() -> quitar.execute(new CartCommand(ALGUIEN, publicada.id())))
                    .doesNotThrowAnyException();
        }

        /**
         * Lo no disponible tiene que poder sacarse.
         *
         * <p>RN-094 lo conserva a la vista; si ademas no se pudiera quitar, el carrito
         * acumularia basura permanente.
         */
        @Test
        void deberia_poder_quitar_lo_que_dejo_de_estar_disponible() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.archivar(AHORA));

            quitar.execute(new CartCommand(ALGUIEN, publicada.id()));

            assertThat(exportar.execute(ALGUIEN)).isEmpty();
        }
    }

    @Nested
    class Leer {

        /** RN-090, criterio 14. */
        @Test
        void deberia_agrupar_por_vendedor() {
            SellerId una = new SellerId(UUID.randomUUID());
            SellerId otra = new SellerId(UUID.randomUUID());
            agregar.execute(new CartCommand(ALGUIEN, publicarDe(una, 100_000).id()));
            avanzar(Duration.ofMinutes(1));
            agregar.execute(new CartCommand(ALGUIEN, publicarDe(otra, 50_000).id()));

            CartView carrito = leer.execute(ALGUIEN);

            assertThat(carrito.grupos()).hasSize(2);
            assertThat(carrito.carrito().seDividira()).isTrue();
        }

        /**
         * Criterio 21: lo no disponible sigue a la vista y fuera del subtotal.
         *
         * <p>Es lo contrario de lo que hace la lista de favoritos, y por eso se prueba en
         * positivo: si el puerto filtrara por estado, esta prueba caeria.
         */
        @Test
        void deberia_traer_tambien_lo_que_dejo_de_estar_disponible_criterio_21() {
            SellerId vendedor = new SellerId(UUID.randomUUID());
            Listing sigue = publicarDe(vendedor, 100_000);
            Listing seVendio = publicarDe(vendedor, 900_000);
            agregar.execute(new CartCommand(ALGUIEN, sigue.id()));
            avanzar(Duration.ofMinutes(1));
            agregar.execute(new CartCommand(ALGUIEN, seVendio.id()));
            publicaciones.guardar(seVendio.archivar(AHORA));

            CartGroup grupo = leer.execute(ALGUIEN).grupos().getFirst();

            assertThat(grupo.lineas()).hasSize(2);
            assertThat(grupo.subtotal()).isEqualTo(Money.dePesos(100_000));
        }

        /** Criterio 17 y 18: manda el precio vigente, y se avisa de que cambio. */
        @Test
        void deberia_usar_el_precio_vigente_y_avisar_del_cambio() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.cambiarPrecio(Money.dePesos(200_000), AHORA));

            CartLine linea = leer.execute(ALGUIEN).grupos().getFirst().lineas().getFirst();

            assertThat(linea.precio()).isEqualTo(Money.dePesos(200_000));
            assertThat(linea.cambioDePrecio()).isTrue();
        }

        @Test
        void deberia_devolver_un_carrito_vacio_sin_consultar_publicaciones() {
            assertThat(leer.execute(ALGUIEN).estaVacio()).isTrue();
        }

        /** Criterio 24: vuelve a publicarse, vuelve a contar, sin escribir nada. */
        @Test
        void deberia_volver_a_contar_lo_que_su_vendedor_reanuda() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.pausar(AHORA));

            assertThat(leer.execute(ALGUIEN).grupos().getFirst().subtotal()).isEqualTo(Money.dePesos(0));

            publicaciones.guardar(publicada.pausar(AHORA).reanudar(AHORA));

            assertThat(leer.execute(ALGUIEN).grupos().getFirst().subtotal()).isEqualTo(Money.dePesos(185_000));
        }

        @Test
        void no_deberia_ver_el_carrito_de_otra_persona() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));

            assertThat(leer.execute(new BuyerId(UUID.randomUUID())).estaVacio()).isTrue();
        }
    }

    @Nested
    class ElEstadoDelControl {

        @Test
        void deberia_decir_que_esta_dentro_y_que_se_puede() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));

            CartItemState respuesta = estado.execute(new CartCommand(ALGUIEN, publicada.id()));

            assertThat(respuesta).isEqualTo(new CartItemState(true, true));
        }

        /** Criterio 5: sobre lo propio el control no se ofrece. */
        @Test
        void deberia_decir_que_no_es_elegible_lo_propio() {
            Listing publicada = publicar();
            BuyerId elVendedor = new BuyerId(publicada.sellerId().value());

            assertThat(estado.execute(new CartCommand(elVendedor, publicada.id())))
                    .isEqualTo(new CartItemState(false, false));
        }

        /** No falla por no encontrarla: la pantalla necesita una respuesta, no un error. */
        @Test
        void no_deberia_fallar_sobre_algo_que_ya_no_se_ve() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.archivar(AHORA));

            assertThat(estado.execute(new CartCommand(ALGUIEN, publicada.id())))
                    .isEqualTo(new CartItemState(true, false));
        }
    }

    @Nested
    class SinSesion {

        /** Criterio 13: los mismos grupos y los mismos subtotales, sin haber entrado. */
        @Test
        void deberia_armar_los_mismos_grupos_y_subtotales() {
            SellerId una = new SellerId(UUID.randomUUID());
            SellerId otra = new SellerId(UUID.randomUUID());
            Listing primera = publicarDe(una, 100_000);
            Listing segunda = publicarDe(una, 30_000);
            Listing tercera = publicarDe(otra, 50_000);

            CartView carrito = anonimo.execute(List.of(primera.id(), segunda.id(), tercera.id()));

            assertThat(carrito.grupos()).hasSize(2);
            assertThat(carrito.cuantos()).isEqualTo(3);
            assertThat(carrito.grupos().getFirst().vendedor()).isEqualTo(una);
            assertThat(carrito.grupos().getFirst().subtotal()).isEqualTo(Money.dePesos(130_000));
        }

        /** El orden que trae el navegador se respeta. */
        @Test
        void deberia_conservar_el_orden_de_la_lista() {
            SellerId una = new SellerId(UUID.randomUUID());
            SellerId otra = new SellerId(UUID.randomUUID());
            Listing deOtra = publicarDe(otra, 50_000);
            Listing deUna = publicarDe(una, 100_000);

            CartView carrito = anonimo.execute(List.of(deOtra.id(), deUna.id()));

            assertThat(carrito.grupos().getFirst().vendedor()).isEqualTo(otra);
        }

        /**
         * RN-068: a quien no ha entrado no se le dice que un identificador existe pero no
         * esta publicado. Lo que no esta se cae de la respuesta.
         */
        @Test
        void no_deberia_devolver_lo_que_no_esta_publicado() {
            Listing publicada = publicar();
            Listing pausada = publicar();
            publicaciones.guardar(pausada.pausar(AHORA));

            CartView carrito = anonimo.execute(List.of(publicada.id(), pausada.id()));

            assertThat(carrito.cuantos()).isEqualTo(1);
        }

        @Test
        void deberia_descartar_sin_error_un_identificador_inventado() {
            Listing publicada = publicar();

            CartView carrito = anonimo.execute(List.of(ListingId.nuevo(), publicada.id()));

            assertThat(carrito.cuantos()).isEqualTo(1);
        }

        @Test
        void deberia_devolver_vacio_sin_identificadores() {
            assertThat(anonimo.execute(List.of()).estaVacio()).isTrue();
        }
    }

    @Nested
    class LaFusion {

        /** RN-095, criterio 9: union y no reemplazo. */
        @Test
        void deberia_unir_sin_perder_ninguno_de_los_dos_lados() {
            Listing yaTenia = publicar();
            Listing traiaElNavegador = publicar();
            agregar.execute(new CartCommand(ALGUIEN, yaTenia.id()));

            MergeResult resultado = fusionar.execute(new MergeCartCommand(ALGUIEN, List.of(traiaElNavegador.id())));

            assertThat(resultado.carrito().cuantos()).isEqualTo(2);
            assertThat(resultado.noEntraron()).isEmpty();
        }

        @Test
        void deberia_poder_repetirse_sin_dano() {
            Listing publicada = publicar();
            MergeCartCommand orden = new MergeCartCommand(ALGUIEN, List.of(publicada.id()));

            fusionar.execute(orden);
            MergeResult segunda = fusionar.execute(orden);

            assertThat(segunda.carrito().cuantos()).isEqualTo(1);
        }

        /**
         * RN-092 se aplica aqui por primera vez a esas filas.
         *
         * <p>Mientras el carrito fue anonimo no habia contra quien comparar, asi que lo propio
         * pudo entrar en el. El ingreso es el momento en que aparece alguien con nombre.
         */
        @Test
        void deberia_descartar_lo_propio_RN_092() {
            Listing publicada = publicar();
            BuyerId elVendedor = new BuyerId(publicada.sellerId().value());

            MergeResult resultado = fusionar.execute(new MergeCartCommand(elVendedor, List.of(publicada.id())));

            assertThat(resultado.carrito().estaVacio()).isTrue();
            assertThat(resultado.noEntraron()).containsExactly(publicada.id());
        }

        /** No falla entera por una linea mala: se descarta y se anota. */
        @Test
        void deberia_descartar_lo_que_ya_no_esta_sin_rechazar_la_peticion() {
            Listing buena = publicar();
            Listing archivada = publicar();
            publicaciones.guardar(archivada.archivar(AHORA));

            MergeResult resultado =
                    fusionar.execute(new MergeCartCommand(ALGUIEN, List.of(buena.id(), archivada.id())));

            assertThat(resultado.carrito().cuantos()).isEqualTo(1);
            assertThat(resultado.noEntraron()).containsExactly(archivada.id());
        }

        /** Criterio 10: se conserva hasta el tope, lo mas reciente primero, y se dice que no cupo. */
        @Test
        void deberia_respetar_el_tope_conservando_lo_mas_reciente() {
            for (int i = 0; i < Cart.MAXIMO_DE_PRODUCTOS - 1; i++) {
                agregar.execute(new CartCommand(ALGUIEN, publicar().id()));
                avanzar(Duration.ofMinutes(1));
            }
            Listing cabe = publicar();
            Listing noCabe = publicar();

            MergeResult resultado = fusionar.execute(new MergeCartCommand(ALGUIEN, List.of(cabe.id(), noCabe.id())));

            assertThat(resultado.carrito().cuantos()).isEqualTo(Cart.MAXIMO_DE_PRODUCTOS);
            assertThat(resultado.noEntraron()).containsExactly(noCabe.id());
            assertThat(filas.existe(ALGUIEN, cabe.id())).isTrue();
            assertThat(filas.existe(ALGUIEN, noCabe.id())).isFalse();
        }

        /**
         * Lo que ya estaba en la cuenta no se desplaza por lo que trae el navegador.
         *
         * <p>Quien entra no puede perder lo que ya habia guardado, y por eso el tope se
         * calcula sobre lo libre y no se reparte entre los dos lados.
         */
        @Test
        void no_deberia_desalojar_lo_que_ya_estaba_guardado() {
            List<Listing> veinte = llenarElCarrito();
            Listing traiaElNavegador = publicar();

            MergeResult resultado = fusionar.execute(new MergeCartCommand(ALGUIEN, List.of(traiaElNavegador.id())));

            assertThat(resultado.noEntraron()).containsExactly(traiaElNavegador.id());
            assertThat(filas.existe(ALGUIEN, veinte.getFirst().id())).isTrue();
        }

        /** Lo que ya esta en la cuenta no consume hueco al fusionarse consigo mismo. */
        @Test
        void no_deberia_gastar_hueco_con_lo_que_ya_estaba() {
            List<Listing> veinte = llenarElCarrito();

            MergeResult resultado = fusionar.execute(
                    new MergeCartCommand(ALGUIEN, List.of(veinte.getFirst().id())));

            assertThat(resultado.noEntraron()).isEmpty();
            assertThat(resultado.carrito().cuantos()).isEqualTo(Cart.MAXIMO_DE_PRODUCTOS);
        }

        @Test
        void deberia_devolver_el_carrito_tal_cual_con_una_lista_vacia() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));

            MergeResult resultado = fusionar.execute(new MergeCartCommand(ALGUIEN, List.of()));

            assertThat(resultado.carrito().cuantos()).isEqualTo(1);
            assertThat(resultado.noEntraron()).isEmpty();
        }

        @Test
        void deberia_rechazar_a_una_cuenta_ya_cerrada() {
            Listing publicada = publicar();
            cuentas.cerrar(ALGUIEN);

            assertThatThrownBy(() -> fusionar.execute(new MergeCartCommand(ALGUIEN, List.of(publicada.id()))))
                    .isInstanceOf(BuyerAccountClosedException.class);
        }
    }

    @Nested
    class DatosPersonales {

        @Test
        void deberia_exportar_tambien_lo_que_ya_no_esta_disponible() {
            Listing publicada = publicar();
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.archivar(AHORA));

            assertThat(exportar.execute(ALGUIEN))
                    .singleElement()
                    .extracting(CartItem::publicacion)
                    .isEqualTo(publicada.id());
        }

        @Test
        void deberia_borrar_el_de_esa_persona_al_cerrar_la_cuenta() {
            Listing publicada = publicar();
            BuyerId otra = new BuyerId(UUID.randomUUID());
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            agregar.execute(new CartCommand(otra, publicada.id()));

            borrar.execute(ALGUIEN);

            assertThat(exportar.execute(ALGUIEN)).isEmpty();
            assertThat(exportar.execute(otra)).hasSize(1);
        }

        @Test
        void no_deberia_fallar_al_borrar_un_carrito_vacio() {
            assertThatCode(() -> borrar.execute(ALGUIEN)).doesNotThrowAnyException();
        }
    }

    // ------------------------------------------------------------- datos

    private void avanzar(Duration cuanto) {
        reloj = reloj.plus(cuanto);
    }

    private List<Listing> llenarElCarrito() {
        List<Listing> veinte = new ArrayList<>();
        for (int i = 0; i < Cart.MAXIMO_DE_PRODUCTOS; i++) {
            Listing publicada = publicar();
            veinte.add(publicada);
            agregar.execute(new CartCommand(ALGUIEN, publicada.id()));
            avanzar(Duration.ofMinutes(1));
        }
        return veinte;
    }

    private Listing publicar() {
        return publicarDe(new SellerId(UUID.randomUUID()), 185_000);
    }

    private Listing publicarDe(SellerId vendedor, long pesos) {
        Listing aprobada = conTomas(borrador(vendedor, Money.dePesos(pesos)))
                .enviarARevision(AHORA)
                .aprobar(new ModeratorId(UUID.randomUUID()), AHORA);

        return publicaciones.guardar(aprobada);
    }

    private static Listing borrador(SellerId vendedor, Money precio) {
        Map<MeasurementKind, BigDecimal> medidas = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> medidas.put(medida, new BigDecimal("50.0")));

        Product producto = new Product(
                ProductId.nuevo(),
                vendedor,
                CAMISAS,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(medidas),
                Color.BEIGE,
                precio,
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    private static Listing conTomas(Listing publicacion) {
        Listing resultado = publicacion;
        for (int i = 0; i < publicacion.tomasExigidas(); i++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                            i,
                            new ImageDimensions(1200, 1600),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    /** Un reloj que la prueba mueve a mano, para fijar el orden de lo que se agrega. */
    private final class RelojMovible extends Clock {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return reloj;
        }
    }
}
