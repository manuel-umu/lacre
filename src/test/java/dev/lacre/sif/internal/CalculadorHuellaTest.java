package dev.lacre.sif.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vectores conocidos de SHA-256 y los ejemplos del apartado 6 del documento de la huella de
 * la AEAT. Son la única comprobación que valida de verdad el paso de cadena a bytes; las
 * properties comprueban el comportamiento, no la corrección del algoritmo ni la codificación.
 */
class CalculadorHuellaTest {

    @Test
    void reproduceLosVectoresEstandarDeSha256() {
        assertThat(CalculadorHuella.calcular("").valor())
                .isEqualTo("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
        assertThat(CalculadorHuella.calcular("abc").valor())
                .isEqualTo("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD");
    }

    @Test
    void codificaEnUtf8ComoExigeElApartado3() {
        // "ñ" es C3 B1 en UTF-8 y B1 en ISO-8859-1: el vector distingue una codificación de la otra.
        assertThat(CalculadorHuella.calcular("ñ").valor())
                .isEqualTo("024BB90888CA89A15A19E9BDD8C712BFB070465FCE1EF25E43C170EA44FC5E5F");
    }

    /**
     * Caso 3 del documento: registro de anulación con registro anterior. Todavía no existe el
     * agregado del registro de anulación, así que la cadena canónica va literal; lo que este
     * test demuestra es que el algoritmo de salida es correcto también para ese subconjunto
     * de campos.
     */
    @Test
    void reproduceElEjemploDeRegistroDeAnulacionDeLaAeat() {
        String cadena = "IDEmisorFacturaAnulada=89890001K"
                + "&NumSerieFacturaAnulada=12345679/G34"
                + "&FechaExpedicionFacturaAnulada=01-01-2024"
                + "&Huella=F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97"
                + "&FechaHoraHusoGenRegistro=2024-01-01T19:20:40+01:00";

        assertThat(CalculadorHuella.calcular(cadena).valor())
                .isEqualTo("177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68");
    }

    @Test
    void rechazaCadenaNula() {
        assertThatThrownBy(() -> CalculadorHuella.calcular(null)).isInstanceOf(NullPointerException.class);
    }
}
