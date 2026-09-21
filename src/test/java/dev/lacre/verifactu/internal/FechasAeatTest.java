package dev.lacre.verifactu.internal;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.Registros;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FechasAeatTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 21);

    /** Clave de régimen 15, que admite una operación posterior a la expedición. */
    private static final Desglose DESGLOSE_DEVENGO_PENDIENTE = Desglose.de(new DetalleDesglose(
            Impuesto.IVA, new ClaveRegimen("15"), CalificacionOperacion.S1,
            Porcentaje.de("10"), Importe.de("111.10"), null, Importe.de("12.35"), null, null));

    @ParameterizedTest
    @MethodSource("altasRechazadas")
    void rechazaFechasFueraDeRango(DatosRegistroAlta datos, String codigo) {
        assertThatThrownBy(() -> FechasAeat.exigir(datos, HOY))
                .isInstanceOfSatisfying(ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo(codigo));
    }

    static Stream<Arguments> altasRechazadas() {
        return Stream.of(
                Arguments.arguments(conExpedicion(LocalDate.of(2024, 10, 27)), "1152"),
                Arguments.arguments(conExpedicion(HOY.plusDays(1)), "1112"),
                Arguments.arguments(
                        conOperacion(HOY.minusYears(20).minusDays(1)), "1134"),
                Arguments.arguments(conOperacion(LocalDate.of(2028, 1, 1)), "1125"));
    }

    @ParameterizedTest
    @MethodSource("altasAceptadas")
    void aceptaCasosLimite(DatosRegistroAlta datos) {
        assertThatCode(() -> FechasAeat.exigir(datos, HOY)).doesNotThrowAnyException();
    }

    static Stream<Arguments> altasAceptadas() {
        return Stream.of(
                Arguments.arguments(conExpedicion(LocalDate.of(2024, 10, 28))),
                Arguments.arguments(conExpedicion(HOY)),
                Arguments.arguments(conOperacion(HOY.minusYears(20))),
                Arguments.arguments(conOperacion(LocalDate.of(2027, 12, 31))));
    }

    private static DatosRegistroAlta conExpedicion(LocalDate fechaExpedicion) {
        return Registros.alta()
                .idFactura(new IdFactura(Registros.EMISOR, "FA/1", fechaExpedicion))
                .build();
    }

    private static DatosRegistroAlta conOperacion(LocalDate fechaOperacion) {
        return Registros.alta()
                .idFactura(new IdFactura(Registros.EMISOR, "FA/1", FechasAeat.ENTRADA_EN_VIGOR))
                .fechaOperacion(fechaOperacion)
                .desglose(DESGLOSE_DEVENGO_PENDIENTE)
                .build();
    }
}
