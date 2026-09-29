package dev.lacre.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObligadoTributarioTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Nif NIF = new Nif("89890001K");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void unObligadoNuevoNaceEnLaVersionCero() {
        ObligadoTributario obligado =
                ObligadoTributario.nuevo(ID, NIF, "Obligado de prueba SL", ZoneId.of("Atlantic/Canary"));

        assertThat(obligado.version()).isZero();
        assertThat(obligado.zonaHoraria()).isEqualTo(ZoneId.of("Atlantic/Canary"));
    }

    @Test
    void sinZonaHorariaNoHayObligado() {
        assertThatThrownBy(() -> ObligadoTributario.nuevo(ID, NIF, "Obligado de prueba SL", null))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("huella");
    }

    @Test
    void exigeIdentificadorNifYNombre() {
        assertThatThrownBy(() -> ObligadoTributario.nuevo(null, NIF, "Obligado SL", MADRID))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> ObligadoTributario.nuevo(ID, null, "Obligado SL", MADRID))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> ObligadoTributario.nuevo(ID, NIF, "  ", MADRID))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void recortaElNombreYLoLimitaALoQueAdmiteElEsquema() {
        assertThat(ObligadoTributario.nuevo(ID, NIF, "  Obligado SL  ", MADRID).nombreRazon())
                .isEqualTo("Obligado SL");
        assertThatThrownBy(() -> ObligadoTributario.nuevo(ID, NIF, "x".repeat(121), MADRID))
                .isInstanceOf(ValorInvalidoException.class);
    }
}
