package co.sendik.identity.dto;

import co.sendik.identity.model.User;
import org.jspecify.annotations.Nullable;

/**
 * El perfil tal como lo ve su dueno, con la ciudad ya resuelta. HU-017, criterios 10 a 12.
 *
 * <p>Hasta HU-017 el perfil era la cuenta sin mas y el borde la traducia directo. Desde
 * entonces la ciudad tiene dos fuentes: lo que la persona escribio a mano, mientras no
 * tenga direccion de origen, y el municipio del origen con su departamento al lado, cuando
 * la tiene (ADR-0042). Resolver cual es cosa de un caso de uso, porque una de las dos
 * habla con un puerto, y el borde recibe el resultado.
 *
 * @param ciudad la que se muestra: escrita a mano, derivada del origen, o ninguna
 * @param ciudadEditable falso cuando hay origen: entonces se cambia desde el origen y no
 *     desde el perfil
 */
public record ProfileView(User cuenta, @Nullable String ciudad, boolean ciudadEditable) {}
