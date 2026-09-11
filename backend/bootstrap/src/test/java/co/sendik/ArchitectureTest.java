package co.sendik;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Segunda linea de defensa de la arquitectura.
 *
 * <p>La primera es el grafo de Gradle: {@code domain} no declara Spring, asi que
 * no puede importarlo. Estas pruebas cubren lo que el grafo no alcanza a ver, y
 * fallan tambien si alguien agrega la dependencia al build para saltarse la
 * regla. La tercera linea es el subagente revisor
 * (docs/arquitectura/vision-tecnica.md).
 *
 * <p>Las capas son modulos de Gradle, no paquetes, asi que cada regla se aplica
 * sobre las clases de un modulo concreto filtrando por su carpeta de salida.
 *
 * <p>Sobre {@code allowEmptyShould}: en Fase 1 varios modulos todavia estan
 * vacios y una regla sin clases que evaluar fallaria por eso, no por una
 * violacion. Las reglas ya estan escritas para que el dia que entre la primera
 * clase de HU-001 ya haya alguien vigilando.
 */
class ArchitectureTest {

    private static final String[] FRAMEWORKS_PROHIBIDOS_EN_EL_DOMINIO = {
        "org.springframework..", "jakarta..", "java.sql..", "org.flywaydb..", "org.testcontainers..", "lombok.."
    };

