package co.sendik.config;

import co.sendik.catalog.port.out.BuyerAccounts;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.Favorites;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.catalog.port.out.SearchEngine;
import co.sendik.catalog.port.out.SellerEligibility;
import co.sendik.catalog.port.out.SellerProfiles;
import co.sendik.catalog.usecase.AddFavoriteUseCase;
import co.sendik.catalog.usecase.AddToCartUseCase;
import co.sendik.catalog.usecase.ApproveListingUseCase;
import co.sendik.catalog.usecase.ArchiveListingUseCase;
import co.sendik.catalog.usecase.ChangeListingPriceUseCase;
import co.sendik.catalog.usecase.ChangeListingShippingUseCase;
import co.sendik.catalog.usecase.CreateListingUseCase;
import co.sendik.catalog.usecase.EraseCartUseCase;
import co.sendik.catalog.usecase.EraseFavoritesUseCase;
import co.sendik.catalog.usecase.ExportCartUseCase;
import co.sendik.catalog.usecase.ExportFavoritesUseCase;
import co.sendik.catalog.usecase.ListCatalogUseCase;
import co.sendik.catalog.usecase.ListCategoriesUseCase;
import co.sendik.catalog.usecase.ListFavoritesUseCase;
import co.sendik.catalog.usecase.ListPendingListingsUseCase;
import co.sendik.catalog.usecase.ListSellerCatalogUseCase;
import co.sendik.catalog.usecase.ListSellerListingsUseCase;
import co.sendik.catalog.usecase.MergeCartUseCase;
import co.sendik.catalog.usecase.PauseListingUseCase;
import co.sendik.catalog.usecase.PreviewCartUseCase;
import co.sendik.catalog.usecase.ReadCartItemStateUseCase;
import co.sendik.catalog.usecase.ReadCartUseCase;
import co.sendik.catalog.usecase.ReadFavoriteStateUseCase;
import co.sendik.catalog.usecase.ReadListingUseCase;
import co.sendik.catalog.usecase.ReadModerationHistoryUseCase;
import co.sendik.catalog.usecase.ReadSellerProfileUseCase;
import co.sendik.catalog.usecase.RejectListingUseCase;
import co.sendik.catalog.usecase.RemoveFavoriteUseCase;
import co.sendik.catalog.usecase.RemoveFromCartUseCase;
import co.sendik.catalog.usecase.RemoveListingImageUseCase;
import co.sendik.catalog.usecase.ReopenListingUseCase;
import co.sendik.catalog.usecase.ResumeListingUseCase;
import co.sendik.catalog.usecase.SubmitListingForReviewUseCase;
import co.sendik.catalog.usecase.SummarizeSellerListingsUseCase;
import co.sendik.catalog.usecase.TakeDownListingUseCase;
import co.sendik.catalog.usecase.UpdateListingContentUseCase;
import co.sendik.catalog.usecase.UploadListingImageUseCase;
import co.sendik.catalog.usecase.WithdrawListingUseCase;
import co.sendik.shared.config.FeatureFlags;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.file.ImagePolicy;
import co.sendik.shared.file.StorageProperties;
import co.sendik.shared.port.out.ImageNormalizer;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ExposedFeatures;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de los casos de uso del catalogo. HU-007.
 *
 * <p>Por lo mismo que {@link IdentityWiring}: los casos de uso no llevan {@code @Service}
 * ni ninguna otra anotacion, porque el modulo {@code application} solo puede ver
 * {@code spring-tx} y una prueba de arquitectura falla si aparece cualquier otra cosa de
 * Spring. Se registran aqui, que es el modulo del cableado, y en sus pruebas se
 * construyen con {@code new}, sin contexto y sin simular un contenedor.
 *
 * <p><strong>No van detras de la bandera.</strong> Los que se apagan con
 * {@code FEATURE_PUBLISHING} son los controladores, que son la puerta; estos beans
 * existen siempre y no hacen nada si nadie los llama. Ponerles la condicion aqui
 * significaria que encender la bandera cambia que hay dentro del contexto y no solo que
 * esta expuesto, que es mas dificil de razonar y de probar.
 *
 * <p>El {@code Clock} lo declara {@link IdentityWiring}: es uno solo para toda la
 * aplicacion, en la zona de operacion y no en UTC.
 */
