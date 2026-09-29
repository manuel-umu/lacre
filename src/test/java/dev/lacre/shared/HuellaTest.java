package dev.lacre.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HuellaTest {

    private static final String VALIDA = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";

    @Test
    void aceptaSesentaYCuatroHexEnMayusculas() {
        assertThat(new Huella(VALIDA).valor()).isEqualTo(VALIDA);
    }

    @Test
    void normalizaAMayusculas() {
        assertThat(new Huella(VALIDA.toLowerCase())).isEqualTo(new Huella(VALIDA));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B85", // 63
                "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B8555", // 65
                "G3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", // no hexadecimal
                " E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", // espacio: no se recorta
                ""
            })
    void rechazaCualquierOtraForma(String valor) {
        assertThatThrownBy(() -> new Huella(valor)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void rechazaNula() {
        assertThatThrownBy(() -> new Huella(null))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("nula");
    }
}
