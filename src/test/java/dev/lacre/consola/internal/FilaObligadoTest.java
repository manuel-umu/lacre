package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnviosDeObligado;
import java.time.OffsetDateTime;
import java.util.UUID;
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

    private EnviosDeObligado pendienteDesde(OffsetDateTime creado) {
        return new EnviosDeObligado(obligado.id(), 1, 0, 0, 0, 0, creado);
    }
}
