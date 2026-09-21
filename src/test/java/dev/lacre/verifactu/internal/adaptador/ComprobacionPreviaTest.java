package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static dev.lacre.verifactu.emision.AnomaliaPrevia.FECHA_DEL_ANTERIOR_EN_EL_FUTURO;
import static dev.lacre.verifactu.emision.AnomaliaPrevia.HUELLA_ANTERIOR_NO_CUADRA;
import static dev.lacre.verifactu.emision.AnomaliaPrevia.IDENTIFICACION_ANTERIOR_NO_CUADRA;
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

    @Test
    void detectaQueElUltimoDeclaraComoAnteriorOtraFactura() {
        CadenaDeRegistros.Enlace ultimo = enlace(2, OTRA, UNA, AHORA.minusMinutes(5));

        assertThat(ComprobacionPrevia.comprobar(
                conXml(ultimo, xml(2, OTRA, idFactura(9), UNA)),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
                .containsExactly(IDENTIFICACION_ANTERIOR_NO_CUADRA);
    }

    @Test
    void unXmlIlegibleNoDejaComprobarLaIdentificacion() {
        CadenaDeRegistros.Enlace ultimo = enlace(2, OTRA, UNA, AHORA.minusMinutes(5));

        assertThat(ComprobacionPrevia.comprobar(
                conXml(ultimo, "<x/>"),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
                .containsExactly(IDENTIFICACION_ANTERIOR_NO_CUADRA);
    }

    /** Huella e identificación se comprueban por separado: puede romperse solo una. */
    @Test
    void unaHuellaAnteriorFalsaConLaIdentificacionBuenaSoloDenunciaLaHuella() {
        assertThat(ComprobacionPrevia.comprobar(
                enlace(2, OTRA, OTRA, AHORA.minusMinutes(5)),
                enlace(1, UNA, null, AHORA.minusMinutes(10)),
                AHORA))
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

    /** Un registro cuyo XML declara como anterior la factura de la posición previa. */
    private static CadenaDeRegistros.Enlace enlace(long posicion, Huella huella,
                                                   Huella huellaAnterior, OffsetDateTime fechaHora) {
        IdFactura anterior = huellaAnterior == null ? null : idFactura(posicion - 1);
        return new CadenaDeRegistros.Enlace(posicion, Registros.EMISOR, "FA/" + posicion,
                Registros.FECHA_EXPEDICION, huella, huellaAnterior, fechaHora,
                xml(posicion, huella, anterior, huellaAnterior));
    }

    private static CadenaDeRegistros.Enlace conXml(CadenaDeRegistros.Enlace enlace, String xml) {
        return new CadenaDeRegistros.Enlace(enlace.posicion(), enlace.emisor(),
                enlace.numSerieFactura(), enlace.fechaExpedicion(), enlace.huella(),
                enlace.huellaAnterior(), enlace.fechaHora(), xml);
    }

    private static String xml(long posicion, Huella huella, IdFactura anterior,
                              Huella huellaAnterior) {
        return EscritorRegistro.escribir(new RegistroEncadenado(
                Registros.alta().idFactura(idFactura(posicion)).build(),
                Optional.ofNullable(anterior).map(id -> new RegistroAnterior(id, huellaAnterior)),
                AHORA, huella));
    }

    private static IdFactura idFactura(long posicion) {
        return new IdFactura(Registros.EMISOR, "FA/" + posicion, Registros.FECHA_EXPEDICION);
    }
}
