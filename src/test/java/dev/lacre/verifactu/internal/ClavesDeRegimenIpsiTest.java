package dev.lacre.verifactu.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.Registros;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClavesDeRegimenIpsiTest {

    private static final LocalDate ULTIMO_DIA_DE_AVISO = LocalDate.of(2026, 12, 31);
    private static final LocalDate PRIMER_DIA_DE_RECHAZO = LocalDate.of(2027, 1, 1);

    @Test
    void hastaEl31DeDiciembreDe2026NoSeExige() {
        assertThatCode(() -> ClavesDeRegimenIpsi.exigir(conLineaIpsi(null), ULTIMO_DIA_DE_AVISO))
                .doesNotThrowAnyException();
        assertThatCode(() -> ClavesDeRegimenIpsi.exigir(conLineaIpsi("02"), ULTIMO_DIA_DE_AVISO))
                .doesNotThrowAnyException();
    }

    @Test
    void desdeEl1DeEneroDe2027SinClaveSeRechaza() {
        assertThatThrownBy(() -> ClavesDeRegimenIpsi.exigir(conLineaIpsi(null), PRIMER_DIA_DE_RECHAZO))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1245"));
    }

    @Test
    void desdeEl1DeEneroDe2027UnaClaveFueraDeLaListaSeRechaza() {
        assertThatThrownBy(() -> ClavesDeRegimenIpsi.exigir(conLineaIpsi("02"), PRIMER_DIA_DE_RECHAZO))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1246"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "08", "11", "18", "19", "20"})
    void desdeEl1DeEneroDe2027LasClavesDeLaListaSeAdmiten(String clave) {
        assertThatCode(() -> ClavesDeRegimenIpsi.exigir(conLineaIpsi(clave), PRIMER_DIA_DE_RECHAZO))
                .doesNotThrowAnyException();
    }

    @Test
    void lasLineasDeOtrosImpuestosNoSeMiran() {
        assertThatCode(() -> ClavesDeRegimenIpsi.exigir(Registros.alta().build(), PRIMER_DIA_DE_RECHAZO))
                .doesNotThrowAnyException();
    }

    private static DatosRegistroAlta conLineaIpsi(String clave) {
        return Registros.alta().desglose(Desglose.de(lineaIpsi(clave))).build();
    }

    /** Línea de IPSI que el modelo admite con esa clave: la 08 y la 20 van no sujetas por localización. */
    private static DetalleDesglose lineaIpsi(String clave) {
        boolean noSujeta = "08".equals(clave) || "20".equals(clave);
        return new DetalleDesglose(
                Impuesto.IPSI,
                clave == null ? null : new ClaveRegimen(clave),
                noSujeta ? CalificacionOperacion.N2 : CalificacionOperacion.S1,
                noSujeta ? null : Porcentaje.de("10"),
                Importe.de("111.10"),
                null,
                noSujeta ? null : Importe.de("12.35"),
                null,
                null);
    }
}
