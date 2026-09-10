package co.sendik.shared.rest;

/**
 * Que funcionalidades estan expuestas, para las reglas de la cadena de seguridad.
 *
 * <p>Existe porque una regla de autorizacion se evalua en el filtro, <strong>antes</strong>
 * de que nadie busque un manejador: una ruta protegida por rol responde 403 aunque su
 * controlador no exista, y un 403 confirma que la funcionalidad esta ahi. Eso es
 * exactamente lo que una bandera existe para no decir, y lo que el criterio 3 de HU-007
 * prohibe, y lo mismo dice HU-002 de la suya. Con esto, la regla solo se declara si la
 * ruta va a existir.
 *
 * <p><strong>El valor lo aporta {@code bootstrap}</strong>, que es el modulo del cableado y
 * el unico que ve {@code FeatureFlags}: {@code presentation} no puede leer configuracion
 * —vive en {@code infrastructure}— y {@code @Value} suelto esta prohibido.
 *
 * <p>Vive en {@code shared} y no en el paquete de cada contexto por la direccion de las
 * dependencias: la cadena de seguridad es comun, y no puede necesitar a {@code catalog}
 * para compilar.
 */
public record ExposedFeatures(boolean sellerVerification, boolean publishing, boolean catalog, boolean checkout) {}
