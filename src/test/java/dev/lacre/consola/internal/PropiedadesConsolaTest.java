package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PropiedadesConsolaTest {

    @Test
    void sinClaveLaConsolaQuedaCerrada() {
        assertThat(new PropiedadesConsola("lacre", null).abierta()).isFalse();
        assertThat(new PropiedadesConsola("lacre", "   ").abierta()).isFalse();
    }

    @Test
    void conClaveSuficienteQuedaAbierta() {
        assertThat(new PropiedadesConsola("lacre", "a".repeat(16)).abierta()).isTrue();
    }

    @Test
    void unaClaveCortaImpideArrancar() {
        assertThatThrownBy(() -> new PropiedadesConsola("lacre", "a".repeat(15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LACRE_CONSOLA_CLAVE");
    }

    @Test
    void losEspaciosNoCuentanParaLaLongitud() {
        assertThatThrownBy(() -> new PropiedadesConsola("lacre", "  " + "a".repeat(15) + "  "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaClaveSinUsuarioImpideArrancar() {
        assertThatThrownBy(() -> new PropiedadesConsola(" ", "a".repeat(16)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LACRE_CONSOLA_USUARIO");
    }
}