@Configuration
public class CatalogWiring {

    /**
     * Lo que la cadena de seguridad necesita saber de las banderas.
     *
     * <p>Se declara aqui y no en {@code presentation} porque {@code FeatureFlags} vive en
     * {@code infrastructure}, que ese modulo no ve. {@code bootstrap} es el unico que ve a
     * todos, y cablear es su trabajo.
     *
     * <p>Sin esto, las rutas de decision del moderador responderian 403 con la bandera
     * apagada, y el criterio 3 pide 404: la funcionalidad no esta, y un 403 diria que si.
     * Lo mismo vale para la revision de verificaciones de HU-002.
     *
     * <p>Vive en {@code CatalogWiring} porque es la historia que lo trajo. Si un tercer
     * contexto necesitara declarar rutas condicionadas, este bean se muda a un cableado
     * propio; con dos banderas no vale la pena todavia.
     */
    @Bean
    ExposedFeatures expuestas(FeatureFlags banderas) {
        return new ExposedFeatures(banderas.sellerVerification(), banderas.publishing(), banderas.catalog());
    }

    @Bean
    CreateListingUseCase createListingUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        return new CreateListingUseCase(publicaciones, categorias, elegibilidad, reloj);
    }

    @Bean
    ReadListingUseCase readListingUseCase(ListingRepository publicaciones) {
        return new ReadListingUseCase(publicaciones);
    }

    @Bean
    UpdateListingContentUseCase updateListingContentUseCase(
            ListingRepository publicaciones,
            Categories categorias,
            SellerEligibility elegibilidad,
            ModerationLog bitacora,
            Clock reloj) {
        return new UpdateListingContentUseCase(publicaciones, categorias, elegibilidad, bitacora, reloj);
    }

    @Bean
    ChangeListingPriceUseCase changeListingPriceUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ChangeListingPriceUseCase(publicaciones, reloj);
    }

    @Bean
    ChangeListingShippingUseCase changeListingShippingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ChangeListingShippingUseCase(publicaciones, reloj);
    }

    /**
     * La politica de las tomas de producto. RN-018 y RN-019.
     *
     * <p><strong>Propia, y con cualificador, porque hay otra.</strong> {@code IdentityWiring}
     * declara {@code politicaDeAvatar} —200x200 y sin proporcion— y ya avisaba de que las
     * tomas tendrian la suya. Sin este bean, pedir un {@code ImagePolicy} por tipo devolvia
     * la del avatar: los criterios 14 y 15 quedaban sin aplicar y una imagen de 200x200 se
     * aceptaba como toma de producto.
     *
     * <p>La proporcion se calcula del minimo y no se configura aparte. Dos numeros que
     * pueden contradecirse entre si —3:4 por un lado y 900x1200 por otro— acaban
     * contradiciendose.
     */
    @Bean
    ImagePolicy politicaDeTomas(StorageProperties almacenamiento) {
        ImageDimensions minimo =
                new ImageDimensions(almacenamiento.listingMinWidth(), almacenamiento.listingMinHeight());

        return new ImagePolicy(almacenamiento.maxImageBytes(), minimo, (double) minimo.width() / minimo.height());
    }

    @Bean
    UploadListingImageUseCase uploadListingImageUseCase(
            ListingRepository publicaciones,
            PublicFileStore almacen,
            ImageNormalizer normalizador,
            @Qualifier("politicaDeTomas") ImagePolicy politica,
            Clock reloj) {
        return new UploadListingImageUseCase(publicaciones, almacen, normalizador, politica, reloj);
    }

    @Bean
    RemoveListingImageUseCase removeListingImageUseCase(
            ListingRepository publicaciones, PublicFileStore almacen, Clock reloj) {
        return new RemoveListingImageUseCase(publicaciones, almacen, reloj);
    }

    @Bean
    SubmitListingForReviewUseCase submitListingForReviewUseCase(
            ListingRepository publicaciones,
            Categories categorias,
            SellerEligibility elegibilidad,
            ModerationLog bitacora,
            Clock reloj) {
        return new SubmitListingForReviewUseCase(publicaciones, categorias, elegibilidad, bitacora, reloj);
    }

    @Bean
    WithdrawListingUseCase withdrawListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new WithdrawListingUseCase(publicaciones, reloj);
    }

    @Bean
    ReopenListingUseCase reopenListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ReopenListingUseCase(publicaciones, reloj);
    }

    @Bean
    ApproveListingUseCase approveListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        return new ApproveListingUseCase(publicaciones, bitacora, avisos, reloj);
    }

    @Bean
    RejectListingUseCase rejectListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        return new RejectListingUseCase(publicaciones, bitacora, avisos, reloj);
    }

    @Bean
    TakeDownListingUseCase takeDownListingUseCase(
            ListingRepository publicaciones,
            ModerationLog bitacora,
            ListingNotifier avisos,
            PublicFileStore almacen,
            Clock reloj) {
        return new TakeDownListingUseCase(publicaciones, bitacora, avisos, almacen, reloj);
    }

    @Bean
    PauseListingUseCase pauseListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new PauseListingUseCase(publicaciones, reloj);
    }

    @Bean
    ResumeListingUseCase resumeListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ResumeListingUseCase(publicaciones, reloj);
    }

    @Bean
    ArchiveListingUseCase archiveListingUseCase(ListingRepository publicaciones, PublicFileStore almacen, Clock reloj) {
        return new ArchiveListingUseCase(publicaciones, almacen, reloj);
    }

    @Bean
    ListSellerListingsUseCase listSellerListingsUseCase(ListingRepository publicaciones) {
        return new ListSellerListingsUseCase(publicaciones);
    }

    /** Las cifras del panel del vendedor. HU-012. */
    @Bean
    SummarizeSellerListingsUseCase summarizeSellerListingsUseCase(ListingRepository publicaciones) {
        return new SummarizeSellerListingsUseCase(publicaciones);
    }

    /** El rastro de moderacion de una publicacion propia. HU-013. */
    @Bean
    ReadModerationHistoryUseCase readModerationHistoryUseCase(ListingRepository publicaciones, ModerationLog bitacora) {
        return new ReadModerationHistoryUseCase(publicaciones, bitacora);
    }

    /** La cola del moderador. HU-008. */
    @Bean
    ListPendingListingsUseCase listPendingListingsUseCase(ListingRepository publicaciones) {
        return new ListPendingListingsUseCase(publicaciones);
    }

    @Bean
    ListCategoriesUseCase listCategoriesUseCase(Categories categorias) {
        return new ListCategoriesUseCase(categorias);
    }

    // --- El catalogo publico. HU-009, y la busqueda. HU-014 -------------------

    /**
     * Recibe el motor y no el repositorio desde HU-014: el catalogo y la busqueda son la
     * misma consulta con distintas condiciones, y quien la resuelve es el puerto que
     * ADR-0035 llena con PostgreSQL. Cambiarlo a Typesense es cambiar que implementacion de
     * {@link SearchEngine} se inyecta aqui, y nada mas.
     */
    @Bean
    ListCatalogUseCase listCatalogUseCase(SearchEngine motor, Categories categorias) {
        return new ListCatalogUseCase(motor, categorias);
    }

    @Bean
    ListSellerCatalogUseCase listSellerCatalogUseCase(ListingRepository publicaciones) {
        return new ListSellerCatalogUseCase(publicaciones);
    }

    @Bean
    ReadSellerProfileUseCase readSellerProfileUseCase(SellerProfiles perfiles) {
        return new ReadSellerProfileUseCase(perfiles);
    }

    // --- Los favoritos. HU-011 -----------------------------------------------

    /**
     * Marcar necesita el reloj porque la fecha del gesto es lo que ordena la lista
     * (criterio 11). Quitar no lo necesita: borrar una fila no tiene fecha que sellar.
     */
    @Bean
    AddFavoriteUseCase addFavoriteUseCase(
            Favorites favoritos, ListingRepository publicaciones, BuyerAccounts cuentas, Clock reloj) {
        return new AddFavoriteUseCase(favoritos, publicaciones, cuentas, reloj);
    }

    @Bean
    RemoveFavoriteUseCase removeFavoriteUseCase(Favorites favoritos) {
        return new RemoveFavoriteUseCase(favoritos);
    }

    @Bean
    ReadFavoriteStateUseCase readFavoriteStateUseCase(Favorites favoritos, ListingRepository publicaciones) {
        return new ReadFavoriteStateUseCase(favoritos, publicaciones);
    }

    @Bean
    ListFavoritesUseCase listFavoritesUseCase(Favorites favoritos) {
        return new ListFavoritesUseCase(favoritos);
    }

    /**
     * Los dos que no usa ninguna pantalla del catalogo: los llama {@code identity} por el
     * puerto {@code UserFavorites}, para la descarga de datos y el cierre de cuenta. Son
     * beans como los demas porque son casos de uso publicos de este contexto, que es
     * justamente la puerta por la que el otro tiene permitido entrar.
     */
    @Bean
    ExportFavoritesUseCase exportFavoritesUseCase(Favorites favoritos) {
        return new ExportFavoritesUseCase(favoritos);
    }

    @Bean
    EraseFavoritesUseCase eraseFavoritesUseCase(Favorites favoritos) {
        return new EraseFavoritesUseCase(favoritos);
    }

    // --- El carrito. HU-015 ---------------------------------------------------

    /**
     * Agregar necesita el reloj porque la fecha en que entro ordena el carrito y decide que
     * se conserva cuando una fusion no cabe entera (criterio 10). Quitar no lo necesita.
     */
    @Bean
    AddToCartUseCase addToCartUseCase(
            CartItems carrito, ListingRepository publicaciones, BuyerAccounts cuentas, Clock reloj) {
        return new AddToCartUseCase(carrito, publicaciones, cuentas, reloj);
    }

    @Bean
    RemoveFromCartUseCase removeFromCartUseCase(CartItems carrito) {
        return new RemoveFromCartUseCase(carrito);
    }

    @Bean
    ReadCartItemStateUseCase readCartItemStateUseCase(CartItems carrito, ListingRepository publicaciones) {
        return new ReadCartItemStateUseCase(carrito, publicaciones);
    }

    @Bean
    ReadCartUseCase readCartUseCase(CartItems carrito, ListingRepository publicaciones) {
        return new ReadCartUseCase(carrito, publicaciones);
    }

    /**
     * La lectura de quien no ha entrado. Necesita el reloj para respetar el orden en que el
     * navegador trae los identificadores, que es el unico orden que existe sin filas en la
     * base: no hay fecha de cuando se agrego porque nadie la anoto.
     */
    @Bean
    PreviewCartUseCase previewCartUseCase(ListingRepository publicaciones, Clock reloj) {
        return new PreviewCartUseCase(publicaciones, reloj);
    }

    /**
     * La fusion depende de la lectura y no la repite.
     *
     * <p>Devuelve el carrito ya armado, y armarlo es exactamente lo que hace
     * {@link ReadCartUseCase}. Pasarselo en vez de reescribir el cruce es lo que garantiza
     * que el carrito que se ve tras entrar sea el mismo que se ve al recargar.
     */
    @Bean
    MergeCartUseCase mergeCartUseCase(
            CartItems carrito,
            ListingRepository publicaciones,
            BuyerAccounts cuentas,
            ReadCartUseCase lectura,
            Clock reloj) {
        return new MergeCartUseCase(carrito, publicaciones, cuentas, lectura, reloj);
    }

    /** Los dos que llama {@code identity} por el puerto {@code UserCart}. */
    @Bean
    ExportCartUseCase exportCartUseCase(CartItems carrito) {
        return new ExportCartUseCase(carrito);
    }

    @Bean
    EraseCartUseCase eraseCartUseCase(CartItems carrito) {
        return new EraseCartUseCase(carrito);
    }
}
