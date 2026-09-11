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
 * Reglas ArchUnit de la arquitectura hexagonal por módulo: qué puede depender de Spring y de
 * base de datos, quién conoce a quién y qué está prohibido en el stack.
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
     * Los {@code package-info} del núcleo quedan exentos: declarar las interfaces publicadas
     * exige la anotación de Modulith.
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
     * Acota la exención anterior: en esos {@code package-info} solo se admiten anotaciones de
     * Modulith.
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

    /** Los conversores de Spring Data JDBC de los value objects viven en la configuración. */
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

    /** El núcleo no conoce a ningún otro módulo. */
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

    /** {@code remision} solo ve a {@code verifactu} por su API publicada. */
    @Test
    void remisionNoEntraEnElInteriorDelNucleo() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.remision..")
                .should().dependOnClassesThat().resideInAPackage("dev.lacre.verifactu.internal..")
                .because("la API pública del núcleo es su paquete raíz");

        regla.check(clases);
    }

    /** {@code identidad} no depende de ningún otro módulo. */
    @Test
    void identidadNoConoceAlRestoDeModulos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.identidad..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.lacre.verifactu..", "dev.lacre.remision..", "dev.lacre.api..")
                .because("identidad es el suelo de los obligados; la dependencia va en un solo sentido");

        regla.check(clases);
    }

    /** Paramétrica sobre cada módulo con {@code internal}; añadir un módulo es añadir su nombre. */
    @ParameterizedTest
    @ValueSource(strings = {"verifactu", "identidad", "remision", "api"})
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
     * Toda la aplicación recibe el instante y los identificadores inyectados; solo
     * {@code ConfiguracionComun} los produce. No prohíbe {@code OffsetDateTime.now(Clock)}. La
     * exención de {@code ConfiguracionComun} es decorativa: ArchUnit no ve la referencia a
     * método {@code UUID::randomUUID}.
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
