package co.sendik.catalog.rest.dto;

import java.util.List;

/**
 * Como quedo el carrito tras la fusion, y que no entro. HU-015, criterios 9 y 10.
 *
 * <p><strong>{@code notMerged} se publica y no se calla.</strong> Si la union supera el tope
 * de RN-097 se conserva hasta el tope, lo mas reciente primero, y quien acaba de entrar tiene
 * derecho a saber que algo se quedo fuera. Una fusion que adelgaza sin avisar es el mismo
 * defecto que RN-094 evita en la pantalla.
 *
 * <p><strong>Son identificadores y no motivos.</strong> Lo que no cupo por el tope, lo que ya
 * no esta publicado y lo que resulta ser de quien entra salen mezclados y sin distinguirse,
 * por lo mismo que el criterio 22: decir cual de las tres cosas paso seria decir sobre esos
 * identificadores lo que RN-068 no deja decir en ninguna otra parte.
 *
 * <p>El navegador los usa para borrar de su copia local lo que ya no tiene sentido guardar.
 */
public record MergeCartResponse(CartResponse cart, List<String> notMerged) {}
