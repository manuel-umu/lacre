package dev.lacre.verifactu.qr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.Registros;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * La URL del código QR, contra el ejemplo del documento oficial de especificaciones del QR. El
 * caso que importa es el {@code &} dentro del número de serie, que sin codificar partiría la
 * URL en dos parámetros.
 */
class UrlDeCotejoTest {

    private static final String PRUEBAS = "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?";

    @Test
    void reproduceElEjemploDelDocumentoOficial() {
        String url = UrlDeCotejo.de(PRUEBAS, alta("12345678&G33", "241.40"));

        assertThat(url)
                .isEqualTo("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR"
                        + "?nif=89890001K&numserie=12345678%26G33&fecha=01-01-2024&importe=241.40");
    }

    @Test
    void losCuatroParametrosVanEnElOrdenDeLaEspecificacion() {
        String url = UrlDeCotejo.de(PRUEBAS, alta("FA/1", "123.45"));

        assertThat(url).endsWith("?nif=89890001K&numserie=FA%2F1&fecha=01-01-2024&importe=123.45");
    }

    /** El espacio se codifica como {@code +}, que es lo que hace el ejemplo de la AEAT. */
    @Test
    void elEspacioDelNumeroDeSerieSeCodifica() {
        assertThat(UrlDeCotejo.de(PRUEBAS, alta("12345678 / G33", "123.45"))).contains("numserie=12345678+%2F+G33");
    }

    /** La coma como separador decimal es el error 2005 de la AEAT. */
    @Test
    void elImporteLlevaPuntoYDosDecimales() {
        assertThat(UrlDeCotejo.de(PRUEBAS, alta("FA/1", "7.2"))).endsWith("importe=7.20");
        assertThat(UrlDeCotejo.de(PRUEBAS, alta("FA/1", "1000"))).endsWith("importe=1000.00");
    }

    private static DatosRegistroAlta alta(String numSerie, String importeTotal) {
        return Registros.alta()
                .idFactura(new IdFactura(new Nif("89890001K"), numSerie, LocalDate.of(2024, 1, 1)))
                .importeTotal(Importe.de(importeTotal))
                .build();
    }
}
