package co.sendik.catalog.client;

import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.identity.model.UserLocale;

/**
 * Los siete motivos de rechazo de una publicacion, en los dos idiomas.
 *
 * <p><strong>Es la unica copia del catalogo de motivos que existe fuera del
 * frontend</strong>, y por el mismo motivo que la de {@code VerificationMailTexts}: un
 * correo no tiene quien lo traduzca al abrirlo, no hay Transloco en un buzon.
 *
 * <p>La enumeracion es la fuente: el {@code switch} no tiene rama por omision, asi que
 * agregar un motivo sin texto aqui no compila.
 */
final class ListingRejectionTexts {

    private ListingRejectionTexts() {}

    static String de(UserLocale idioma, ListingRejectionReason motivo) {
        boolean espanol = idioma == UserLocale.ES;

        return switch (motivo) {
            case PHOTOS_UNUSABLE ->
                espanol
                        ? "las fotos no se pueden usar: están borrosas, muy oscuras o no cumplen el mínimo"
                        : "the photos are unusable: blurry, too dark or below the minimum size";
            case PHOTOS_MISMATCH ->
                espanol
                        ? "las fotos no corresponden con lo que describe la publicación"
                        : "the photos do not match what the listing describes";
            case MEASUREMENTS_UNRELIABLE ->
                espanol ? "las medidas faltan o no son creíbles" : "the measurements are missing or not believable";
            case CONDITION_MISDECLARED ->
                espanol
                        ? "la condición declarada no es la que se ve en las fotos"
                        : "the declared condition is not what the photos show";
            case PROHIBITED_ITEM ->
                espanol ? "el producto no se puede vender en Sendik" : "this product cannot be sold on Sendik";
            case SUSPECTED_COUNTERFEIT -> espanol ? "sospechamos que no es original" : "we suspect it is not authentic";
            case PRICE_OUT_OF_RANGE ->
                espanol
                        ? "el precio está fuera del rango razonable para esa categoría"
                        : "the price is outside the reasonable range for that category";
        };
    }
}
