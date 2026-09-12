package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static dev.lacre.verifactu.emision.AnomaliaPrevia.FECHA_DEL_ANTERIOR_EN_EL_FUTURO;
import static dev.lacre.verifactu.emision.AnomaliaPrevia.HUELLA_ANTERIOR_NO_CUADRA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La comprobación previa del art. 7.i, sin base de datos. Lo que no se admite es que el registro
 * anterior venga del futuro; que el nuevo sea muy posterior es lo normal.
 */
class ComprobacionPreviaTest {

    private static final OffsetDateTime AHORA =
            OffsetDateTime.of(2024, 1, 1, 19, 20, 30, 0, ZoneOffset.ofHours(1));

    private static final Huella UNA = new Huella("A".repeat(64));
    private static final Huella OTRA = new Huella("B".repeat(64));

    @Test
    void unaCadenaBienEncadenadaNoTieneAnomalias() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(2, OTRA, UNA, AHORA.minusMinutes(5)),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
                .isEmpty();
    }

    @Test
    void elPrimerRegistroDeLaCadenaEstaBienPorNoLlevarHuellaAnterior() {
        assertThat(ComprobacionPrevia.comprobar(enlace(1, UNA, null, AHORA.minusMinutes(5)), null, AHORA))
                .isEmpty();
    }

    // --- Requisito 1.º: el último está correctamente encadenado ---

    @Test
    void detectaQueElUltimoNoEnlazaConElQueLePrecede() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(2, OTRA, OTRA, AHORA.minusMinutes(5)),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
                .containsExactly(HUELLA_ANTERIOR_NO_CUADRA);
    }

    @Test
    void detectaQueElUltimoDiceAbrirLaCadenaSinSerElPrimero() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(2, OTRA, null, AHORA.minusMinutes(5)),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
                .containsExactly(HUELLA_ANTERIOR_NO_CUADRA);
    }

    @Test
    void detectaQueElPrimeroLlevaHuellaAnteriorSinTenerQuien() {
        assertThat(ComprobacionPrevia.comprobar(enlace(1, OTRA, UNA, AHORA.minusMinutes(5)), null, AHORA))
                .containsExactly(HUELLA_ANTERIOR_NO_CUADRA);
    }

    // --- Requisito 2.º: la fecha del último no viene del futuro ---

    @Test
    void queElRegistroNuevoSeaMuyPosteriorNoEsAnomalia() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(1, UNA, null, AHORA.minusDays(3)), null, AHORA))
                .isEmpty();
    }

    @Test
    void unMinutoDeAdelantoTodaviaSeAdmite() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(1, UNA, null, AHORA.plusMinutes(1)), null, AHORA))
                .isEmpty();
    }

    @Test
    void masDeUnMinutoDeAdelantoEsAnomalia() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(1, UNA, null, AHORA.plusMinutes(1).plusSeconds(1)), null, AHORA))
                .containsExactly(FECHA_DEL_ANTERIOR_EN_EL_FUTURO);
    }

    /** El huso no puede alterar la comparación: se comparan instantes, no horas de reloj. */
    @Test
    void compararConOtroHusoNoInventaAnomalias() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(1, UNA, null, AHORA.withOffsetSameInstant(ZoneOffset.UTC)), null, AHORA))
                .isEmpty();
    }

    @Test
    void lasDosAnomaliasSeAcumulan() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(2, OTRA, OTRA, AHORA.plusHours(1)),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
                .containsExactlyInAnyOrder(HUELLA_ANTERIOR_NO_CUADRA, FECHA_DEL_ANTERIOR_EN_EL_FUTURO);
    }

    private static CadenaDeRegistros.Enlace enlace(long posicion, Huella huella,
                                                   Huella huellaAnterior, OffsetDateTime fechaHora) {
        return new CadenaDeRegistros.Enlace(posicion, new Nif("89890001K"), "FA/" + posicion,
                LocalDate.of(2024, 1, 1), huella, huellaAnterior, fechaHora);
    }
}
