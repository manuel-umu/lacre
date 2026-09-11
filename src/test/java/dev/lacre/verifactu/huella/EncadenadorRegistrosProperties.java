package dev.lacre.verifactu.huella;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.Registros;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Invariantes de la cadena para cualquier huella anterior. */
class EncadenadorRegistrosProperties {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final EncadenadorRegistros ENCADENADOR = new EncadenadorRegistros(
            Clock.fixed(Instant.parse("2024-01-01T18:20:30Z"), ZoneOffset.UTC),
            new CanonicalizadorAeat());

    private static final DatosRegistroAlta DATOS = Registros.alta().build();

    @Provide
    Arbitrary<Huella> huellas() {
        return Arbitraries.strings().withChars("0123456789ABCDEF").ofLength(64).map(Huella::new);
    }

    @Property
    void huellasAnterioresDistintasProducenRegistrosDistintos(@ForAll("huellas") Huella una,
                                                              @ForAll("huellas") Huella otra) {
        Assume.that(!una.equals(otra));

        assertThat(ENCADENADOR.encadenar(DATOS, Optional.of(Registros.anterior(una)), MADRID).huella())
                .isNotEqualTo(ENCADENADOR.encadenar(DATOS, Optional.of(Registros.anterior(otra)), MADRID).huella());
    }

    @Property
    void laHuellaAnteriorSiempreEntraEnLaCadenaCanonica(@ForAll("huellas") Huella anterior) {
        String cadena = new CanonicalizadorAeat().canonicalizar(
                DATOS, Optional.of(Registros.anterior(anterior)), ENCADENADOR.encadenar(DATOS, Optional.empty(), MADRID)
                        .fechaHoraHusoGenRegistro());

        assertThat(cadena).contains("&Huella=" + anterior.valor() + "&");
    }

    @Property
    void encadenarEsDeterministaParaLaMismaHuellaAnterior(@ForAll("huellas") Huella anterior) {
        assertThat(ENCADENADOR.encadenar(DATOS, Optional.of(Registros.anterior(anterior)), MADRID))
                .isEqualTo(ENCADENADOR.encadenar(DATOS, Optional.of(Registros.anterior(anterior)), MADRID));
    }

    @Property
    void ningunaHuellaAnteriorProduceLaHuellaDelPrimerRegistro(@ForAll("huellas") Huella anterior) {
        assertThat(ENCADENADOR.encadenar(DATOS, Optional.of(Registros.anterior(anterior)), MADRID).huella())
                .isNotEqualTo(ENCADENADOR.encadenar(DATOS, Optional.empty(), MADRID).huella());
    }
}
