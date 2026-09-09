package co.sendik.catalog.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Que la busqueda existe. HU-014, criterio 26.
 *
 * <p><strong>Es un bean que solo esta cuando {@code FEATURE_SEARCH} esta encendida</strong>,
 * y no tiene nada dentro: su presencia es toda la informacion. El catalogo lo pide como
 * opcional y, si no esta, responde 404 a cualquier peticion con parametros de busqueda.
 *
 * <p><strong>Por que no basta con el patron de las otras tres banderas.</strong> Alli la
 * bandera apagada hace que el controlador no se cree y la ruta entera desaparezca. Aqui no
 * se puede: {@code GET /api/v1/listings} es tambien el catalogo de HU-009, que tiene su
 * propia bandera y tiene que seguir respondiendo. Lo que desaparece no es la ruta sino la
 * capacidad de buscar en ella.
 *
 * <p>El resultado hacia afuera es el mismo que el de las otras tres, y eso importa: un 404
 * con {@code COMMON_NOT_FOUND}, byte por byte igual al que Spring devuelve cuando un
 * controlador no existe. Un 403 confirmaria que la busqueda esta ahi, apagada.
 */
@Component
@ConditionalOnProperty(prefix = "sendik.features", name = "search", havingValue = "true")
public class SearchFeature {}
