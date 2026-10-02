package dev.lacre.remision.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.internal.EsquemasAeat;
import dev.lacre.verifactu.registro.IdFactura;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class EscritorConsultaTest {

    private static final ObligadoTributario OBLIGADO = ObligadoTributario.nuevo(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            new Nif("89890001K"),
            "Obligado de prueba SL",
            ZoneId.of("Europe/Madrid"));

    @Test
    void laPrimeraPaginaValidaContraElEsquemaOficial() {
        String sobre = EscritorConsulta.envolver(OBLIGADO, YearMonth.of(2026, 9), null);

        assertThat(validarCuerpo(sobre)).isEmpty();
        assertThat(sobre)
                .contains("<sf:Ejercicio>2026</sf:Ejercicio>")
                .contains("<sf:Periodo>09</sf:Periodo>")
                .contains("<sf:NIF>89890001K</sf:NIF>")
                .doesNotContain("ClavePaginacion");
    }

    @Test
    void lasSiguientesLlevanLaClaveDePaginacion() {
        IdFactura ultima = new IdFactura(new Nif("89890001K"), "FA/77", LocalDate.of(2026, 9, 30));

        String sobre = EscritorConsulta.envolver(OBLIGADO, YearMonth.of(2026, 9), ultima);

        assertThat(validarCuerpo(sobre)).isEmpty();
        assertThat(sobre)
                .contains("<con:ClavePaginacion>")
                .contains("<sf:NumSerieFactura>FA/77</sf:NumSerieFactura>")
                .contains("<sf:FechaExpedicionFactura>30-09-2026</sf:FechaExpedicionFactura>");
    }

    @Test
    void escapaElNombreDelObligado() {
        ObligadoTributario conAmpersand = ObligadoTributario.nuevo(
                UUID.randomUUID(), new Nif("89890001K"), "Pérez & Hijos <SL>", ZoneId.of("Europe/Madrid"));

        String sobre = EscritorConsulta.envolver(conAmpersand, YearMonth.of(2026, 1), null);

        assertThat(validarCuerpo(sobre)).isEmpty();
        assertThat(sobre).contains("Pérez &amp; Hijos &lt;SL&gt;");
    }

    /** Valida {@code ConsultaFactuSistemaFacturacion}; el sobre SOAP no lo describe el XSD. */
    private static Optional<String> validarCuerpo(String sobre) {
        String apertura = "<con:ConsultaFactuSistemaFacturacion>";
        String cierre = "</con:ConsultaFactuSistemaFacturacion>";
        String cuerpo = sobre.substring(sobre.indexOf(apertura), sobre.indexOf(cierre) + cierre.length())
                .replaceFirst(
                        apertura,
                        "<con:ConsultaFactuSistemaFacturacion xmlns:con=\"" + EscritorConsulta.NS_CONSULTA
                                + "\" xmlns:sf=\"" + EscritorLote.NS_SF + "\">");
        try {
            EsquemasAeat.compilar("ConsultaLR.xsd").newValidator().validate(new StreamSource(new StringReader(cuerpo)));
            return Optional.empty();
        } catch (SAXException e) {
            return Optional.of(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
