package co.sendik.shared.mail;

/**
 * El cuerpo de la tarea que viaja por Cloud Tasks.
 *
 * <p>Su gemelo al otro lado es {@code MailDeliveryRequest}, en {@code presentation}. Son
 * dos registros con los mismos tres campos y **no se comparte uno solo** por la misma
 * regla que separa los DTO de la API de los del dominio: uno es lo que este adaptador
 * escribe y el otro es lo que el borde acepta y valida. Compartirlo ataria
 * {@code presentation} a un detalle de {@code infrastructure}, que es una dependencia que
 * el grafo de modulos no permite.
 *
 * <p>Lo que si tienen que compartir son los nombres de los campos, porque entre los dos hay
 * JSON. Eso no lo puede garantizar el compilador, asi que lo comprueba una prueba.
 *
 * <p><strong>Aqui viaja el enlace de verificacion en claro</strong>, dentro del cuerpo HTML.
 * Es la concesion que ADR-0031 anota y acota: el token no se puede resumir porque es
 * justo lo que la persona tiene que recibir, la cola no es publica, y la tarea se borra al
 * entregarse.
 */
record MailTaskPayload(String destinatario, String asunto, String cuerpoHtml) {}
