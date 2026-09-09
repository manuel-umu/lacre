package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.internal.EsquemasAeat;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.Registros;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El sobre SOAP del envío.
 * <p>
 * Lo que de verdad prueba esto es la <strong>validación del cuerpo contra el XSD oficial</strong>.
 * El sobre se arma concatenando marcado estático con los fragmentos ya serializados, y esa
 * concatenación solo es defendible si algo comprueba que el resultado sigue siendo un
 * {@code RegFactuSistemaFacturacion} válido. Ese algo es este test.
 */
class EscritorLoteTest {

    private static final ObligadoTributario OBLIGADO = ObligadoTributario.nuevo(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            new Nif("89890001K"), "Obligado de prueba SL", ZoneId.of("Europe/Madrid"));

    private static final EncadenadorRegistros ENCADENADOR = new EncadenadorRegistros(
            Clock.fixed(Instant.parse("2024-01-01T18:20:30Z"), ZoneOffset.UTC),
            new CanonicalizadorAeat());

    private static String registro(String numSerie) {
        return EscritorRegistro.escribir(ENCADENADOR.encadenar(
                Registros.alta().idFactura(Registros.idFactura(numSerie)).build(),
                Optional.empty(), ZoneId.of("Europe/Madrid")));
    }

    @Test
    void elCuerpoDelSobreValidaContraElEsquemaOficial() {
        String sobre = EscritorLote.envolver(OBLIGADO, List.of(registro("FA/1")));

        assertThat(validarCuerpo(sobre)).isEmpty();
    }

    @Test
    void variosRegistrosValidanIgual() {
        List<String> registros = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> registro("FA/" + i))
                .toList();

        assertThat(validarCuerpo(EscritorLote.envolver(OBLIGADO, registros))).isEmpty();
    }

    @Test
    void tieneLaEstructuraSoapQueDescribeElDocumentoOficial() {
        String sobre = EscritorLote.envolver(OBLIGADO, List.of(registro("FA/1")));

        assertThat(sobre)
                .startsWith("<soapenv:Envelope")
                .contains("<soapenv:Header/>")
                .contains("<soapenv:Body>")
                .contains(":RegFactuSistemaFacturacion")
                .contains(":Cabecera")
                .contains(":RegistroFactura")
                .endsWith("</soapenv:Envelope>");
    }

    /**
     * El nombre o razón social es el único dato variable del sobre, y por eso es el único que
     * pasa por StAX: concatenado a mano, un {@code &} rompería el documento entero.
     */
    @Test
    void escapaElNombreDelObligado() {
        ObligadoTributario conAmpersand = ObligadoTributario.nuevo(OBLIGADO.id(), OBLIGADO.nif(),
                "Pérez & Hijos, SL", ZoneId.of("Europe/Madrid"));

        String sobre = EscritorLote.envolver(conAmpersand, List.of(registro("FA/1")));

        assertThat(sobre).contains("Pérez &amp; Hijos, SL").doesNotContain("Pérez & Hijos");
        assertThat(validarCuerpo(sobre)).isEmpty();
    }

    // --- Los dos límites ---

    @Test
    void unEnvioVacioNoTieneSentido() {
        assertThatThrownBy(() -> EscritorLote.envolver(OBLIGADO, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Mil registros por envío es tope de la AEAT, no una elección nuestra. */
    @Test
    void masDeMilRegistrosSeRechazanAntesDeSalir() {
        List<String> demasiados = IntStream.rangeClosed(1, EscritorLote.MAXIMO_REGISTROS_POR_ENVIO + 1)
                .mapToObj(i -> "<sf:RegistroAlta/>")
                .toList();

        assertThatThrownBy(() -> EscritorLote.envolver(OBLIGADO, demasiados))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    /**
     * El sobre SOAP no lo describe ningún XSD de la AEAT, así que se valida su contenido: el
     * elemento {@code RegFactuSistemaFacturacion}, que es lo que ella sí especifica.
     */
    private static Optional<String> validarCuerpo(String sobre) {
        int inicio = sobre.indexOf("<sfLR:RegFactuSistemaFacturacion>");
        String cuerpo = sobre.substring(inicio, sobre.indexOf("</sfLR:RegFactuSistemaFacturacion>")
                + "</sfLR:RegFactuSistemaFacturacion>".length());
        String conNamespace = cuerpo.replaceFirst("<sfLR:RegFactuSistemaFacturacion>",
                "<sfLR:RegFactuSistemaFacturacion xmlns:sfLR=\"" + EscritorLote.NS_LR + "\">");
        try {
            Validator validador = EsquemasAeat.compilar("SuministroLR.xsd").newValidator();
            validador.validate(new StreamSource(new StringReader(conNamespace)));
            return Optional.empty();
        } catch (SAXException e) {
            return Optional.of(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
