package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.dto.SearchCriteria;
import co.sendik.catalog.dto.SearchHit;
import co.sendik.catalog.exception.ListingConcurrentlyModifiedException;
import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.PriceRange;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SearchText;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.persistence.PostgresSearchEngine;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.catalog.port.out.SearchEngine;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los adaptadores de persistencia del catalogo, contra PostgreSQL 17 real. HU-007.
 *
 * <p>Lo que se prueba aqui y no se puede probar en otro sitio: que el agregado sale de
 * la base tal como entro —incluidas las medidas, que viajan en {@code jsonb}—, que las
 * imagenes se reescriben sin duplicarse, y que el bloqueo optimista del criterio 34
 * hace lo que dice cuando dos escrituras chocan de verdad.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CatalogPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T15:00:00Z");

    private final ListingRepository publicaciones;
    private final SearchEngine motor;
    private final Categories categorias;
    private final ModerationLog bitacora;
    private final JdbcClient jdbc;

    CatalogPersistenceTest(
            ListingRepository publicaciones,
            SearchEngine motor,
            Categories categorias,
            ModerationLog bitacora,
            JdbcClient jdbc) {
        this.publicaciones = publicaciones;
        this.motor = motor;
        this.categorias = categorias;
        this.bitacora = bitacora;
        this.jdbc = jdbc;
    }

    @Test
    void deberia_leer_del_arbol_sembrado_una_categoria_de_moda() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        assertThat(camisas.allowsUsed()).isTrue();
        assertThat(camisas.measurementGroup()).isEqualTo(MeasurementGroup.TOP);
        assertThat(camisas.sizeSystems()).contains(SizeSystem.ALPHA);
        assertThat(camisas.admitePublicaciones()).isTrue();
    }

    @Test
    void deberia_leer_la_tecnologia_sin_permitir_lo_usado_RN_064() {
        Category celulares = categoriaPorSlug("celulares-y-tabletas");

        assertThat(celulares.allowsUsed()).isFalse();
        assertThat(celulares.condicionesAdmisibles()).containsExactly(Condition.NEW);
        assertThat(celulares.measurementGroup()).isEqualTo(MeasurementGroup.DEVICE);
    }

    @Test
    void deberia_leer_los_dos_sistemas_de_talla_de_los_jeans() {
        Category jeans = categoriaPorSlug("jeans");

        assertThat(jeans.sizeSystems()).containsExactlyInAnyOrder(SizeSystem.WAIST_INCHES, SizeSystem.NUMERIC_CO);
    }

    // Ida y vuelta completa: si algo se pierde en el mapeo, se pierde aqui.
    @Test
    void deberia_devolver_el_agregado_tal_como_entro() {
        Listing guardada = publicaciones.guardar(borradorConTomas());

        Listing leida = publicaciones.buscar(guardada.id()).orElseThrow();

        assertThat(leida.status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(leida.sellerId()).isEqualTo(guardada.sellerId());
        assertThat(leida.product().title()).isEqualTo(guardada.product().title());
        assertThat(leida.product().price()).isEqualTo(Money.dePesos(185_000));
        assertThat(leida.tomasDelVendedor()).hasSize(8);
    }

    /**
     * El borrador recien nacido, con la categoria y nada mas. Criterio 5.
     *
     * <p>Es como empieza toda publicacion: el formulario pide la categoria, pulsa
     * «Empezar» y manda {@code {"categoryId": ...}} sin un solo campo mas. Ninguna
     * prueba lo cubria —todas creaban el producto completo— y contra PostgreSQL de
     * verdad no funcionaba: doce columnas {@code NOT NULL} en V9 y un repositorio que
     * desreferenciaba lo anulable sin guarda. La pantalla de publicar respondia 500 en
     * su primera peticion.
     */
    @Test
    void deberia_guardar_y_releer_un_borrador_con_solo_la_categoria_criterio_5() {
        Listing vacio = borradorVacio();

        Listing guardada = publicaciones.guardar(vacio);
        Listing leida = publicaciones.buscar(guardada.id()).orElseThrow();

        assertThat(leida.status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(leida.product().categoryId()).isEqualTo(vacio.product().categoryId());
        assertThat(leida.product().title()).isNull();
        assertThat(leida.product().description()).isNull();
        assertThat(leida.product().brand()).isNull();
        assertThat(leida.product().condition()).isNull();
        assertThat(leida.product().size()).isNull();
        assertThat(leida.product().color()).isNull();
        assertThat(leida.product().price()).isNull();
        assertThat(leida.product().shipping()).isNull();
        assertThat(leida.product().measurements().valores()).isEmpty();
        assertThat(leida.images()).isEmpty();
    }

    /**
     * «Salir a la mitad y volver retoma donde iba», que es la otra frase del criterio 5.
     *
     * <p>Va aparte de la anterior porque ejercita la otra rama del guardado: la fila ya
     * existe, asi que entra por el {@code ON CONFLICT DO UPDATE}, y lo que se comprueba
     * es que las columnas pasan de nulo a valor sin dejar nada por el camino.
     */
    @Test
    void deberia_completar_despues_el_borrador_que_nacio_vacio_criterio_5() {
        Listing vacio = publicaciones.guardar(borradorVacio());

        Listing completado = publicaciones.guardar(vacio.editarContenido(
                Product.crear(
                        vacio.product().id(),
                        vacio.product().sellerId(),
                        categoriaPorSlug("camisas-y-blusas"),
                        new Title("Camisa de lino color hueso"),
                        new Description("Usada dos veces."),
                        new Brand("Zara"),
                        Condition.LIKE_NEW,
                        new Size(SizeSystem.ALPHA, "M"),
                        medidasDe(MeasurementGroup.TOP),
                        Color.BEIGE,
                        Money.dePesos(185_000),
                        envio(),
                        null,
                        null),
                AHORA));

        Product leido = publicaciones.buscar(completado.id()).orElseThrow().product();

        assertThat(leido.title()).isEqualTo(new Title("Camisa de lino color hueso"));
        assertThat(leido.condition()).isEqualTo(Condition.LIKE_NEW);
        assertThat(leido.size()).isEqualTo(new Size(SizeSystem.ALPHA, "M"));
        assertThat(leido.color()).isEqualTo(Color.BEIGE);
        assertThat(leido.price()).isEqualTo(Money.dePesos(185_000));
        assertThat(leido.shipping()).isEqualTo(envio());
        assertThat(leido.measurements().valores()).isNotEmpty();
    }

    // Las medidas viajan en jsonb. Un double por el camino las estropearia.
    @Test
    void deberia_conservar_las_medidas_con_su_decimal_exacto_RN_021() {
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        valores.put(MeasurementKind.CHEST, new BigDecimal("52.5"));
        valores.put(MeasurementKind.LENGTH, new BigDecimal("70.0"));
        valores.put(MeasurementKind.SHOULDERS, new BigDecimal("41"));
        valores.put(MeasurementKind.SLEEVE, new BigDecimal("60.5"));

        Listing guardada = publicaciones.guardar(borradorDe(new Measurements(valores)));

        Measurements leidas =
                publicaciones.buscar(guardada.id()).orElseThrow().product().measurements();

        assertThat(leidas.valores().get(MeasurementKind.CHEST)).isEqualByComparingTo("52.5");
        assertThat(leidas.valores().get(MeasurementKind.SLEEVE)).isEqualByComparingTo("60.5");
        assertThat(leidas.valores()).hasSize(4);
    }

    @Test
    void deberia_reescribir_las_imagenes_sin_duplicarlas() {
        Listing conOcho = publicaciones.guardar(borradorConTomas());

        Listing conSieteYUnaNueva =
                conOcho.sinImagen(conOcho.images().getFirst().id(), AHORA);
        publicaciones.guardar(conSieteYUnaNueva);

        Listing leida = publicaciones.buscar(conOcho.id()).orElseThrow();
        assertThat(leida.tomasDelVendedor()).hasSize(7);

        long filas = jdbc.sql("SELECT count(*) FROM product_images WHERE product_id = :p")
                .param("p", conOcho.product().id().value())
                .query(Long.class)
                .single();
        assertThat(filas).isEqualTo(7);
    }

    @Test
    void deberia_guardar_una_imagen_de_referencia_junto_a_las_tomas_RN_066() {
        Listing sellado = publicaciones.guardar(selladoConCuatroTomasYReferencia());

        Listing leida = publicaciones.buscar(sellado.id()).orElseThrow();

        assertThat(leida.tomasDelVendedor()).hasSize(4);
        assertThat(leida.imagenesDeReferencia()).hasSize(1);
        assertThat(leida.imagenesDeReferencia().getFirst().kind()).isEqualTo(ImageKind.REFERENCE);
        assertThat(leida.imagenesDeReferencia().getFirst().angleDegrees()).isNull();
    }

    // Criterio 34: el vendedor edita mientras el moderador decide.
    @Test
    void deberia_rechazar_la_segunda_escritura_concurrente_criterio_34() {
        Listing guardada = publicaciones.guardar(borradorConTomas());

        Listing copiaDelVendedor = publicaciones.buscar(guardada.id()).orElseThrow();
        Listing copiaDelModerador = publicaciones.buscar(guardada.id()).orElseThrow();
        assertThat(copiaDelVendedor.version()).isEqualTo(copiaDelModerador.version());

        publicaciones.guardar(copiaDelVendedor.enviarARevision(AHORA));

        assertThatThrownBy(() -> publicaciones.guardar(copiaDelModerador.archivar(AHORA)))
                .isInstanceOf(ListingConcurrentlyModifiedException.class);

        // Y lo que de verdad importa: la decision del primero sigue en pie.
        assertThat(publicaciones.buscar(guardada.id()).orElseThrow().status()).isEqualTo(ListingStatus.PENDING_REVIEW);
    }

    // El escenario que destapo la revision de seguridad: el moderador retira una replica
    // y el vendedor, con la ficha abierta, cambia el precio y deshace la retirada.
    @Test
    void deberia_impedir_que_un_cambio_de_precio_deshaga_una_retirada_criterio_34() {
        Listing publicada = publicada();
        Listing copiaDelVendedor = publicaciones.buscar(publicada.id()).orElseThrow();

        publicaciones.guardar(publicaciones.buscar(publicada.id()).orElseThrow().archivar(AHORA));

        assertThatThrownBy(() -> publicaciones.guardar(copiaDelVendedor.cambiarPrecio(Money.dePesos(120_000), AHORA)))
                .isInstanceOf(ListingConcurrentlyModifiedException.class);

        assertThat(publicaciones.buscar(publicada.id()).orElseThrow().status()).isEqualTo(ListingStatus.ARCHIVED);
    }

    @Test
    void deberia_subir_la_version_en_cada_guardado() {
        Listing guardada = publicaciones.guardar(borradorConTomas());
        assertThat(guardada.version()).isZero();

        Listing enRevision = publicaciones.guardar(guardada.enviarARevision(AHORA));

        assertThat(enRevision.version()).isEqualTo(1L);
        assertThat(publicaciones.buscar(guardada.id()).orElseThrow().version()).isEqualTo(1L);
    }

    // V10: las dos marcas conviven en la columna de arreglo.
    @Test
    void deberia_guardar_las_dos_marcas_de_atencion_criterios_12_y_18() {
        Listing marcada = publicaciones.guardar(borradorDe(medidasDe(MeasurementGroup.TOP), Money.dePesos(9_000))
                .marcarCargaDesdeGaleria(AHORA));

        Listing leida = publicaciones.buscar(marcada.id()).orElseThrow();

        assertThat(leida.attentionReasons())
                .containsExactlyInAnyOrder(AttentionReason.PRICE_OUT_OF_RANGE, AttentionReason.GALLERY_UPLOAD);
    }

    // HU-012: las cifras del panel. Contra PostgreSQL porque el vendedor vive en
    // `products` y el estado en `listings`: la union es justo lo que una prueba en memoria
    // no puede equivocarse en comprobar.
    @Test
    void deberia_contar_por_estado_solo_lo_del_vendedor_que_pregunta_HU_012() {
        SellerId una = new SellerId(nuevoUsuario());
        SellerId otra = new SellerId(nuevoUsuario());

        publicaciones.guardar(borradorDeVendedor(una));
        publicaciones.guardar(borradorDeVendedor(una));
        publicadaDe(una, AHORA);
        publicaciones.guardar(borradorDeVendedor(otra));

        var suyas = publicaciones.contarPorEstadoDelVendedor(una);

        assertThat(suyas).hasSize(2).containsEntry(ListingStatus.DRAFT, 2L).containsEntry(ListingStatus.PUBLISHED, 1L);
        assertThat(publicaciones.contarPorEstadoDelVendedor(otra))
                .as("las de una no pueden contarse en el panel de la otra")
                .hasSize(1)
                .containsEntry(ListingStatus.DRAFT, 1L);
    }

    /**
     * Los seis estados alcanzables hacen el viaje de ida y vuelta por la columna de texto.
     *
     * <p><strong>Es el unico sitio donde se puede comprobar.</strong>
     * {@code ListingStatus.valueOf(fila.getString("status"))} convierte texto a enumeracion, y
     * eso depende de que los literales de la restriccion {@code listings_status_valid} sean
     * exactamente los de {@link ListingStatus}. Son dos archivos que nadie ata, y con solo
     * {@code DRAFT} y {@code PUBLISHED} recorridos, cinco de los siete nunca hacian ese viaje:
     * una migracion que escribiera otro literal reventaria en produccion con todo en verde.
     *
     * <p>{@code SOLD} queda fuera porque no hay forma de llegar: lo provoca el pago aprobado
     * (RN-035) y eso es Fase 3. El dia que exista, esta prueba lo pide.
     */
    @Test
    void deberia_contar_los_seis_estados_alcanzables_HU_012() {
        SellerId vendedora = new SellerId(nuevoUsuario());
        ModeratorId moderadora = new ModeratorId(nuevoUsuario());

        publicaciones.guardar(borradorDeVendedor(vendedora));
        publicaciones.guardar(conTomas(borradorDeVendedor(vendedora), 8).enviarARevision(AHORA));
        publicadaDe(vendedora, AHORA);
        publicaciones.guardar(conTomas(borradorDeVendedor(vendedora), 8)
                .enviarARevision(AHORA)
                .rechazar(moderadora, ListingRejectionReason.PHOTOS_UNUSABLE, "Las tomas salen movidas.", AHORA));
        publicaciones.guardar(publicadaDe(vendedora, AHORA).pausar(AHORA));
        publicaciones.guardar(borradorDeVendedor(vendedora).archivar(AHORA));

        assertThat(publicaciones.contarPorEstadoDelVendedor(vendedora))
                .containsEntry(ListingStatus.DRAFT, 1L)
                .containsEntry(ListingStatus.PENDING_REVIEW, 1L)
                .containsEntry(ListingStatus.PUBLISHED, 1L)
                .containsEntry(ListingStatus.REJECTED, 1L)
                .containsEntry(ListingStatus.PAUSED, 1L)
                .containsEntry(ListingStatus.ARCHIVED, 1L)
                .as("SOLD no es alcanzable hasta que haya pagos")
                .doesNotContainKey(ListingStatus.SOLD);
    }

    /**
     * El {@code GROUP BY} <strong>no inventa los estados vacios</strong>, y eso se fija
     * aqui a proposito.
     *
     * <p>Completar los siete de RN-061 con cero es de {@code SummarizeSellerListingsUseCase}.
     * Si algun dia esta consulta empezara a devolverlos, la prueba del caso de uso seguiria
     * pasando sin que el caso de uso hiciera nada y nadie se enteraria de que la decision
     * se mudo de capa.
     */
    @Test
    void deberia_devolver_el_conteo_vacio_de_quien_no_tiene_ninguna_HU_012() {
        assertThat(publicaciones.contarPorEstadoDelVendedor(new SellerId(nuevoUsuario())))
                .isEmpty();
    }

    // V12: la cola del moderador, contra el indice parcial y la base de verdad.
    @Test
    void deberia_devolver_en_la_cola_solo_lo_que_espera_revision_HU_008() {
        Listing esperando = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        publicaciones.guardar(borradorConTomas());
        publicada();

        var cola = publicaciones.pendientesDeRevision(0, 50);

        assertThat(cola)
                .as("la cola no puede traer nada que no este esperando")
                .allSatisfy(publicacion -> assertThat(publicacion.status()).isEqualTo(ListingStatus.PENDING_REVIEW));
        assertThat(cola).extracting(Listing::id).contains(esperando.id());
    }

    /**
     * <strong>Pedir mas filas no puede mover el arranque.</strong>
     *
     * <p>La firma de esta cola era {@code (pagina, tamano)} y el adaptador derivaba el
     * desplazamiento del mismo argumento que el limite. Con esa forma, el dia que alguien
     * pidiera una fila de mas -para saber si hay pagina siguiente sin contar la tabla- el
     * salto se iria con ella y una publicacion por pagina dejaria de salir, en silencio.
     * A la cola de verificaciones le paso exactamente eso en cuanto quiso su {@code
     * hasMore}; aqui la prueba llega antes que el defecto.
     */
    @Test
    void deberia_empezar_donde_dice_el_salto_aunque_se_pidan_mas_filas_HU_008() {
        Instant temprano = AHORA.minus(Duration.ofHours(6));

        for (int cuantas = 0; cuantas < 5; cuantas++) {
            publicaciones.guardar(borradorConTomas().enviarARevision(temprano.plusSeconds(cuantas)));
        }

        var desdeElPrincipio = publicaciones.pendientesDeRevision(0, 5).stream()
                .map(Listing::id)
                .toList();
        var saltandoUna = publicaciones.pendientesDeRevision(1, 4).stream()
                .map(Listing::id)
                .toList();

        assertThat(saltandoUna).isEqualTo(desdeElPrincipio.subList(1, 5));
    }

    /**
     * El desempate por {@code id}, que es lo que hace correcto el desplazamiento.
     *
     * <p>Las cuatro se envian en el <strong>mismo instante</strong>, que es justo el caso
     * que {@code submitted_at} sola no ordena. Sin desempate, dos peticiones de paginas
     * contiguas pueden traer la misma fila dos veces y dejarse otra sin traer. Se envian
     * con un ano de antiguedad para que ocupen las primeras posiciones de la cola pase lo
     * que pase con lo que dejen otras pruebas de esta clase.
     */
    @Test
    void deberia_paginar_sin_repetir_ni_perder_cuando_el_instante_coincide_HU_008() {
        Instant mismoInstante = AHORA.minus(Duration.ofDays(365));

        List<ListingId> enviadas = new ArrayList<>();
        for (int cuantas = 0; cuantas < 4; cuantas++) {
            enviadas.add(publicaciones
                    .guardar(borradorConTomas().enviarARevision(mismoInstante))
                    .id());
        }

        List<ListingId> primera = idsDeLaCola(0, 2);
        List<ListingId> segunda = idsDeLaCola(2, 2);

        assertThat(primera).as("una fila no puede salir en dos paginas").doesNotContainAnyElementsOf(segunda);
        assertThat(Stream.concat(primera.stream(), segunda.stream()).toList())
                .as("entre las dos paginas tienen que estar las cuatro")
                .containsExactlyInAnyOrderElementsOf(enviadas);
    }

    private List<ListingId> idsDeLaCola(long salto, int cuantas) {
        return publicaciones.pendientesDeRevision(salto, cuantas).stream()
                .map(Listing::id)
                .toList();
    }

    /**
     * {@code hayPendientesDesde} contesta lo mismo que contestaria traer una fila de mas,
     * sin traerla -ni resolver su portada, que es una consulta aparte.
     *
     * <p>Se mide relativo a lo que ya hay en la cola: el contenedor es uno para toda la
     * clase y otras pruebas dejan publicaciones esperando, asi que se cuenta primero y se
     * pregunta despues por las dos posiciones que importan.
     */
    @Test
    void deberia_decir_si_queda_alguna_pendiente_a_partir_de_una_posicion_HU_008() {
        publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));

        long cuantasHay = 0;
        while (!publicaciones.pendientesDeRevision(cuantasHay, 1).isEmpty()) {
            cuantasHay++;
        }

        assertThat(publicaciones.hayPendientesDesde(cuantasHay - 1))
                .as("en la ultima posicion ocupada todavia queda una")
                .isTrue();
        assertThat(publicaciones.hayPendientesDesde(cuantasHay))
                .as("pasada la ultima ya no queda ninguna")
                .isFalse();
        assertThat(publicaciones.hayPendientesDesde(0)).isTrue();
    }

    @Test
    void deberia_ordenar_la_cola_por_lo_que_lleva_mas_tiempo_esperando_HU_008() {
        Instant temprano = AHORA.minus(Duration.ofHours(6));
        Instant mediodia = AHORA.minus(Duration.ofHours(2));

        Listing tercera = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        Listing primera = publicaciones.guardar(borradorConTomas().enviarARevision(temprano));
        Listing segunda = publicaciones.guardar(borradorConTomas().enviarARevision(mediodia));

        var cola = publicaciones.pendientesDeRevision(0, 50);

        assertThat(cola).extracting(Listing::id).containsSubsequence(primera.id(), segunda.id(), tercera.id());
    }

    /** La razon de que exista la columna: con `updated_at`, esta prueba fallaria. */
    @Test
    void no_deberia_perder_el_turno_por_cambiar_el_precio_mientras_espera_HU_008() {
        Instant temprano = AHORA.minus(Duration.ofHours(6));

        Listing primera = publicaciones.guardar(borradorConTomas().enviarARevision(temprano));
        Listing segunda = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));

        publicaciones.guardar(publicaciones
                .buscar(primera.id())
                .orElseThrow()
                .cambiarPrecio(Money.dePesos(190_000), AHORA.plus(Duration.ofHours(1))));

        var cola = publicaciones.pendientesDeRevision(0, 50);

        assertThat(cola).extracting(Listing::id).containsSubsequence(primera.id(), segunda.id());
        assertThat(cola)
                .filteredOn(publicacion -> publicacion.id().equals(primera.id()))
                .singleElement()
                .satisfies(publicacion -> assertThat(publicacion.submittedAt()).isEqualTo(temprano));
    }

    /**
     * La cola trae la frontal y nada mas.
     *
     * <p>Antes traia las ocho de cada fila —una consulta por publicacion— para pintar una
     * miniatura. Esta prueba fija las dos mitades: que la frontal esta, y que las otras
     * siete no viajan.
     */
    @Test
    void deberia_traer_solo_la_toma_frontal_en_la_cola_HU_008() {
        Listing esperando = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));

        Listing enLaCola = publicaciones.pendientesDeRevision(0, 50).stream()
                .filter(p -> p.id().equals(esperando.id()))
                .findFirst()
                .orElseThrow();

        assertThat(enLaCola.images()).hasSize(1);
        assertThat(enLaCola.images().getFirst().position()).isZero();
    }

    @Test
    void deberia_conservar_motivo_y_nota_de_un_rechazo_RN_022() {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        publicaciones.guardar(
                enRevision.rechazar(moderador, ListingRejectionReason.PHOTOS_UNUSABLE, "Frontal borrosa", AHORA));

        Listing leida = publicaciones.buscar(enRevision.id()).orElseThrow();
        assertThat(leida.rejectionReason()).isEqualTo(ListingRejectionReason.PHOTOS_UNUSABLE);
        assertThat(leida.rejectionNote()).isEqualTo("Frontal borrosa");
        assertThat(leida.moderatedBy()).isEqualTo(moderador);
    }

    @Test
    void deberia_devolver_solo_las_del_vendedor_que_pregunta() {
        Listing mia = publicaciones.guardar(borradorConTomas());
        publicaciones.guardar(borradorConTomas());

        var suyas = publicaciones.buscarDelVendedor(mia.sellerId(), 0, 20);

        assertThat(suyas).hasSize(1);
        assertThat(suyas.getFirst().id()).isEqualTo(mia.id());
    }

    // RN-045: el rastro se acumula y no se sobrescribe.
    @Test
    void deberia_acumular_las_entradas_de_la_bitacora_RN_045() {
        Listing publicacion = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrar(publicacion.id(), moderador, ModerationAction.APPROVED, null, null, AHORA);
        bitacora.registrar(publicacion.id(), moderador, ModerationAction.ARCHIVED, "PROHIBITED_ITEM", "replica", AHORA);

        long entradas = jdbc.sql("SELECT count(*) FROM moderation_events WHERE listing_id = :l")
                .param("l", publicacion.id().value())
                .query(Long.class)
                .single();

        assertThat(entradas).isEqualTo(2);
    }

    // --- El rastro de moderacion. HU-013 -------------------------------------

    /**
     * Criterio 7 visto desde abajo: el rastro de dos publicaciones no se mezcla.
     *
     * <p>El caso de uso comprueba el dueno antes de preguntar, pero esta consulta filtra
     * por publicacion y tiene que filtrar de verdad. Si el {@code WHERE} se perdiera, esa
     * comprobacion previa dejaria de proteger nada: la publicacion seria tuya y los eventos
     * de todo el mundo.
     */
    @Test
    void deberia_devolver_solo_el_rastro_de_la_publicacion_que_se_pide() {
        Listing mia = publicaciones.guardar(borradorConTomas());
        Listing suya = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrarEnvio(mia.id(), mia.sellerId(), AHORA);
        bitacora.registrar(suya.id(), moderador, ModerationAction.REJECTED, "PROHIBITED_ITEM", "nota ajena", AHORA);

        assertThat(bitacora.historial(mia.id()))
                .extracting(ModerationEvent::action)
                .containsExactly(ModerationAction.SUBMITTED);
    }

    /** Criterios 1 a 3: cada accion vuelve con su motivo, y el envio sin ninguno. */
    @Test
    void deberia_devolver_cada_accion_con_su_motivo_y_su_fecha() {
        Listing publicacion = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrarEnvio(publicacion.id(), publicacion.sellerId(), AHORA);
        bitacora.registrar(
                publicacion.id(),
                moderador,
                ModerationAction.REJECTED,
                "PHOTOS_UNUSABLE",
                "borrosa",
                AHORA.plusSeconds(60));

        List<ModerationEvent> rastro = bitacora.historial(publicacion.id());

        assertThat(rastro)
                .extracting(ModerationEvent::action, ModerationEvent::reason)
                .containsExactly(
                        tuple(ModerationAction.REJECTED, ListingRejectionReason.PHOTOS_UNUSABLE),
                        tuple(ModerationAction.SUBMITTED, null));
        assertThat(rastro.getLast().occurredAt()).isEqualTo(AHORA);

        // Y la de la decision, que es la mitad que faltaba de la regresion de los dos
        // relojes: `created_at` conserva su DEFAULT now(), asi que quitar `:cuando` del
        // INSERT de `registrar` dejaba la fila fechada por el motor sin que nada lo viera.
        // El orden no lo delata, porque sigue saliendo igual.
        assertThat(rastro.getFirst().occurredAt()).isEqualTo(AHORA.plusSeconds(60));
    }

    /**
     * El desempate por identificador, que es la leccion ya pagada en las dos colas.
     *
     * <p>Dos eventos en el mismo instante -- aprobar y reenviar caben en el mismo
     * milisegundo -- dejan el orden indefinido si solo se ordena por fecha, y PostgreSQL
     * puede devolverlos distinto entre dos cargas de la misma pantalla.
     *
     * <p><strong>Los tres eventos son distinguibles a proposito.</strong> La primera version
     * de esta prueba escribia tres envios identicos y afirmaba que sus identificadores salian
     * descendentes... de una consulta propia que ella misma ordenaba por
     * {@code created_at DESC, id DESC}. Es decir, comprobaba que su propio {@code ORDER BY}
     * funciona: borrar {@code , id DESC} del adaptador la dejaba en verde. Con tres acciones
     * distintas se afirma sobre lo que devuelve {@code historial}, que es lo que se quiere
     * proteger, y sin el desempate el orden sale mal.
     *
     * <p><strong>Y el orden esperado no se escribe a mano, que es lo que la hacia
     * intermitente.</strong> Afirmaba {@code ARCHIVED, APPROVED, SUBMITTED}, es decir, el
     * orden inverso al de insercion, dando por hecho que identificadores creados uno detras
     * de otro salen crecientes. No es cierto dentro del mismo milisegundo: el generador de
     * v7 pone azar en los 74 bits que siguen a la marca de tiempo y **no lleva contador
     * monotono**, cosa que ADR-0015 aceptó por escrito. Aprobar y archivar caben de sobra en
     * un milisegundo -- en el corredor de la integracion continua caben--, y ahi el orden de
     * los dos identificadores es una moneda al aire. Fallo asi en `main` el 10 de septiembre
     * de 2026, despues de pasar en el pull request y dos veces en local.
     *
     * <p>Lo que se afirma ahora es la regla y no una de sus dos caras: que {@code historial}
     * devuelve exactamente lo que devuelve ordenar por identificador descendente, leido de la
     * base por separado. Sigue teniendo dientes -- sin {@code , id DESC} el adaptador
     * devuelve el orden de insercion, que con tres identificadores al azar coincide con este
     * una vez de cada seis-- y ya no depende de quien gane la moneda.
     */
    @Test
    void deberia_desempatar_por_identificador_cuando_dos_eventos_caen_a_la_misma_hora() {
        Listing publicacion = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrarEnvio(publicacion.id(), publicacion.sellerId(), AHORA);
        bitacora.registrar(publicacion.id(), moderador, ModerationAction.APPROVED, null, null, AHORA);
        bitacora.registrar(publicacion.id(), moderador, ModerationAction.ARCHIVED, "PROHIBITED_ITEM", null, AHORA);

        List<ModerationEvent> rastro = bitacora.historial(publicacion.id());

        List<ModerationAction> porIdentificador = jdbc
                .sql("SELECT action FROM moderation_events WHERE listing_id = :publicacion ORDER BY id DESC")
                .param("publicacion", publicacion.id().value())
                .query(String.class)
                .list()
                .stream()
                .map(ModerationAction::valueOf)
                .toList();

        assertThat(rastro).extracting(ModerationEvent::action).containsExactlyElementsOf(porIdentificador);
        assertThat(rastro)
                .extracting(ModerationEvent::action)
                .containsExactlyInAnyOrder(
                        ModerationAction.ARCHIVED, ModerationAction.APPROVED, ModerationAction.SUBMITTED);
        assertThat(rastro).extracting(ModerationEvent::occurredAt).containsOnly(AHORA);
    }

    /**
     * Criterio 3: el retiro de RN-024 vuelve con su motivo desde la base de verdad.
     *
     * <p>Estaba probado solo en {@code application}, sobre el doble en memoria. Que
     * {@code ARCHIVED} y su motivo sobrevivan al viaje de ida y vuelta por PostgreSQL no lo
     * miraba nadie, y es el evento que mas le importa a quien vende.
     */
    @Test
    void deberia_devolver_el_retiro_con_su_motivo_criterio_3() {
        Listing publicacion = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrar(
                publicacion.id(), moderador, ModerationAction.ARCHIVED, "SUSPECTED_COUNTERFEIT", "replica", AHORA);

        assertThat(bitacora.historial(publicacion.id())).singleElement().satisfies(evento -> {
            assertThat(evento.action()).isEqualTo(ModerationAction.ARCHIVED);
            assertThat(evento.reason()).isEqualTo(ListingRejectionReason.SUSPECTED_COUNTERFEIT);
        });
    }

    /** Criterio 6: un borrador que nunca salio no tiene rastro, y eso no es un fallo. */
    @Test
    void deberia_devolver_un_rastro_vacio_cuando_no_ha_pasado_nada() {
        Listing borrador = publicaciones.guardar(borradorConTomas());

        assertThat(bitacora.historial(borrador.id())).isEmpty();
    }

    /**
     * Criterio 5 y RN-074, comprobados donde de verdad se cumplen.
     *
     * <p>La fila lleva actor y nota -- auditar exige saber quien decidio -- y la consulta
     * del rastro no los nombra. Esta prueba los escribe con valores reconocibles y afirma
     * sobre la fila cruda que siguen ahi, para que se vea que lo que no sale es una decision
     * de la lectura y no que se dejaran de escribir.
     */
    @Test
    void deberia_seguir_guardando_actor_y_nota_aunque_el_rastro_no_los_devuelva_RN_074() {
        Listing publicacion = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrar(
                publicacion.id(), moderador, ModerationAction.REJECTED, "PHOTOS_UNUSABLE", "nota interna", AHORA);

        Map<String, Object> fila = jdbc.sql("SELECT actor_id, notes FROM moderation_events WHERE listing_id = :l")
                .param("l", publicacion.id().value())
                .query()
                .singleRow();

        assertThat(fila.get("actor_id")).isEqualTo(moderador.value());
        assertThat(fila.get("notes")).isEqualTo("nota interna");
    }

    // --- El catalogo publico. HU-009 -----------------------------------------

    /**
     * RN-068 contra la base de verdad.
     *
     * <p>Se siembran los siete estados y se comprueba que sale uno. Es la prueba que el
     * criterio 2 pide y la unica forma de demostrarlo: en memoria el filtro es una linea
     * de Java, aqui es la clausula que de verdad corre.
     */
    @Test
    void deberia_traer_del_catalogo_solo_lo_publicado_RN_068() {
        Listing visible = publicada();
        Listing borrador = publicaciones.guardar(borradorConTomas());
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        Listing pausada = publicaciones.guardar(publicada().pausar(AHORA));

        List<ListingId> catalogo = catalogo(50).stream().map(Listing::id).toList();

        // Por contencion y no por igualdad: esta clase comparte la base entre pruebas y
        // varias dejan publicaciones vivas. Lo que se afirma es lo que esta prueba
        // sembro, que es de lo unico que puede responder.
        assertThat(catalogo).contains(visible.id());
        assertThat(catalogo).doesNotContain(borrador.id(), enRevision.id(), pausada.id());
    }

    /**
     * Lo mas reciente primero, que es lo que el indice de V14 sostiene.
     *
     * <p>Se recorre <strong>el escaparate de un vendedor propio</strong> y no el catalogo
     * entero. Leia los primeros cincuenta del catalogo y quedaba a merced de cuantas filas
     * dejaran las demas pruebas: la vieja se publica dos horas antes, asi que cae detras de
     * todas las que comparten el instante {@code AHORA}, y en cuanto esas pasen de cuarenta
     * y nueve se sale del tramo y la prueba falla por algo que no esta comprobando. La
     * <p><strong>Y demuestra el escaparate y nada mas.</strong> Hasta HU-014 la clausula del
     * orden era una sola y recorrer una consulta demostraba la otra; desde que el catalogo lo
     * sirve {@code PostgresSearchEngine}, con su propio {@code orden()}, son dos. La del
     * catalogo la cubre {@code deberia_ordenar_por_publicacion_mas_reciente_cuando_se_pide_ese_orden}.
     */
    @Test
    void deberia_ordenar_el_catalogo_por_fecha_de_publicacion_descendente() {
        SellerId vendedor = new SellerId(nuevoUsuario());
        Listing vieja = publicadaDe(vendedor, AHORA.minus(Duration.ofHours(2)));
        Listing nueva = publicadaDe(vendedor, AHORA);

        List<Listing> suyas = publicaciones.publicadasDelVendedor(vendedor, null, 50);

        assertThat(suyas).extracting(Listing::id).containsExactly(nueva.id(), vieja.id());
    }

    /**
     * El cursor sobre la pareja, no sobre la fecha.
     *
     * <p>Las tres se publican en el <strong>mismo instante</strong>, que es lo que pasa con
     * un reloj fijo y lo que puede pasar en produccion. Con un cursor que solo mirara
     * `published_at`, el segundo tramo se saltaria dos o repetiria las tres para siempre.
     * Esta prueba es la razon de que la consulta use `(published_at, id) < (:fecha, :id)`.
     */
    @Test
    void deberia_recorrer_el_catalogo_sin_repetir_ni_perder_publicadas_en_el_mismo_instante() {
        // Las tres del mismo vendedor, y se recorre su escaparate: asi el tramo es solo lo
        // que esta prueba sembro. Desde HU-014 hay dos `condicionDelCursor` -la de esta clase
        // y la de PostgresSearchEngine-, asi que esto ya no demuestra la del catalogo: esa la
        // cubre `deberia_recorrer_el_catalogo_por_fecha_sin_repetir_ni_perder_las_del_mismo_instante`.
        SellerId vendedor = new SellerId(nuevoUsuario());
        publicadaDe(vendedor, AHORA);
        publicadaDe(vendedor, AHORA);
        publicadaDe(vendedor, AHORA);

        List<Listing> primera = publicaciones.publicadasDelVendedor(vendedor, null, 2);
        CatalogCursor desde = CatalogCursor.porFecha(
                Objects.requireNonNull(primera.getLast().publishedAt()),
                primera.getLast().id());
        List<Listing> segunda = publicaciones.publicadasDelVendedor(vendedor, desde, 2);

        assertThat(primera).hasSize(2);
        assertThat(segunda).hasSize(1);
        assertThat(primera)
                .extracting(Listing::id)
                .doesNotContainAnyElementsOf(segunda.stream().map(Listing::id).toList());
    }

    /** Criterio 8: el filtro de categoria es el de la publicacion, no el de su familia. */
    @Test
    void deberia_filtrar_el_catalogo_por_categoria() {
        Listing camisa = publicada();
        Category jeans = categoriaPorSlug("jeans");

        List<Listing> soloCamisas =
                buscar(criterios().categorias(camisa.product().categoryId()).limite(50));

        assertThat(soloCamisas).extracting(Listing::id).contains(camisa.id());
        assertThat(soloCamisas)
                .allSatisfy(publicacion -> assertThat(publicacion.product().categoryId())
                        .isEqualTo(camisa.product().categoryId()));

        // Ninguna prueba de esta clase publica en jeans, asi que el filtro se puede
        // afirmar por el lado vacio tambien.
        assertThat(buscar(criterios().categorias(jeans.id()))).isEmpty();
    }

    /** El escaparate de un vendedor: lo suyo y publicado, no sus borradores. */
    @Test
    void deberia_traer_del_escaparate_solo_lo_publicado_del_vendedor() {
        Listing visible = publicada();
        SellerId suyo = visible.sellerId();
        publicaciones.guardar(borradorConTomas());

        List<Listing> escaparate = publicaciones.publicadasDelVendedor(suyo, null, 24);

        // Aqui si es igualdad: el vendedor es de esta prueba y no tiene nada mas.
        assertThat(escaparate).extracting(Listing::id).containsExactly(visible.id());
    }

    /** Criterio 10: una familia son sus hojas, y las de una familia retirada no cuentan. */
    @Test
    void deberia_resolver_una_familia_en_sus_hojas_publicables() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");
        Category tops =
                categorias.buscar(Objects.requireNonNull(camisas.parentId())).orElseThrow();

        List<CategoryId> hojas = categorias.publicablesBajo(tops.id());

        assertThat(hojas).contains(camisas.id()).doesNotContain(tops.id());
        assertThat(categorias.publicablesBajo(camisas.id())).containsExactly(camisas.id());
    }

    /** Criterio 9: retirada del arbol no es lo mismo que vacia. */
    @Test
    void no_deberia_resolver_una_categoria_retirada_criterio_9() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");
        retirar("camisas-y-blusas");
        try {
            assertThat(categorias.publicablesBajo(camisas.id())).isEmpty();
        } finally {
            devolver("camisas-y-blusas");
        }
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * El arbol completo, contra lo que sembraron V9 y V11.
     *
     * <p>Es la unica prueba que ve los nombres visibles: no estan en el modelo de dominio,
     * asi que ninguna prueba de dominio puede mirarlos. Y son texto que lee un comprador.
     */
    @Test
    void deberia_armar_el_arbol_activo_con_sus_nombres() {
        List<CategoryView> arbol = categorias.arbolActivo();

        assertThat(arbol).hasSize(6).allSatisfy(familia -> {
            assertThat(familia.esFamilia()).isTrue();
            assertThat(familia.hijas()).isNotEmpty();
        });

        assertThat(arbol.stream().flatMap(familia -> familia.hijas().stream())).hasSize(31);
    }

    /** V11: los nombres se sembraron sin tildes y eso lo lee un comprador. */
    @Test
    void deberia_traer_los_nombres_del_espanol_bien_escritos() {
        List<String> nombres = categorias.arbolActivo().stream()
                .flatMap(familia -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(familia.nombreEs()),
                        familia.hijas().stream().map(CategoryView::nombreEs)))
                .toList();

        assertThat(nombres)
                .contains("Tecnología", "Suéteres, buzos y sacos", "Trajes de baño", "Cámaras")
                .doesNotContain("Tecnologia", "Sueteres, buzos y sacos", "Trajes de bano", "Camaras");
    }

    /** RN-064: las siete de tecnologia no admiten lo usado, y el formulario lo necesita. */
    @Test
    void deberia_decir_que_la_tecnologia_no_admite_lo_usado_RN_064() {
        CategoryView tecnologia = categorias.arbolActivo().stream()
                .filter(familia -> "tech".equals(familia.slug()))
                .findFirst()
                .orElseThrow();

        assertThat(tecnologia.hijas()).hasSize(7).allSatisfy(hija -> {
            assertThat(hija.admiteUsado()).isFalse();
            assertThat(hija.medidasObligatorias()).isNotEmpty();
        });
    }

    /**
     * Una categoria retirada no se ofrece, y la publicacion que ya la tenia la conserva.
     *
     * <p>Es el caso borde de la historia, y hasta ahora el {@code WHERE active} se podia
     * quitar sin que ninguna prueba se enterara: las tres leian el arbol sembrado, donde
     * todo esta activo.
     */
    @Test
    void no_deberia_ofrecer_una_categoria_retirada() {
        Category gafas = categoriaPorSlug("gafas");
        retirar("gafas");

        try {
            List<String> hojas = categorias.arbolActivo().stream()
                    .flatMap(familia -> familia.hijas().stream())
                    .map(CategoryView::slug)
                    .toList();

            assertThat(hojas).doesNotContain("gafas").hasSize(30);
            // Y sigue existiendo para quien ya la tenia: no se borra, se retira.
            assertThat(categorias.buscar(gafas.id())).isPresent();
        } finally {
            devolver("gafas");
        }
    }

    /** Retirada la familia, sus hojas no cuelgan de la nada: desaparecen con ella. */
    @Test
    void no_deberia_ofrecer_las_hojas_de_una_familia_retirada() {
        retirar("tech");

        try {
            List<CategoryView> arbol = categorias.arbolActivo();

            assertThat(arbol).hasSize(5).noneMatch(familia -> "tech".equals(familia.slug()));
            assertThat(arbol.stream().flatMap(familia -> familia.hijas().stream()))
                    .hasSize(24)
                    .noneMatch(hoja -> "celulares-y-tabletas".equals(hoja.slug()));
        } finally {
            devolver("tech");
        }
    }

    /**
     * El orden sale de la columna {@code position}.
     *
     * <p>Su motivo declarado es que el desplegable no cambie de orden entre peticiones, y
     * eso no lo comprueba contar cuantas hay.
     */
    @Test
    void deberia_devolver_las_familias_en_el_orden_sembrado() {
        List<String> familias =
                categorias.arbolActivo().stream().map(CategoryView::slug).toList();

        assertThat(familias).containsExactly("tops", "bottoms", "full-body", "footwear", "accessories", "tech");
    }

    /** Retira una categoria o una familia, como haria una migracion futura. */
    private void retirar(String slug) {
        cambiarActiva(slug, false);
    }

    /**
     * Devuelve al arbol lo que la prueba retiro.
     *
     * <p>Esta clase comparte la base entre pruebas: sin deshacerlo, las que cuentan el
     * arbol entero empiezan a fallar por culpa de esta.
     */
    private void devolver(String slug) {
        cambiarActiva(slug, true);
    }

    private void cambiarActiva(String slug, boolean activa) {
        jdbc.sql("UPDATE categories SET active = :activa WHERE slug = :slug")
                .param("activa", activa)
                .param("slug", slug)
                .update();
    }

    private Category categoriaPorSlug(String slug) {
        UUID id = jdbc.sql("SELECT id FROM categories WHERE slug = :s")
                .param("s", slug)
                .query(UUID.class)
                .single();

        return categorias.buscar(new CategoryId(id)).orElseThrow();
    }

    /** Una cuenta real: products.seller_id apunta a users. */
    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Vendedor de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    // ------------------------------------------------------------ la busqueda
    //
    // HU-014. Lo que se prueba aqui y no se puede probar en otro sitio: que el texto
    // encuentra lo mismo con tildes y sin ellas, que el diccionario lematiza, que los
    // filtros y los cuatro ordenes se traducen a SQL que hace lo que dice, y que la
    // consulta del texto puede usar el indice de V18. El doble en memoria de
    // `application` filtra igual pero no lematiza ni puntua como `ts_rank`.
    //
    // Las publicaciones de estas pruebas llevan una palabra inventada en el titulo. La
    // clase comparte la base entre pruebas y varias dejan «Camisa de lino color hueso»
    // viva: buscar «camisa» traeria las de todas.
    //
    // Y cada prueba usa una **raiz** distinta, no solo una palabra distinta. El
    // diccionario `spanish` lematiza tambien lo inventado: «zurdaco», «zurdaca» y
    // «zurdaku» eran la misma palabra para el motor -las tres dan `zurdac`- y las
    // pruebas se veian entre si. Es la misma lematizacion que celebra la prueba del
    // plural, vista desde el otro lado.

    /**
     * Criterio 5. La razon de ser de `unaccent` y del envoltorio inmutable de V18.
     *
     * <p>Va en las dos direcciones a proposito: quien escribe con tilde tiene que encontrar
     * lo que se publico sin ella, y al reves. En un teclado de celular la mitad de la gente
     * escribe de una forma y la otra mitad de la otra.
     */
    @Test
    void deberia_encontrar_lo_mismo_con_tildes_y_sin_ellas_criterio_5() {
        Listing publicada = publicadaCon("Camisón quiltafo de seda", null);

        assertThat(buscar(criterios().texto("camison quiltafo")))
                .extracting(Listing::id)
                .containsExactly(publicada.id());
        assertThat(buscar(criterios().texto("camisón quiltafo")))
                .extracting(Listing::id)
                .containsExactly(publicada.id());
    }

    /** Criterio 6: mayusculas, minusculas o mezcla dan lo mismo. */
    @Test
    void deberia_encontrar_lo_mismo_en_mayusculas_y_en_minusculas_criterio_6() {
        Listing publicada = publicadaCon("Camisa brilquen de lino", null);

        assertThat(buscar(criterios().texto("BRILQUEN")))
                .extracting(Listing::id)
                .containsExactly(publicada.id());
        assertThat(buscar(criterios().texto("BrIlQuEn")))
                .extracting(Listing::id)
                .containsExactly(publicada.id());
    }

    /**
     * El diccionario `spanish` lematiza, y eso solo se puede comprobar contra PostgreSQL.
     *
     * <p>No esta en ningun criterio: es lo que el motor da de regalo, y se fija aqui para
     * que el dia que alguien cambie el diccionario a `simple` la prueba lo diga.
     */
    @Test
    void deberia_encontrar_el_singular_buscando_el_plural() {
        Listing publicada = publicadaCon("Camisa vasquen de lino", null);

        assertThat(buscar(criterios().texto("camisas vasquen")))
                .extracting(Listing::id)
                .containsExactly(publicada.id());
    }

    @Test
    void deberia_cumplir_RN_082_buscando_en_el_titulo_y_en_la_marca_pero_no_en_la_descripcion() {
        Listing porTitulo = publicadaCon("Camisa tulqueno de lino", null);
        Listing porMarca = publicadaCon("Jean recto azul oscuro", "Palquiro");

        assertThat(buscar(criterios().texto("tulqueno")))
                .extracting(Listing::id)
                .containsExactly(porTitulo.id());
        assertThat(buscar(criterios().texto("palquiro")))
                .extracting(Listing::id)
                .containsExactly(porMarca.id());
        // «Usada dos veces» es la descripcion de todas las publicaciones de esta clase.
        assertThat(buscar(criterios().texto("usada"))).isEmpty();
    }

    /**
     * RN-085: la marca se busca como texto y no filtra.
     *
     * <p>La historia dio esta regla por no comprobable porque un parametro no reconocido se
     * ignora en silencio, y eso era solo la mitad: la regla dice que la marca <strong>es
     * texto buscable</strong>, y esa mitad si se afirma. La otra -que {@code ?brand=} no
     * hace nada- la fija {@code CatalogControllerTest}, y se pondra en rojo el dia que se
     * cierre el hueco de los parametros desconocidos, que es cuando hay que releer esto.
     *
     * <p>La marca es texto libre y opcional -«Nike», «nike» y «NIKE» serian tres valores
     * distintos-, que es la razon de que no sea una lista cerrada como el color.
     */
    @Test
    void deberia_cumplir_RN_085_encontrando_por_la_marca_escrita_de_cualquier_forma() {
        Listing conMarca = publicadaCon("Jean recto azul oscuro", "TRALQUIVIA");
        Listing sinMarca = publicadaCon("Jean recto negro", null);

        assertThat(buscar(criterios().texto("tralquivia")))
                .extracting(Listing::id)
                .containsExactly(conMarca.id());
        assertThat(buscar(criterios().texto("TrAlQuIvIa")))
                .extracting(Listing::id)
                .containsExactly(conMarca.id());
        assertThat(buscar(criterios().texto("tralquivia")))
                .extracting(Listing::id)
                .doesNotContain(sinMarca.id());
    }

    /**
     * RN-081: por este segundo camino tampoco sale lo que no esta publicado.
     *
     * <p>Se siembran los cinco estados no publicados con la misma palabra -incluido
     * {@code REJECTED}, que faltaba- y se afirma que sale <strong>uno</strong>,
     * y no solo que el pausado no aparece: la regla habla de «cualquiera de los otros seis
     * estados» y una prueba por estado deja los demas a la buena voluntad de quien escriba
     * la consulta.
     */
    @Test
    void deberia_cumplir_RN_081_no_encontrando_lo_que_no_esta_publicado() {
        Listing viva = publicadaCon("Camisa grimalto de lino", null);

        publicaciones.guardar(publicadaCon("Camisa grimalto pausada", null).pausar(AHORA));
        publicaciones.guardar(publicadaCon("Camisa grimalto archivada", null).archivar(AHORA));
        publicaciones.guardar(borradorConTitulo("Camisa grimalto en borrador"));
        publicaciones.guardar(borradorConTitulo("Camisa grimalto en revision").enviarARevision(AHORA));
        publicaciones.guardar(borradorConTitulo("Camisa grimalto rechazada")
                .enviarARevision(AHORA)
                .rechazar(
                        new ModeratorId(nuevoUsuario()),
                        ListingRejectionReason.PHOTOS_UNUSABLE,
                        "Las tomas salen movidas.",
                        AHORA));

        assertThat(buscar(criterios().texto("grimalto")))
                .extracting(Listing::id)
                .containsExactly(viva.id());
    }

    /** Criterio 14: los filtros se exigen todos y se traducen a un solo SQL. */
    @Test
    void deberia_exigir_todos_los_filtros_a_la_vez_criterio_14() {
        Listing azulEmeM = publicadaCon(
                "Camisa nubrafo azul", null, Condition.NEW, new Size(SizeSystem.ALPHA, "M"), Color.BLUE, 90_000);
        publicadaCon("Camisa nubrafo roja", null, Condition.NEW, new Size(SizeSystem.ALPHA, "M"), Color.RED, 90_000);
        publicadaCon("Camisa nubrafo azul L", null, Condition.NEW, new Size(SizeSystem.ALPHA, "L"), Color.BLUE, 90_000);

        List<Listing> resultado = buscar(criterios()
                .texto("nubrafo")
                .colores(Color.BLUE)
                .talla(new Size(SizeSystem.ALPHA, "M"))
                .condiciones(Condition.NEW));

        assertThat(resultado).extracting(Listing::id).containsExactly(azulEmeM.id());
    }

    /** RN-087: la talla se compara con su sistema, nunca el valor suelto. */
    @Test
    void deberia_cumplir_RN_087_no_mezclando_sistemas_de_talla() {
        Listing porLetra = publicadaCon(
                "Camisa delvicho por letra",
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                Color.BEIGE,
                90_000);
        publicadaCon(
                "Camisa delvicho numerica",
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.NUMERIC_CO, "10"),
                Color.BEIGE,
                90_000);

        assertThat(buscar(criterios().texto("delvicho").talla(new Size(SizeSystem.ALPHA, "M"))))
                .extracting(Listing::id)
                .containsExactly(porLetra.id());
    }

    /** Criterio 13: los dos extremos entran, y lo decide un BETWEEN de verdad. */
    @Test
    void deberia_incluir_los_dos_extremos_del_rango_de_precio_criterio_13() {
        Listing enElMinimo = publicadaConPrecio("Camisa yerpanto barata", 50_000);
        Listing enElMaximo = publicadaConPrecio("Camisa yerpanto cara", 100_000);
        publicadaConPrecio("Camisa yerpanto carisima", 100_001);

        List<Listing> resultado =
                buscar(criterios().texto("yerpanto").precio(50_000, 100_000).orden(CatalogSort.PRICE_ASC));

        assertThat(resultado).extracting(Listing::id).containsExactly(enElMinimo.id(), enElMaximo.id());
    }

    /**
     * Criterio 18 y 21. El cursor de precio, con dos precios iguales.
     *
     * <p>El empate de precio no es raro: es lo normal en un catalogo donde la gente pone
     * cifras redondas. Sin el desempate por identificador, el segundo tramo se salta una de
     * las dos o repite las dos para siempre.
     */
    @Test
    void deberia_recorrer_por_precio_sin_repetir_ni_perder_las_que_valen_igual_criterio_21() {
        publicadaConPrecio("Camisa cambrilo una", 70_000);
        publicadaConPrecio("Camisa cambrilo otra", 70_000);
        publicadaConPrecio("Camisa cambrilo tercera", 90_000);

        List<Listing> primera = buscar(
                criterios().texto("cambrilo").orden(CatalogSort.PRICE_ASC).limite(2));
        CatalogCursor desde = CatalogCursor.porPrecio(
                CatalogSort.PRICE_ASC,
                Objects.requireNonNull(primera.getLast().product().price()),
                primera.getLast().id());
        List<Listing> segunda = buscar(criterios()
                .texto("cambrilo")
                .orden(CatalogSort.PRICE_ASC)
                .desde(desde)
                .limite(2));

        assertThat(primera).hasSize(2);
        assertThat(segunda).hasSize(1);
        assertThat(primera)
                .extracting(Listing::id)
                .doesNotContainAnyElementsOf(segunda.stream().map(Listing::id).toList());
    }

    /**
     * El cursor de relevancia, que es el que mas empata.
     *
     * <p>{@code ts_rank} devuelve `real` y el cursor viaja como `float8`. Si la comparacion
     * mezclara las dos precisiones, el empate exacto dejaria de serlo y el desempate por
     * identificador no llegaria a aplicarse: la fila del borde se repetiria o se perderia.
     * Esta prueba es la razon del casteo explicito en la condicion del cursor.
     */
    @Test
    void deberia_recorrer_por_relevancia_sin_repetir_ni_perder_las_que_puntuan_igual() {
        publicadaCon("Camisa sextavio de lino", null);
        publicadaCon("Camisa sextavio de lino", null);
        publicadaCon("Camisa sextavio de lino", null);

        List<SearchHit> primera =
                motor.buscar(criterios().texto("sextavio").limite(2).arma());
        SearchHit ultima = primera.getLast();
        CatalogCursor desde = CatalogCursor.porRelevancia(
                ultima.exigirRelevancia(), ultima.publicacion().id());

        List<SearchHit> segunda = motor.buscar(
                criterios().texto("sextavio").desde(desde).limite(2).arma());

        assertThat(primera).hasSize(2);
        assertThat(segunda).hasSize(1);
        assertThat(primera.stream().map(r -> r.publicacion().id()).toList())
                .doesNotContainAnyElementsOf(
                        segunda.stream().map(r -> r.publicacion().id()).toList());
    }

    /** El orden por relevancia trae la puntuacion, y los demas no la calculan. */
    @Test
    void deberia_puntuar_solo_cuando_se_ordena_por_relevancia() {
        publicadaCon("Camisa olvarico de lino", null);

        assertThat(motor.buscar(criterios().texto("olvarico").arma()))
                .allSatisfy(resultado -> assertThat(resultado.relevancia()).isNotNull());
        assertThat(motor.buscar(criterios()
                        .texto("olvarico")
                        .orden(CatalogSort.PRICE_ASC)
                        .arma()))
                .allSatisfy(resultado -> assertThat(resultado.relevancia()).isNull());
    }

    /**
     * Que la consulta del texto <strong>pueda</strong> usar el indice de V18.
     *
     * <p>Es la prueba que ninguna otra puede dar: una busqueda que pasa por el indice
     * equivocado devuelve exactamente lo mismo, solo que recorriendo la tabla, y no se nota
     * en ningun resultado. Lo que se comprueba es que la expresion del indice y la de la
     * consulta coinciden; si alguien cambia una y no la otra, esto se pone rojo.
     *
     * <p>Con el planificador libre y tres filas de prueba, recorrer la tabla siempre gana:
     * por eso se le quita esa opcion dentro de la transaccion. No se le fuerza a usar el
     * indice —eso no se puede—, se le quita la alternativa barata y se mira si sabe llegar.
     */
    @Test
    @Transactional
    void deberia_poder_resolver_la_busqueda_de_texto_por_el_indice_de_V18() {
        jdbc.sql("SET LOCAL enable_seqscan = off").update();

        String plan = String.join(
                "\n",
                jdbc.sql("EXPLAIN SELECT l.id FROM listings l JOIN products p ON p.id = l.product_id WHERE "
                                + PostgresSearchEngine.CONDICION_DE_TEXTO)
                        .param("texto", "camisa de lino")
                        .query(String.class)
                        .list());

        assertThat(plan).contains("idx_products_search");
    }

    /**
     * Criterio 17 por el camino que de verdad sirve el catalogo.
     *
     * <p>Es la mitad que se habia quedado sin prueba: {@code PostgresSearchEngine} tiene su
     * propio {@code orden()} desde que el catalogo pasa por el, y nadie afirmaba que
     * {@code NEWEST} ordenara. Se pide el orden a proposito, porque con texto el de omision
     * es la relevancia (RN-088).
     */
    @Test
    void deberia_ordenar_por_publicacion_mas_reciente_cuando_se_pide_ese_orden() {
        Listing vieja = publicadaEn("Camisa brendolio vieja", AHORA.minus(Duration.ofHours(2)));
        Listing nueva = publicadaEn("Camisa brendolio nueva", AHORA);

        assertThat(buscar(criterios().texto("brendolio").orden(CatalogSort.NEWEST)))
                .extracting(Listing::id)
                .containsExactly(nueva.id(), vieja.id());
    }

    /**
     * Criterio 21 por ese mismo camino, con el empate que lo hace necesario.
     *
     * <p>Las tres en el mismo instante: sin el desempate por identificador de
     * {@code (published_at, id) < (:fecha, :id)}, el segundo tramo repite o se salta.
     */
    @Test
    void deberia_recorrer_el_catalogo_por_fecha_sin_repetir_ni_perder_las_del_mismo_instante() {
        publicadaEn("Camisa quintaldo de lino", AHORA);
        publicadaEn("Camisa quintaldo de algodon", AHORA);
        publicadaEn("Camisa quintaldo de seda", AHORA);

        List<Listing> primera =
                buscar(criterios().texto("quintaldo").orden(CatalogSort.NEWEST).limite(2));
        CatalogCursor desde = CatalogCursor.porFecha(
                Objects.requireNonNull(primera.getLast().publishedAt()),
                primera.getLast().id());
        List<Listing> segunda = buscar(criterios()
                .texto("quintaldo")
                .orden(CatalogSort.NEWEST)
                .desde(desde)
                .limite(2));

        assertThat(primera).hasSize(2);
        assertThat(segunda).hasSize(1);
        assertThat(primera)
                .extracting(Listing::id)
                .doesNotContainAnyElementsOf(segunda.stream().map(Listing::id).toList());
    }

    /**
     * La otra mitad del criterio 18: de mayor a menor.
     *
     * <p>Solo se afirmaba {@code PRICE_ASC}. Invertir {@code PRICE_DESC} en el motor dejaba
     * la suite entera en verde.
     */
    @Test
    void deberia_ordenar_por_precio_de_mayor_a_menor_criterio_18() {
        Listing barata = publicadaConPrecio("Camisa zurnaldo barata", 50_000);
        Listing media = publicadaConPrecio("Camisa zurnaldo media", 90_000);
        Listing cara = publicadaConPrecio("Camisa zurnaldo cara", 140_000);

        assertThat(buscar(criterios().texto("zurnaldo").orden(CatalogSort.PRICE_DESC)))
                .extracting(Listing::id)
                .containsExactly(cara.id(), media.id(), barata.id());
    }

    /**
     * Criterio 16: con texto y sin orden pedido, manda la relevancia.
     *
     * <p><strong>La mas relevante se publica antes que la otra</strong>, asi que si el orden
     * cayera a la fecha -o si {@code ts_rank} se ordenara al reves- la afirmacion se rompe.
     * La unica prueba que habia usaba tres titulos identicos, es decir tres puntuaciones
     * empatadas: cambiar {@code DESC} por {@code ASC} la dejaba en verde.
     */
    @Test
    void deberia_ordenar_por_relevancia_y_no_por_fecha_cuando_hay_texto_criterio_16() {
        Listing masRelevante = publicadaEn("Marlopio marlopio marlopio de lino", AHORA.minus(Duration.ofHours(2)));
        Listing menosRelevante = publicadaEn("Camisa marlopio de lino", AHORA);

        assertThat(buscar(criterios().texto("marlopio")))
                .extracting(Listing::id)
                .containsExactly(masRelevante.id(), menosRelevante.id());
    }

    // --- apoyo de la busqueda ------------------------------------------------

    /** El catalogo entero por el motor, que es como lo pide el caso de uso. */
    private List<Listing> catalogo(int limite) {
        return buscar(criterios().limite(limite));
    }

    private List<Listing> buscar(Criterios criterios) {
        return motor.buscar(criterios.arma()).stream()
                .map(SearchHit::publicacion)
                .toList();
    }

    private static Criterios criterios() {
        return new Criterios();
    }

    /** Unos criterios con lo minimo puesto, para que cada prueba diga solo lo que le importa. */
    private static final class Criterios {

        private @Nullable SearchText texto;
        private List<CategoryId> categorias = List.of();
        private Set<Condition> condiciones = Set.of();
        private @Nullable Size talla;
        private Set<Color> colores = Set.of();
        private PriceRange precio = PriceRange.SIN_LIMITE;
        private @Nullable CatalogSort orden;
        private @Nullable CatalogCursor desde;
        private int limite = 24;

        Criterios texto(String texto) {
            this.texto = SearchText.de(texto);
            return this;
        }

        Criterios categorias(CategoryId... categorias) {
            this.categorias = List.of(categorias);
            return this;
        }

        Criterios condiciones(Condition... condiciones) {
            this.condiciones = Set.of(condiciones);
            return this;
        }

        Criterios colores(Color... colores) {
            this.colores = Set.of(colores);
            return this;
        }

        Criterios talla(Size talla) {
            this.talla = talla;
            return this;
        }

        Criterios precio(long minimo, long maximo) {
            this.precio = new PriceRange(Money.dePesos(minimo), Money.dePesos(maximo));
            return this;
        }

        Criterios orden(CatalogSort orden) {
            this.orden = orden;
            return this;
        }

        Criterios desde(CatalogCursor desde) {
            this.desde = desde;
            return this;
        }

        Criterios limite(int limite) {
            this.limite = limite;
            return this;
        }

        SearchCriteria arma() {
            return new SearchCriteria(
                    texto,
                    categorias,
                    condiciones,
                    talla,
                    colores,
                    precio,
                    CatalogSort.efectivo(orden, texto != null),
                    desde,
                    limite);
        }
    }

    private Listing publicadaCon(String titulo, @Nullable String marca) {
        return publicadaCon(titulo, marca, Condition.LIKE_NEW, new Size(SizeSystem.ALPHA, "M"), Color.BEIGE, 185_000);
    }

    private Listing publicadaConPrecio(String titulo, long precio) {
        return publicadaCon(titulo, null, Condition.LIKE_NEW, new Size(SizeSystem.ALPHA, "M"), Color.BEIGE, precio);
    }

    /** La misma, publicada en un instante concreto: es lo que hace falta para ordenar por fecha. */
    private Listing publicadaEn(String titulo, Instant cuando) {
        return publicadaCon(
                titulo, null, Condition.LIKE_NEW, new Size(SizeSystem.ALPHA, "M"), Color.BEIGE, 185_000, cuando);
    }

    /** Una publicacion viva con los campos por los que HU-014 busca y filtra. */
    private Listing publicadaCon(
            String titulo, @Nullable String marca, Condition condicion, Size talla, Color color, long precio) {
        return publicadaCon(titulo, marca, condicion, talla, color, precio, AHORA);
    }

    private Listing publicadaCon(
            String titulo,
            @Nullable String marca,
            Condition condicion,
            Size talla,
            Color color,
            long precio,
            Instant cuando) {

        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                categoriaPorSlug("camisas-y-blusas"),
                new Title(titulo),
                new Description("Usada dos veces."),
                marca == null ? null : new Brand(marca),
                condicion,
                talla,
                medidasDe(MeasurementGroup.TOP),
                color,
                Money.dePesos(precio),
                envio(),
                null,
                null);

        Listing borrador = conTomas(Listing.crearBorrador(ListingId.nuevo(), producto, cuando), 8);
        Listing enRevision = publicaciones.guardar(borrador.enviarARevision(cuando));

        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), cuando));
    }

    /**
     * Un borrador con un titulo concreto, para sembrar estados que la busqueda no debe traer.
     *
     * <p>Sin el, los estados no publicados se sembraban con el titulo generico y no llevaban
     * la palabra buscada: la prueba afirmaba sobre ellos sin que pudieran salir nunca.
     */
    private Listing borradorConTitulo(String titulo) {
        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                categoriaPorSlug("camisas-y-blusas"),
                new Title(titulo),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                medidasDe(MeasurementGroup.TOP),
                Color.BEIGE,
                Money.dePesos(185_000),
                envio(),
                null,
                null);

        return conTomas(Listing.crearBorrador(ListingId.nuevo(), producto, AHORA), 8);
    }

    private Listing borradorConTomas() {
        return conTomas(borradorDe(medidasDe(MeasurementGroup.TOP)), 8);
    }

    /** Un borrador de un vendedor concreto, para las cifras del panel. */
    private Listing borradorDeVendedor(SellerId vendedor) {
        return Listing.crearBorrador(ListingId.nuevo(), productoDe(vendedor, medidasDe(MeasurementGroup.TOP)), AHORA);
    }

    private Listing publicada() {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), AHORA));
    }

    /** Publicada por un vendedor concreto, para aislar un escaparate de las demas pruebas. */
    private Listing publicadaDe(SellerId vendedor, Instant cuando) {
        Listing borrador = conTomas(
                Listing.crearBorrador(ListingId.nuevo(), productoDe(vendedor, medidasDe(MeasurementGroup.TOP)), AHORA),
                8);

        Listing enRevision = publicaciones.guardar(borrador.enviarARevision(cuando));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), cuando));
    }

    private Product productoDe(SellerId vendedor, Measurements medidas) {
        return Product.crear(
                ProductId.nuevo(),
                vendedor,
                categoriaPorSlug("camisas-y-blusas"),
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                new Brand("Zara"),
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                medidas,
                Color.BEIGE,
                Money.dePesos(185_000),
                envio(),
                null,
                null);
    }

    /** Lo que crea «Empezar»: la categoria y nada mas. Criterio 5. */
    private Listing borradorVacio() {
        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                categoriaPorSlug("camisas-y-blusas"),
                null,
                null,
                null,
                null,
                null,
                Measurements.vacias(),
                null,
                null,
                null,
                null,
                null);

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    private Listing borradorDe(Measurements medidas) {
        return borradorDe(medidas, Money.dePesos(185_000));
    }

    private Listing borradorDe(Measurements medidas, Money precio) {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                camisas,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                new Brand("Zara"),
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                medidas,
                Color.BEIGE,
                precio,
                envio(),
                null,
                null);

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    private Listing selladoConCuatroTomasYReferencia() {
        Category celulares = categoriaPorSlug("celulares-y-tabletas");

        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                celulares,
                new Title("Telefono de gama media, 128 GB"),
                new Description("Nuevo, sellado."),
                new Brand("Motorola"),
                Condition.NEW,
                Size.unica(),
                medidasDe(MeasurementGroup.DEVICE),
                Color.BLACK,
                Money.dePesos(900_000),
                envio(),
                true,
                null);

        Listing sellado = conTomas(Listing.crearBorrador(ListingId.nuevo(), producto, AHORA), 4);
        return sellado.conImagen(
                ProductImage.referencia(
                        ProductImageId.nuevo(),
                        clave(),
                        0,
                        new ImageDimensions(900, 1200),
                        120_000L,
                        ImageContentType.JPEG),
                AHORA);
    }

    /** Ocho tomas van seguidas; cuatro van de dos en dos, que son las canonicas. */
    private static Listing conTomas(Listing publicacion, int cuantas) {
        int paso = ProductImage.TOMAS_DE_LA_SECUENCIA / cuantas;

        Listing resultado = publicacion;
        for (int i = 0; i < cuantas; i++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            clave(),
                            i * paso,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    private static FileKey clave() {
        return new FileKey("productos/" + UUID.randomUUID() + ".jpg");
    }

    private static Measurements medidasDe(MeasurementGroup grupo) {
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        grupo.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));
        return new Measurements(valores);
    }

    private static ShippingDimensions envio() {
        return new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0"));
    }
}
