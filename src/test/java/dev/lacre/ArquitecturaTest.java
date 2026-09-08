package dev.lacre;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas que hacen verificable la arquitectura hexagonal por módulo del
 * <a href="../../../docs/adr/0002-arquitectura-hexagonal-por-modulo.md">ADR 0002</a>.
 * <p>
 * La primera es la que sostiene todo lo demás: si el núcleo de {@code verifactu} no depende de
 * Spring, se puede publicar en Maven Central; si un día deja de cumplirse, este test se pone
 * rojo antes de que nadie lo descubra al intentar extraer la librería.
 */
class ArquitecturaTest {

    private static final String ADAPTADORES = "..verifactu.internal.adaptador..";

    private static JavaClasses clases;

    @BeforeAll
    static void importarProduccion() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.lacre");
    }

    // --- Lo que hace publicable la librería ---

    /**
     * La excepción de {@code package-info} es deliberada y está acotada por la regla siguiente.
     * Declarar las interfaces con nombre de Modulith exige anotar el paquete, y no hay otra
     * forma de hacerlo. Es metadato: un {@code package-info} no contiene código, y una anotación
     * cuya clase no esté en el classpath la ignora la JVM en silencio, así que el artefacto
     * publicado puede declarar {@code spring-modulith-api} como dependencia opcional sin que
     * quien lo use tenga que arrastrar Modulith.
     */
    @Test
    void elNucleoNoDependeDeSpring() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.verifactu..")
                .and().resideOutsideOfPackage(ADAPTADORES)
                .and().doNotHaveSimpleName("package-info")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("el núcleo del módulo se publica en Maven Central y no puede exigir Spring; "
                        + "los adaptadores van en " + ADAPTADORES);

        regla.check(clases);
    }

    /**
     * Mantiene estrecha la excepción de arriba: en los {@code package-info} del núcleo solo se
     * admiten las anotaciones de Modulith que declaran las interfaces publicadas. Cualquier otra
     * cosa de Spring ahí sería colar una dependencia por la puerta de atrás.
     */
    @Test
    void loUnicoDeSpringEnElNucleoSonLasAnotacionesDeModulith() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.verifactu..")
                .and().resideOutsideOfPackage(ADAPTADORES)
                .and().haveSimpleName("package-info")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "son de Spring pero no de Modulith",
                                clase -> clase.getPackageName().startsWith("org.springframework")
                                        && !clase.getPackageName().startsWith("org.springframework.modulith")))
                .because("la excepción cubre solo la declaración de interfaces con nombre");

        regla.allowEmptyShould(true).check(clases);
    }

    @Test
    void elNucleoNoDependeDeNingunaBaseDeDatos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.verifactu..")
                .and().resideOutsideOfPackage(ADAPTADORES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "javax.sql..", "org.postgresql..", "org.flywaydb..")
                .because("la librería publicada no puede exigir una base de datos");

        regla.check(clases);
    }

    @Test
    void sharedNoDependeDeSpringNiDeBaseDeDatos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "java.sql..", "javax.sql..", "org.postgresql..")
                .because("shared viaja con la librería, así que carga con la misma restricción");

        regla.check(clases);
    }

    /**
     * Los conversores {@code @WritingConverter}/{@code @ReadingConverter} de los value objects
     * son código de adaptador, no de dominio: viven junto a la configuración de la aplicación.
     */
    @Test
    void sharedNoConoceANingunModulo() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.lacre.verifactu..", "dev.lacre.facturacion..",
                        "dev.lacre.identidad..", "dev.lacre.remision..")
                .because("shared es el suelo sobre el que se apoyan los módulos, no al revés");

        regla.check(clases);
    }

    // --- Fronteras entre módulos ---

    /**
     * El núcleo no conoce a nadie. Es lo que permite extraerlo como librería: quien la use no
     * arrastra ni la identidad de los obligados ni el envío a la AEAT.
     * <p>
     * Sustituye a la regla que vigilaba la frontera con {@code facturacion}, módulo que
     * desapareció al pasar lacre a ser un componente integrado en el ERP: quien expide la
     * factura es el ERP, no nosotros.
     */
    @Test
    void elNucleoNoConoceAlRestoDeModulos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.verifactu..")
                .and().resideOutsideOfPackage(ADAPTADORES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.lacre.identidad..", "dev.lacre.remision..", "dev.lacre.api..")
                .because("el núcleo se publica solo; se comunica con eventos, no con imports");

        regla.check(clases);
    }

    /**
     * La dirección que de verdad importa: {@code remision} puede escuchar los eventos que
     * publica {@code verifactu}, pero solo a través de su API publicada.
     */
    @Test
    void remisionNoEntraEnElInteriorDelNucleo() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.remision..")
                .should().dependOnClassesThat().resideInAPackage("dev.lacre.verifactu.internal..")
                .because("la API pública del núcleo es su paquete raíz");

        regla.check(clases);
    }

    /**
     * {@code identidad} solo dice quién es el obligado y con qué zona se fechan sus registros.
     * Si un día importase {@code verifactu}, el ciclo dejaría a los dos módulos inseparables.
     */
    @Test
    void identidadNoConoceAlRestoDeModulos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.identidad..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.lacre.verifactu..", "dev.lacre.remision..", "dev.lacre.api..")
                .because("identidad es el suelo de los obligados; la dependencia va en un solo sentido");

        regla.check(clases);
    }

    /**
     * Vale para todos los módulos que tengan {@code internal}, no solo para el núcleo: un módulo
     * cuyo interior se pueda tocar desde fuera deja de poder cambiar sin romper a nadie. Añadir
     * un módulo es añadir su nombre aquí.
     */
    @ParameterizedTest
    @ValueSource(strings = {"verifactu", "remision"})
    void nadieDeFueraDelModuloEntraEnSuPaqueteInterno(String modulo) {
        ArchRule regla = noClasses()
                .that().resideOutsideOfPackage("dev.lacre." + modulo + "..")
                .should().dependOnClassesThat().resideInAPackage("dev.lacre." + modulo + ".internal..")
                .because("la API pública de " + modulo + " es su paquete raíz");

        regla.check(clases);
    }

    // --- Prohibiciones del stack ---

    @Test
    void nadieUsaJpa() {
        ArchRule regla = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javax.persistence..", "jakarta.persistence..", "org.hibernate..")
                .because("la única estrategia de persistencia del proyecto es Spring Data JDBC");

        regla.check(clases);
    }

    /**
     * Ya no es solo el dominio de {@code verifactu}: <strong>toda</strong> la aplicación recibe el
     * instante y los identificadores inyectados, y la única clase autorizada a producirlos es
     * {@code ConfiguracionComun}. Se amplió al escribir {@code remision}, cuyo oyente también
     * fecha filas: una segunda fuente de indeterminismo escondida en otro módulo es igual de
     * mala aunque no entre en la huella.
     * <p>
     * Ojo, no prohíbe {@code OffsetDateTime.now(Clock)}, que es precisamente lo que hay que
     * usar: la regla persigue las variantes sin argumentos, que leen el reloj del sistema.
     * <p>
     * La exención de {@code ConfiguracionComun} <strong>hoy no hace nada</strong>, y se comprobó:
     * ArchUnit no ve la referencia a método {@code UUID::randomUUID}, solo las llamadas
     * directas. Se deja porque escribirla como lambda sería legítimo y pondría roja la única
     * clase que tiene permiso para producir indeterminismo.
     */
    @Test
    void soloUnaClaseProduceIndeterminismo() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre..")
                .and().doNotHaveSimpleName("ConfiguracionComun")
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.time.OffsetDateTime.class, "now")
                .orShould().callMethod(java.util.UUID.class, "randomUUID")
                .because("el reloj y el generador de identificadores se inyectan, y la fecha y "
                        + "hora entra en el cálculo de la huella: debe ser reproducible");

        regla.check(clases);
    }
}
