package co.sendik.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Banderas de funcionalidad. Permiten desplegar codigo incompleto sin exponerlo,
 * en lugar de mantener ramas de Git de larga vida.
 *
 * <p>Todas apagadas en Fase 1 (docs/operacion/configuracion.md). Cada una se
 * enciende cuando su fase entra en produccion, no antes.
 *
 * @param sellerVerification verificacion de identidad del vendedor (Fase 2)
 * @param publishing publicacion de prendas (Fase 2)
 * @param catalog catalogo publico: listado, categorias, ficha y perfil del vendedor (Fase 2)
 * @param checkout proceso de compra y pago (Fase 3)
 * @param search busqueda y filtros del catalogo (Fase 3, HU-014). Contra PostgreSQL y no
 *     contra Typesense, que queda aplazado detras del mismo puerto (ADR-0035)
 * @param spinViewer visor 360 en la ficha de producto (Fase 2)
 */
@ConfigurationProperties(prefix = "sendik.features")
public record FeatureFlags(
        boolean sellerVerification,
        boolean publishing,
        boolean catalog,
        boolean checkout,
        boolean search,
        boolean spinViewer) {}
