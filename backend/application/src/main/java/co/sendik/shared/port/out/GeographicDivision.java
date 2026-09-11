package co.sendik.shared.port.out;

import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La division politico-administrativa de Colombia, tal como la publica el DANE. HU-016,
 * RN-100.
 *
 * <p>Solo se lee. Se siembra con la migracion V20 y se vuelve a sembrar con otra el dia que
 * el DANE cree, suprima o renombre algo; no hay ningun camino por el que la aplicacion
 * escriba aqui.
 *
 * <p><strong>Vive en {@code shared} y no en {@code identity}</strong>, al reves que el
 * repositorio de direcciones: la lista no es de nadie. Hoy la usa el formulario de
 * direccion de entrega y manana la usara el cotizador de envios, que estara en
 * {@code shipping} (ADR-0040).
 *
 * <p>Es el mismo sitio donde ya vive {@code SensitiveDataCipher}, y por la misma razon:
 * un puerto que no pertenece a un contexto concreto.
 */
public interface GeographicDivision {

    /**
     * Los departamentos, ordenados por nombre.
     *
     * <p>Sin paginar y sin filtro: son treinta y tres y caben en una respuesta. Van todos,
     * incluido Bogota D.C., que en la Divipola es departamento y municipio a la vez.
     */
    List<Department> departamentos();

    /**
     * Los municipios <strong>activos</strong> de un departamento, ordenados por nombre.
     *
     * <p>Activos y no todos: es lo que alimenta el selector, y ofrecer un municipio que el
     * DANE suprimio seria dejar guardar una direccion que ya no se puede cotizar. Los
     * inactivos siguen leyendose por {@link #buscarMunicipios}, que es lo que el criterio
     * 23 necesita.
     */
    List<Municipality> municipiosActivosDe(DepartmentCode departamento);

    /**
     * Uno, para comprobar antes de guardar.
     *
     * <p>Devuelve <strong>tambien los inactivos</strong>, y quien decide que un inactivo no
     * se puede elegir es el caso de uso: el puerto responde que existe, la regla es de
     * arriba. Asi el mismo metodo sirve para validar al escribir y para leer una direccion
     * vieja.
     */
    Optional<Municipality> buscarMunicipio(MunicipalityCode codigo);

    /**
     * Varios de golpe, para pintar una libreta entera sin una consulta por fila.
     *
     * <p>Incluye los inactivos: una direccion guardada sobre un municipio suprimido se
     * sigue leyendo con su nombre (criterio 23). Los codigos que no existan simplemente no
     * salen en el mapa.
     */
    Map<MunicipalityCode, Municipality> buscarMunicipios(Collection<MunicipalityCode> codigos);
}
