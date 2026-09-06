package co.sendik.shared.port.out;

/**
 * Entrega un correo al proveedor y **dice si salio**.
 *
 * <p>Existe por una razon muy concreta y vale la pena dejarla escrita, porque sin ella
 * ADR-0031 no funciona. {@link MailTransport} promete no lanzar: un correo que no sale no
 * puede tumbar la operacion que lo provoco, y por eso su adaptador registra el fallo y
 * sigue. Esa promesa es la correcta para quien encola, y es exactamente la equivocada para
 * quien entrega: si la peticion que Cloud Tasks hace al terminar la tarea siempre responde
 * bien, el servicio de colas da la tarea por entregada y **no reintenta nunca**, que es
 * justo lo que se compro al elegirlo.
 *
 * <p>De ahi que sea un puerto aparte y no un metodo mas del otro. Los dos los implementa
 * el mismo adaptador, pero se piden desde sitios distintos: {@code MailTransport} lo pide
 * quien manda un correo, y este lo pide **solo** el endpoint que recibe la tarea.
 *
 * <p>Que lo implemente unicamente el transporte directo -y nunca el que encola- es lo que
 * hace imposible el bucle: quien entrega no puede volver a encolar porque el tipo que
 * recibe no ofrece esa operacion.
 */
public interface MailDelivery {

    /**
     * @return {@code true} si la tarea esta terminada: o el proveedor acepto el correo, o
     *     lo rechazo de forma definitiva y volver a intentarlo solo serviria para recibir
     *     el mismo no. {@code false} si el fallo fue transitorio -un corte de red, un 5xx-
     *     y merece que la cola lo reintente mas tarde.
     */
    boolean entregar(String destinatario, String asunto, String cuerpoHtml);
}
