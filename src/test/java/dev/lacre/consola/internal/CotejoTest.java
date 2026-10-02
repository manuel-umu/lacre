package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.RegistroEnAeat;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.consulta.RegistroDeFactura;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CotejoTest {

    private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9);
    private static final Nif EMISOR = new Nif("89890001K");
    private static final Huella H1 = huella('1');
    private static final Huella H2 = huella('2');

    private final List<RegistroDeFactura> locales = new ArrayList<>();
    private final Map<UUID, EstadoEnvio> estados = new HashMap<>();
    private final List<RegistroEnAeat> enAeat = new ArrayList<>();

    @Test
    void lasMismasHuellasCoincidenYElPeriodoCuadra() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        aeat("FA/1", H1, RegistroEnAeat.Estado.CORRECTO);

        Cotejo cotejo = cotejar();

        assertThat(cotejo.coinciden()).containsExactly(factura("FA/1"));
        assertThat(cotejo.cuadra()).isTrue();
    }

    @Test
    void valeLaHuellaDeLaUltimaSubsanacionAceptada() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO_CON_ERRORES);
        local("FA/1", TipoRegistro.ALTA, H2, EstadoEnvio.ACEPTADO);
        aeat("FA/1", H2, RegistroEnAeat.Estado.CORRECTO);

        assertThat(cotejar().coinciden()).containsExactly(factura("FA/1"));
    }

    @Test
    void unaSubsanacionRechazadaNoCuentaYLaAeatSigueConLaAnterior() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        local("FA/1", TipoRegistro.ALTA, H2, EstadoEnvio.RECHAZADO);
        aeat("FA/1", H1, RegistroEnAeat.Estado.CORRECTO);

        assertThat(cotejar().cuadra()).isTrue();
    }

    @Test
    void otraHuellaVigenteEsUnaDiscrepancia() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        aeat("FA/1", H2, RegistroEnAeat.Estado.CORRECTO);

        assertThat(cotejar().discrepancias())
                .singleElement()
                .satisfies(d -> assertThat(d.motivo()).contains("huella"));
    }

    @Test
    void unaAnulacionAceptadaCoincideConUnAnuladoConLaHuellaDeLaAnulacion() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        local("FA/1", TipoRegistro.ANULACION, H2, EstadoEnvio.ACEPTADO);
        aeat("FA/1", H2, RegistroEnAeat.Estado.ANULADO);

        assertThat(cotejar().coinciden()).containsExactly(factura("FA/1"));
    }

    @Test
    void unAnuladoConLaHuellaDelAltaEsUnaDiscrepancia() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        local("FA/1", TipoRegistro.ANULACION, H2, EstadoEnvio.ACEPTADO);
        aeat("FA/1", H1, RegistroEnAeat.Estado.ANULADO);

        assertThat(cotejar().discrepancias())
                .singleElement()
                .satisfies(d -> assertThat(d.motivo()).contains("huella"));
    }

    @Test
    void anuladaEnUnSitioYNoEnElOtroEsUnaDiscrepancia() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        aeat("FA/1", H1, RegistroEnAeat.Estado.ANULADO);
        local("FA/2", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);
        local("FA/2", TipoRegistro.ANULACION, H2, EstadoEnvio.ACEPTADO);
        aeat("FA/2", H1, RegistroEnAeat.Estado.CORRECTO);

        assertThat(cotejar().discrepancias())
                .extracting(Cotejo.Discrepancia::motivo)
                .containsExactlyInAnyOrder(
                        "la AEAT la tiene anulada y lacre no", "lacre la anuló y la AEAT no la tiene anulada");
    }

    @Test
    void unaFacturaQueLacreNoTieneAceptadaEsUnaDiscrepancia() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.PENDIENTE);
        aeat("FA/1", H1, RegistroEnAeat.Estado.CORRECTO);

        assertThat(cotejar().discrepancias())
                .singleElement()
                .satisfies(d -> assertThat(d.motivo()).contains("ningún registro"));
    }

    @Test
    void loQueSoloEstaEnLaAeatSeInformaAparte() {
        aeat("FA/1", H1, RegistroEnAeat.Estado.CORRECTO);

        assertThat(cotejar().soloEnAeat()).extracting(RegistroEnAeat::idFactura).containsExactly(factura("FA/1"));
    }

    @Test
    void unaFacturaAceptadaDelPeriodoQueLaAeatNoDevuelveEsSoloDeLacre() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.ACEPTADO);

        assertThat(cotejar().soloEnLacre())
                .extracting(RegistroDeFactura::idFactura)
                .containsExactly(factura("FA/1"));
    }

    @Test
    void niLasPendientesNiLasRechazadasNiLasDeOtroPeriodoSonSoloDeLacre() {
        local("FA/1", TipoRegistro.ALTA, H1, EstadoEnvio.PENDIENTE);
        local("FA/2", TipoRegistro.ALTA, H1, EstadoEnvio.RECHAZADO);
        locales.add(new RegistroDeFactura(
                UUID.randomUUID(),
                locales.size() + 1,
                TipoRegistro.ALTA,
                factura("FA/3"),
                H1,
                LocalDate.of(2026, 8, 31)));
        estados.put(locales.getLast().id(), EstadoEnvio.ACEPTADO);

        assertThat(cotejar().soloEnLacre()).isEmpty();
    }

    @Test
    void unCotejoParcialLoDice() {
        assertThat(Cotejo.de(SEPTIEMBRE, List.of(), false, List.of(), Map.of()).completo())
                .isFalse();
    }

    private Cotejo cotejar() {
        return Cotejo.de(SEPTIEMBRE, enAeat, true, locales, estados);
    }

    private void local(String numSerie, TipoRegistro tipo, Huella huella, EstadoEnvio estado) {
        RegistroDeFactura registro =
                new RegistroDeFactura(UUID.randomUUID(), locales.size() + 1, tipo, factura(numSerie), huella, null);
        locales.add(registro);
        estados.put(registro.id(), estado);
    }

    private void aeat(String numSerie, Huella huella, RegistroEnAeat.Estado estado) {
        enAeat.add(new RegistroEnAeat(factura(numSerie), huella, estado, null, null, null));
    }

    private static IdFactura factura(String numSerie) {
        return new IdFactura(EMISOR, numSerie, LocalDate.of(2026, 9, 15));
    }

    private static Huella huella(char digito) {
        return new Huella(String.valueOf(digito).repeat(64));
    }
}
