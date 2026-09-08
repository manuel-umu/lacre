package dev.lacre.verifactu.registro;

import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los caracteres admitidos en {@code NumSerieFactura}, de
 * {@code Validaciones_Errores_Veri-Factu.pdf} §3.1.3.
 * <p>
 * El XSD no expresa esta restricción —solo la longitud—, así que sin esta validación se
 * generarían registros con la forma correcta que la AEAT rechaza.
 */
class IdFacturaTest {

    private static final Nif EMISOR = new Nif("89890001K");
    private static final LocalDate FECHA = LocalDate.of(2024, 1, 1);

    @Test
    void elEjemploOficialDeLaAeatEsValido() {
        assertThat(idFactura("12345678/G33").numSerieFactura()).isEqualTo("12345678/G33");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FA/1", "A-2024-0001", "FACTURA 1", "!#$%&()*+,-./:;?@[]^_{|}~"})
    void elAsciiImprimibleQueNoEstaProhibidoPasa(String numSerie) {
        assertThat(idFactura(numSerie).numSerieFactura()).isEqualTo(numSerie);
    }

    /**
     * Los cinco prohibidos por el documento, listados por código ASCII: 34, 39, 60, 61 y 62. Se
     * usan los códigos y no los glifos del PDF, donde el 39 se imprime como acento grave.
     * <p>
     * El {@code =} y los ángulos no son capricho: el primero separa los campos de la cadena
     * canónica de la huella, y los otros dos romperían el XML.
     */
    @ParameterizedTest
    @ValueSource(chars = {34, 39, 60, 61, 62})
    void losCincoCaracteresProhibidosSeRechazan(char prohibido) {
        assertThatThrownBy(() -> idFactura("FA" + prohibido + "1"))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("ASCII " + (int) prohibido);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FA\t1", "FA\n1", "FAÑ1", "FA€1"})
    void loQueNoEsAsciiImprimibleSeRechaza(String numSerie) {
        assertThatThrownBy(() -> idFactura(numSerie))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("ASCII");
    }

    /**
     * El recorte de los extremos ocurre antes de mirar los caracteres, así que un espacio de
     * sobra no es un carácter prohibido: es ruido de formato. Los espacios interiores sí se
     * conservan, porque el documento de la huella lo exige.
     */
    @Test
    void losEspaciosDeLosExtremosSeRecortanYLosDeDentroSeConservan() {
        assertThat(idFactura(" 12345678 / G33 ").numSerieFactura()).isEqualTo("12345678 / G33");
    }

    private static IdFactura idFactura(String numSerie) {
        return new IdFactura(EMISOR, numSerie, FECHA);
    }
}
