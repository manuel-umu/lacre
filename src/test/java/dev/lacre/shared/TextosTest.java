package dev.lacre.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {0x0, 0x1, 0x8, 0xB, 0xC, 0xE, 0x1F, 0xD800, 0xDFFF, 0xFFFE, 0xFFFF})
    void rechazaLosCaracteresQueElXmlNoAdmite(int caracter) {
        String valor = "a" + (char) caracter + "b";

        assertThatThrownBy(() -> Textos.obligatorio(valor, 10, "La descripción"))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessage("La descripción contiene un carácter que no admite el XML de la AEAT: U+%04X"
                        .formatted(caracter));
        assertThatThrownBy(() -> Textos.opcional(valor, 10, "La descripción"))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x9, 0xA, 0xD, 0x20, 0xD7FF, 0xE000, 0xFFFD, 0x10000, 0x1F600, 0x10FFFF})
    void admiteLosQueElXmlSiAdmite(int caracter) {
        String valor = "a" + Character.toString(caracter) + "b";

        assertThat(Textos.obligatorio(valor, 10, "La descripción")).isEqualTo(valor);
    }
}
