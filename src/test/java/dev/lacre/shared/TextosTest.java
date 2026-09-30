package dev.lacre.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextosTest {

    @Test
    void unTextoOpcionalEnBlancoEsNulo() {
        assertThat(Textos.opcional("   ", 10, "campo")).isNull();
        assertThat(Textos.opcional(null, 10, "campo")).isNull();
    }

    @Test
    void unTextoOpcionalSeRecorta() {
        assertThat(Textos.opcional("  valor  ", 10, "campo")).isEqualTo("valor");
    }
}
