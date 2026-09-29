package dev.lacre.verifactu.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.RechazoPrevio;
import dev.lacre.verifactu.registro.Registros;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OperativaAeatTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("rechazadasSiLaFacturaExiste")
    void conRegistroPrevioSeRechazaComoDuplicado(String operativa, DatosRegistro datos) {
        assertThatThrownBy(() -> OperativaAeat.exigir(datos, true))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("3000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rechazadasSiLaFacturaExiste")
    void sinRegistroPrevioSeAdmite(String operativa, DatosRegistro datos) {
        assertThatCode(() -> OperativaAeat.exigir(datos, false)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("admitidasSiLaFacturaExiste")
    void conRegistroPrevioSeAdmite(String operativa, DatosRegistro datos) {
        assertThatCode(() -> OperativaAeat.exigir(datos, true)).doesNotThrowAnyException();
    }

    static Stream<Arguments> rechazadasSiLaFacturaExiste() {
        return Stream.of(
                Arguments.arguments("alta", Registros.emitible().build()),
                Arguments.arguments(
                        "alta por rechazo",
                        Registros.emitible()
                                .subsanacion(true)
                                .rechazoPrevio(RechazoPrevio.X)
                                .build()),
                Arguments.arguments("anulación sin registro previo", anulacion(true)));
    }

    static Stream<Arguments> admitidasSiLaFacturaExiste() {
        return Stream.of(
                Arguments.arguments(
                        "subsanación", Registros.emitible().subsanacion(true).build()),
                Arguments.arguments(
                        "alta por rechazo de subsanación",
                        Registros.emitible()
                                .subsanacion(true)
                                .rechazoPrevio(RechazoPrevio.S)
                                .build()),
                Arguments.arguments("anulación", anulacion(false)));
    }

    private static DatosRegistroAnulacion anulacion(boolean sinRegistroPrevio) {
        return new DatosRegistroAnulacion(
                Registros.idFacturaEmitible("FA/1"),
                null,
                sinRegistroPrevio,
                false,
                null,
                null,
                Registros.sistemaInformatico());
    }
}
