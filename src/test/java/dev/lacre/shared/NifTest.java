package dev.lacre.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NifTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678Z",  // DNI
            "00000000T",  // resto 0 del módulo 23
            "99999999R",
            "X0000000T",  // NIE, prefijo X -> 0
            "Y1234567X",  // prefijo Y -> 1
            "Z1234567R",  // prefijo Z -> 2
            "A28015865",  // CIF de organización con control numérico obligatorio
            "B12345674",
            "P1234567D",  // organización con control alfabético obligatorio
            "C12345674",  // organización que admite ambos controles
            "C1234567D"
    })
    void aceptaNifsValidos(String valor) {
        assertThat(new Nif(valor).valor()).isEqualTo(valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678A",  // letra de control incorrecta
            "12345678",   // sin letra
            "123456789",  // nueve dígitos
            "X1234567A",  // control de NIE incorrecto
            "W1234567X",  // W no es prefijo válido de NIE
            "B12345670",  // control de CIF incorrecto
            "B1234567D",  // B exige control numérico
            "A1234567D",
            "P12345674",  // P exige control alfabético
            "I1234567A",  // I no es letra de organización
            "ABCDEFGHI",
            "",
            "   ",
            "1234567 8Z"  // espacio interior
    })
    void rechazaNifsInvalidos(String valor) {
        assertThatThrownBy(() -> new Nif(valor)).isInstanceOf(NifInvalidoException.class);
    }

    @Test
    void rechazaNulo() {
        assertThatThrownBy(() -> new Nif(null))
                .isInstanceOf(NifInvalidoException.class)
                .hasMessageContaining("nulo");
    }

    @Test
    void normalizaMayusculasYEspaciosAlrededor() {
        assertThat(new Nif("  12345678z  ")).isEqualTo(new Nif("12345678Z"));
    }

    @Test
    void nifInvalidoEsUnValorInvalido() {
        assertThatThrownBy(() -> new Nif("12345678A")).isInstanceOf(ValorInvalidoException.class);
    }
}
