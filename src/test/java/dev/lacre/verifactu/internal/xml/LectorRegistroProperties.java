package dev.lacre.verifactu.internal.xml;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Lector y escritor coinciden en todo lo que entra en la huella: números de serie con cualquier
 * carácter admitido, importes con signo, fechas y husos, incluido el desfase cero de Canarias en
 * invierno, que se escribe como {@code Z}.
 */
class LectorRegistroProperties {

    private static final CanonicalizadorAeat CANONICALIZADOR = new CanonicalizadorAeat();

    @Property
    void loQueSeEscribeSeLeeIgualYSuHuellaSeRecalcula(
            @ForAll("numerosDeSerie") String numSerie,
            @ForAll("importes") Importe cuotaTotal,
            @ForAll("importes") Importe importeTotal,
            @ForAll("fechas") LocalDate expedicion,
            @ForAll("instantes") Instant generacion,
            @ForAll("zonas") ZoneId zona,
            @ForAll("anteriores") Optional<RegistroAnterior> anterior) {

        RegistroEncadenado registro = new EncadenadorRegistros(Clock.fixed(generacion, ZoneOffset.UTC), CANONICALIZADOR)
                .encadenar(
                        Registros.alta()
                                .idFactura(new IdFactura(Registros.EMISOR, numSerie, expedicion))
                                .cuotaTotal(cuotaTotal)
                                .importeTotal(importeTotal)
                                .macrodato(
                                        importeTotal.valor().abs().compareTo(DatosRegistroAlta.UMBRAL_MACRODATO.valor())
                                                >= 0)
                                .build(),
                        anterior,
                        zona);

        RegistroLeido leido = LectorRegistro.leer(EscritorRegistro.escribir(registro));

        assertThat(leido.campos()).isEqualTo(registro.datos().camposDeHuella());
        assertThat(leido.anterior()).isEqualTo(registro.registroAnterior());
        assertThat(leido.fechaHoraHusoGenRegistro()).isEqualTo(registro.fechaHoraHusoGenRegistro());
        assertThat(leido.huellaRecalculada(CANONICALIZADOR)).isEqualTo(registro.huella());
    }

    /** ASCII imprimible salvo las comillas y {@code <}, {@code =} y {@code >}. */
    @Provide
    Arbitrary<String> numerosDeSerie() {
        String admitidos = IntStream.rangeClosed(32, 126)
                .filter(c -> "\"'<=>".indexOf(c) < 0)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
        return Arbitraries.strings()
                .withChars(admitidos.toCharArray())
                .ofMinLength(1)
                .ofMaxLength(60)
                .filter(texto -> !texto.isBlank());
    }

    @Provide
    Arbitrary<Importe> importes() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-999999999999.99"), new BigDecimal("999999999999.99"))
                .ofScale(2)
                .map(valor -> Importe.de(valor.toPlainString()));
    }

    @Provide
    Arbitrary<LocalDate> fechas() {
        return Arbitraries.integers()
                .between(0, 36_500)
                .map(dias -> LocalDate.of(2000, 1, 1).plusDays(dias));
    }

    @Provide
    Arbitrary<Instant> instantes() {
        return Arbitraries.longs()
                .between(
                        Instant.parse("2000-01-01T00:00:00Z").getEpochSecond(),
                        Instant.parse("2100-01-01T00:00:00Z").getEpochSecond())
                .map(Instant::ofEpochSecond);
    }

    @Provide
    Arbitrary<ZoneId> zonas() {
        return Arbitraries.of("Europe/Madrid", "Atlantic/Canary", "Africa/Ceuta")
                .map(ZoneId::of);
    }

    @Provide
    Arbitrary<Optional<RegistroAnterior>> anteriores() {
        Arbitrary<RegistroAnterior> enlace = Arbitraries.strings()
                .withChars("0123456789ABCDEF")
                .ofLength(64)
                .map(huella -> new RegistroAnterior(Registros.idFactura("FA/ANTERIOR"), new Huella(huella)));
        return Arbitraries.oneOf(Arbitraries.just(Optional.empty()), enlace.map(Optional::of));
    }
}