    @Test
    void el_dominio_no_depende_de_ningun_framework() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FRAMEWORKS_PROHIBIDOS_EN_EL_DOMINIO)
                .because("si manana cambia la base de datos, el marco web o la pasarela,"
                        + " el dominio no se toca. Ver docs/arquitectura/vision-tecnica.md")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("domain"));
    }

    @Test
    void la_capa_de_aplicacion_solo_usa_las_transacciones_de_spring() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat(resideInAPackage("org.springframework..")
                        .and(not(resideInAPackage("org.springframework.transaction.."))))
                .because("application orquesta casos de uso y abre transacciones."
                        + " Todo lo demas de Spring pertenece a infrastructure o presentation")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("application"));
    }

    @Test
    void la_presentacion_no_conoce_la_persistencia() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("co.sendik..persistence..", "co.sendik..client..")
                .because("un controlador delega en un caso de uso; si llega a la base de datos,"
                        + " la logica quedo en el borde")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("presentation"));
    }

    @Test
    void la_infraestructura_no_conoce_los_controladores() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik..rest..")
                .because("las dependencias apuntan hacia adentro: un adaptador nunca depende del borde HTTP")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("infrastructure"));
    }

    @Test
    void un_controlador_no_habla_directamente_con_un_repositorio() {
        ArchRule regla = noClasses()
                .that()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("entre el controlador y el repositorio va el caso de uso,"
                        + " que es quien abre la transaccion")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    @Test
    void un_caso_de_uso_no_toca_la_persistencia() {
        ArchRule regla = noClasses()
                .that()
                .resideInAPackage("co.sendik..usecase..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik..persistence..")
                .because("un caso de uso habla con un puerto que el mismo define, nunca con"
                        + " el adaptador que lo implementa")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    @Test
    void un_caso_de_uso_se_llama_caso_de_uso() {
        ArchRule regla = classes()
                .that()
                .resideInAPackage("co.sendik..usecase..")
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("UseCase")
                .because("un caso de uso por clase, con un unico metodo publico (backend/CLAUDE.md)")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    @Test
    void no_se_inyectan_dependencias_por_campo() {
        ArchRule regla = noFields()
                .should()
                .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .because("la inyeccion va por constructor y sin anotacion: deja las dependencias"
                        + " visibles y la clase construible en una prueba")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    /**
     * El atajo que deja sin efecto la regla de la configuracion tipada.
     *
     * <p>El grafo de Gradle impide que {@code presentation} importe una clase de
     * {@code @ConfigurationProperties}, que vive en {@code infrastructure}, pero no impide
     * lo equivalente: leer el valor con {@code @Value} en el borde, porque
     * {@code spring-beans} esta en su classpath por el starter de webmvc. Con eso la regla
     * de backend/CLAUDE.md -todo valor externo en una clase tipada y validada- se queda
     * escrita y nada la comprueba.
     *
     * <p>Llego con ADR-0038, donde la configuracion entra por {@code bootstrap} y la
     * correccion entera depende de que siga entrando por ahi.
     *
     * <p><strong>Cubre el parametro y no solo el campo, y esa es la mitad que importa.</strong>
     * La inyeccion por campo ya esta prohibida por {@link #no_se_inyectan_dependencias_por_campo},
     * asi que en este repositorio nadie escribiria un campo con {@code @Value}: la forma que
     * alguien escribiria de verdad es {@code ClientIpHasher(@Value("...") int saltos)}. Las dos
     * se comprueban; la del parametro necesita condicion propia porque no hay sintaxis fluida
     * para ella, no porque ArchUnit no la vea.
     */
    @Test
    void no_se_leen_valores_de_configuracion_con_value() {
        ArchRule enCampos = noFields()
                .should()
                .beAnnotatedWith(VALUE)
                .because("todo valor externo se declara en una clase @ConfigurationProperties"
                        + " tipada y validada, en infrastructure (backend/CLAUDE.md)");

        ArchRule enParametros = codeUnits()
                .should(noRecibirValorPorParametro())
                .because("todo valor externo se declara en una clase @ConfigurationProperties"
                        + " tipada y validada, en infrastructure (backend/CLAUDE.md)");

        JavaClasses clases = todasLasClasesIncluidasLasPruebas();
        enCampos.check(clases);
        enParametros.check(clases);
    }

    private static final String VALUE = "org.springframework.beans.factory.annotation.Value";

    private static ArchCondition<JavaCodeUnit> noRecibirValorPorParametro() {
        return new ArchCondition<>("no recibir ningun parametro anotado con @Value") {
            @Override
            public void check(JavaCodeUnit unidad, ConditionEvents eventos) {
                for (JavaParameter parametro : unidad.getParameters()) {
                    if (parametro.isAnnotatedWith(VALUE)) {
                        eventos.add(SimpleConditionEvent.violated(
                                unidad, unidad.getFullName() + " recibe un parametro anotado con @Value"));
                    }
                }
            }
        };
    }

    @Test
    void no_se_usa_jackson_2_sino_jackson_3() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.fasterxml.jackson..")
                .because("Spring Boot 4 trae Jackson 3, cuyo paquete es tools.jackson."
                        + " com.fasterxml es la version anterior y arrastra otro ObjectMapper")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    @Test
    void no_se_usa_resttemplate_sino_restclient() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
                .because("los clientes HTTP se escriben con RestClient o con una interfaz @HttpExchange")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    @Test
    void no_se_usan_las_anotaciones_de_simulacion_retiradas() {
        ArchRule regla = noFields()
                .should()
                .beAnnotatedWith("org.springframework.boot.test.mock.mockito.MockBean")
                .orShould()
                .beAnnotatedWith("org.springframework.boot.test.mock.mockito.SpyBean")
                .because("en Spring Boot 4 se llaman @MockitoBean y @MockitoSpyBean")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    /**
     * Los dos paquetes del dominio, no solo {@code model}.
     *
     * <p>La regla nombraba unicamente {@code ..model..} y eso la dejaba ciega ante
     * cualquier tipo de dominio que viva en otro paquete. Al agregar
     * {@code co.sendik.shared.file} —los objetos de valor de archivos e imagenes,
     * ADR-0018— un DTO de la API podia exponer una {@code FileKey} entera, es decir
     * la ruta interna del archivo dentro del almacen, y la regla no habria dicho
     * nada. Enumerar los
     * paquetes tiene ese costo: cada paquete nuevo del dominio hay que agregarlo
     * aqui, y el dia que se olvide la regla protege menos de lo que parece.
     */
    @Test
    void un_objeto_de_dominio_no_se_filtra_hacia_la_api() {
        ArchRule regla = noClasses()
                .that()
                .resideInAPackage("co.sendik..rest.dto..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("co.sendik..model..", "co.sendik.shared.file..")
                .because("la API tiene sus propios DTO aunque al principio parezcan identicos:"
                        + " asi el dominio puede cambiar sin romper el contrato publico")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    /**
     * Un controlador tampoco <strong>devuelve</strong> un tipo del dominio.
     *
     * <p>La regla de arriba mira donde reside una clase, asi que solo protege lo que ya
     * decidiste poner en {@code rest.dto}. No ve el otro camino, que es mas facil de tomar
     * sin darse cuenta: un caso de uso devuelve un tipo del dominio -{@code Listing},
     * {@code ModerationEvent}- y el metodo del controlador lo reenvia tal cual en vez de
     * pasarlo por su mapeador. Ninguna clase reside donde no debe y el objeto sale igual.
     *
     * <p><strong>Lo hizo necesario HU-013 y RN-074.</strong> Ese rastro se apoya en que
     * {@code actor_id} y {@code notes} no salen por tres sitios: la consulta no los
     * selecciona, {@code ModerationEvent} no los lleva y el DTO no tiene campo para ellos.
     * De las tres, la unica que alguien puede deshacer sin tocar nada mas es la tercera, y
     * hasta aqui nada la vigilaba: basta declarar el metodo como {@code List<ModerationEvent>}
     * y devolver lo que da el caso de uso.
     *
     * <p><strong>Mira dentro de los genericos</strong>, que es de lo que se trata: el tipo
     * crudo de {@code List<ModerationEvent>} es {@code java.util.List} y no delata nada.
     *
     * <p>No se puede escribir como «un controlador no depende de {@code ..model..}»:
     * {@code ListingsController} construye {@link co.sendik.catalog.model.ListingId} y
     * {@code SellerId} a partir de la ruta y del token, y eso es correcto. Lo que no puede
     * es devolverlos.
     */
    @Test
    void un_controlador_no_devuelve_un_tipo_del_dominio() {
        ArchRule regla = methods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .and()
                .arePublic()
                .should(noDevolverTiposDelDominio())
                .because("presentation traduce entre el mundo exterior y los casos de uso."
                        + " Un objeto de dominio reenviado tal cual convierte el modelo en el contrato")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    private static ArchCondition<JavaMethod> noDevolverTiposDelDominio() {
        return new ArchCondition<>("no devolver tipos de co.sendik..model.. ni de co.sendik.shared.file..") {
            @Override
            public void check(JavaMethod metodo, ConditionEvents eventos) {
                for (String tipo : tiposDe(metodo.getReturnType())) {
                    if (esDelDominio(tipo)) {
                        eventos.add(SimpleConditionEvent.violated(
                                metodo, metodo.getFullName() + " devuelve " + tipo + ", que es del dominio"));
                    }
                }
            }
        };
    }

    /** El tipo y, si viene parametrizado, tambien lo que lleva dentro. */
    private static List<String> tiposDe(JavaType tipo) {
        List<String> nombres = new ArrayList<>();
        nombres.add(tipo.toErasure().getName());

        if (tipo instanceof JavaParameterizedType parametrizado) {
            for (JavaType argumento : parametrizado.getActualTypeArguments()) {
                nombres.addAll(tiposDe(argumento));
            }
        }
        return nombres;
    }

    private static boolean esDelDominio(String tipo) {
        return tipo.startsWith("co.sendik.") && (tipo.contains(".model.") || tipo.startsWith("co.sendik.shared.file."));
    }

    /**
     * El dominio y la aplicacion de un contexto no conocen otro contexto.
     *
     * <p><strong>Es la regla que hace supervivible el unico ciclo que hay entre
     * contextos.</strong> Hasta HU-011 las cinco aristas iban de {@code catalog} a
     * {@code identity}; los favoritos abrieron la de vuelta, porque el cierre de cuenta y
     * la descarga de datos tienen que alcanzarlos. {@code vision-tecnica.md} lo permite
     * —«si necesita algo, es por un caso de uso publico o por un evento de dominio»— y lo
     * que lo hace tolerable es que todo ese acoplamiento viva en {@code infrastructure},
     * donde es una decision de cableado y no de modelo.
     *
     * <p>Si un dia hay que separar los dos contextos en servicios, lo que habra que
     * reescribir son adaptadores. Sin esta regla, la proxima vez podria ser el dominio.
     */
    @Test
    void el_dominio_y_la_aplicacion_de_un_contexto_no_conocen_otro_contexto() {
        for (String modulo : new String[] {"domain", "application"}) {
            comprobarQueNoSeConocen(clasesDelModulo(modulo), "catalog", "identity");
            comprobarQueNoSeConocen(clasesDelModulo(modulo), "identity", "catalog");
        }
    }

    /**
     * Y ningun contexto lee la persistencia de otro.
     *
     * <p>Es {@code vision-tecnica.md} —«el estado de un contexto no se consulta leyendo las
     * tablas de otro. Nunca»— escrito como prueba, que hasta ahora solo estaba escrito como
     * frase. Los tres adaptadores que cruzan preguntan por casos de uso publicos; esta
     * regla es la que impide que el cuarto tome el atajo.
     */
    @Test
    void ningun_contexto_lee_la_persistencia_de_otro() {
        JavaClasses todas = todasLasClases();

        noClasses()
                .that()
                .resideInAPackage("co.sendik.identity..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik.catalog.persistence..")
                .because("un contexto no consulta las tablas de otro: pregunta por un caso de uso publico")
                .allowEmptyShould(true)
                .check(todas);

        noClasses()
                .that()
                .resideInAPackage("co.sendik.catalog..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik.identity.persistence..")
                .because("un contexto no consulta las tablas de otro: pregunta por un caso de uso publico")
                .allowEmptyShould(true)
                .check(todas);
    }

    private static void comprobarQueNoSeConocen(JavaClasses clases, String contexto, String otro) {
        noClasses()
                .that()
                .resideInAPackage("co.sendik." + contexto + "..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik." + otro + "..")
                .because("el acoplamiento entre contextos vive en infrastructure, donde es cableado."
                        + " En domain o en application seria modelo, y separarlos costaria reescribirlo")
                .allowEmptyShould(true)
                .check(clases);
    }

    /** Solo las clases de produccion del modulo indicado, filtradas por su carpeta de salida. */
    private static JavaClasses clasesDelModulo(String modulo) {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(location -> location.contains("/" + modulo + "/build/"))
                .importPackages("co.sendik");
    }

    private static JavaClasses todasLasClases() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("co.sendik");
    }

    private static JavaClasses todasLasClasesIncluidasLasPruebas() {
        return new ClassFileImporter().importPackages("co.sendik");
    }
}
