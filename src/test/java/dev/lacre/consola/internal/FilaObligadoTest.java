package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnviosDeObligado;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FilaObligadoTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-01T12:00:00Z");

    private final ObligadoTributario obligado = ObligadoTributario.nuevo(
            UUID.randomUUID(), ObligadosDePrueba.siguienteNif(), "Obligado SL", ObligadosDePrueba.MADRID);

    @Test
    void sinEnviosLaFilaVaACeroYSinEspera() {
        FilaObligado fila = FilaObligado.de(obligado, null, null, AHORA);

        assertThat(fila.envios().pendientes()).isZero();
        assertThat(fila.espera()).isNull();
        assertThat(fila.atascado()).isFalse();
    }

    @Test
    void unaHoraJustaNoEsAtasco() {
        FilaObligado fila = FilaObligado.de(obligado, 1L, pendienteDesde(AHORA.minusHours(1)), AHORA);

        assertThat(fila.atascado()).isFalse();
        assertThat(fila.espera()).isEqualTo("1 h 0 min");
    }

    @Test
    void masDeUnaHoraEsAtasco() {
        FilaObligado fila = FilaObligado.de(obligado, 1L, pendienteDesde(AHORA.minusMinutes(61)), AHORA);

        assertThat(fila.atascado()).isTrue();
        assertThat(fila.espera()).isEqualTo("1 h 1 min");
    }

    @Test
    void menosDeUnaHoraSeDaEnMinutos() {
        assertThat(FilaObligado.de(obligado, 1L, pendienteDesde(AHORA.minusMinutes(42)), AHORA)
                        .espera())
                .isEqualTo("42 min");
    }

    @Test
    void elEstadoVaDeLoQueMasAtencionPideALoQueMenos() {
        assertThat(FilaObligado.de(obligado, 1L, pendienteDesde(AHORA.minusHours(2)), AHORA)
                        .estado())
                .isEqualTo(FilaObligado.Estado.ATASCADO);
        assertThat(FilaObligado.de(obligado, 1L, envios(0, 1, 1, 0, 0), AHORA).estado())
                .isEqualTo(FilaObligado.Estado.CON_ERRORES);
        assertThat(FilaObligado.de(obligado, 1L, envios(0, 1, 0, 1, 1), AHORA).estado())
                .isEqualTo(FilaObligado.Estado.APARTADOS);
        assertThat(FilaObligado.de(obligado, 1L, pendienteDesde(AHORA.minusMinutes(5)), AHORA)
                        .estado())
                .isEqualTo(FilaObligado.Estado.ENVIANDO);
        assertThat(FilaObligado.de(obligado, 1L, null, AHORA).estado()).isEqualTo(FilaObligado.Estado.AL_DIA);
        assertThat(FilaObligado.de(obligado, null, null, AHORA).estado()).isEqualTo(FilaObligado.Estado.SIN_ACTIVIDAD);
    }

    @Test
    void losErroresAtendidosNoCuentanComoSinAtenderPeroSiComoIncidencias() {
        FilaObligado fila = FilaObligado.de(obligado, 1L, envios(2, 1, 0, 0, 1), AHORA);

        assertThat(fila.erroresSinAtender()).isEqualTo(2);
        assertThat(fila.incidencias()).isEqualTo(3);
    }

    @Test
    void laVistaGeneralOrdenaPorEstadoYDespuesPorNombre() {
        FilaObligado alDiaB = conNombre("beta", 1L, null);
        FilaObligado alDiaA = conNombre("Alfa", 1L, null);
        FilaObligado conErrores = conNombre("Zeta", 1L, envios(1, 0, 0, 0, 0));

        assertThat(Stream.of(alDiaB, alDiaA, conErrores).sorted(FilaObligado.POR_ATENCION))
                .containsExactly(conErrores, alDiaA, alDiaB);
    }

    private FilaObligado conNombre(String nombre, Long posicion, EnviosDeObligado envios) {
        ObligadoTributario otro = ObligadoTributario.nuevo(
                UUID.randomUUID(), ObligadosDePrueba.siguienteNif(), nombre, ObligadosDePrueba.MADRID);
        return FilaObligado.de(otro, posicion, envios, AHORA);
    }

    private EnviosDeObligado envios(
            long rechazados, long aceptadosConErrores, long duplicados, long apartados, long atendidos) {
        return new EnviosDeObligado(
                obligado.id(), 0, 0, aceptadosConErrores, rechazados, duplicados, apartados, atendidos, null);
    }

    private EnviosDeObligado pendienteDesde(OffsetDateTime creado) {
        return new EnviosDeObligado(obligado.id(), 1, 0, 0, 0, 0, 0, 0, creado);
    }
}
