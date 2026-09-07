package dev.lacre;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas que hacen verificable la arquitectura hexagonal por módulo del
 * <a href="../../../docs/adr/0002-arquitectura-hexagonal-por-modulo.md">ADR 0002</a>.
 * <p>
 * La primera es la que sostiene todo lo demás: si el núcleo de {@code sif} no depende de
 * Spring, se puede publicar en Maven Central; si un día deja de cumplirse, este test se pone
 * rojo antes de que nadie lo descubra al intentar extraer la librería.
 */
class ArquitecturaTest {

    private static final String ADAPTADORES = "..sif.internal.adaptador..";

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
    void elNucleoDeSifNoDependeDeSpring() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.sif..")
                .and().resideOutsideOfPackage(ADAPTADORES)
                .and().doNotHaveSimpleName("package-info")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("el núcleo de sif se publica en Maven Central y no puede exigir Spring; "
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
                .that().resideInAPackage("dev.lacre.sif..")
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
    void elNucleoDeSifNoDependeDeNingunaBaseDeDatos() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.sif..")
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
                        "dev.lacre.sif..", "dev.lacre.facturacion..",
                        "dev.lacre.identidad..", "dev.lacre.remision..")
                .because("shared es el suelo sobre el que se apoyan los módulos, no al revés");

        regla.check(clases);
    }

    // --- Fronteras entre módulos ---

    @Test
    void sifYFacturacionNoSeConocen() {
        noClasses().that().resideInAPackage("dev.lacre.sif..")
                .should().dependOnClassesThat().resideInAPackage("dev.lacre.facturacion..")
                .because("la frontera entre ambos es lo que permite extraer sif; "
                        + "se cruza con eventos de dominio, no con imports")
                .check(clases);

        // allowEmptyShould porque el módulo facturacion todavía no existe: la regla queda puesta
        // para que el día que exista no se pueda cruzar la frontera sin que este test avise.
        noClasses().that().resideInAPackage("dev.lacre.facturacion..")
                .should().dependOnClassesThat().resideInAPackage("dev.lacre.sif..")
                .allowEmptyShould(true)
                .check(clases);
    }

    @Test
    void nadieDeFueraDeSifEntraEnSuPaqueteInterno() {
        ArchRule regla = noClasses()
                .that().resideOutsideOfPackage("dev.lacre.sif..")
                .should().dependOnClassesThat().resideInAPackage("dev.lacre.sif.internal..")
                .because("la API pública del módulo es su paquete raíz");

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

    @Test
    void elDominioNoLeeElRelojDelSistema() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("dev.lacre.sif..")
                .and().resideOutsideOfPackage(ADAPTADORES)
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.time.OffsetDateTime.class, "now")
                .orShould().callMethod(java.util.UUID.class, "randomUUID")
                .because("el dominio recibe un Clock y un generador de identificadores; "
                        + "la fecha y hora entra en el cálculo de la huella y debe ser reproducible");

        regla.check(clases);
    }
}
