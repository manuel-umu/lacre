package dev.lacre.remision.internal.adaptador;

import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.HUELLA_FA1;
import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.HUELLA_FA2;
import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.clavePaginacion;
import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.cuerpo;
import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.error;
import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.registro;
import static dev.lacre.remision.internal.adaptador.RespuestasDeConsulta.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.remision.EnvioRechazadoException;
import dev.lacre.remision.RegistroEnAeat;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.internal.EsquemasAeat;
import dev.lacre.verifactu.registro.IdFactura;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LectorRespuestaConsultaTest {

    @Test
    void laRespuestaDeLosTestsValidaContraElEsquemaOficial() throws Exception {
        String cuerpo = cuerpo(
                "S",
                "ConDatos",
                registro("FA/1", HUELLA_FA1, "Correcto", "") + registro("FA/2", HUELLA_FA2, "Anulado", ""),
                clavePaginacion("FA/2"));

        EsquemasAeat.compilar("RespuestaConsultaLR.xsd")
                .newValidator()
                .validate(new StreamSource(new StringReader(cuerpo)));
    }

    @Test
    void leeCadaRegistroSinConfundirloConSuAnteriorNiConLaRectificada() {
        LectorRespuestaConsulta.Pagina pagina = LectorRespuestaConsulta.leer(
                respuesta("N", "ConDatos", registro("FA/1", HUELLA_FA1, "Correcto", ""), ""));

        assertThat(pagina.siguiente()).isNull();
        assertThat(pagina.registros())
                .containsExactly(new RegistroEnAeat(
                        new IdFactura(new Nif("89890001K"), "FA/1", LocalDate.of(2024, 11, 27)),
                        new Huella(HUELLA_FA1),
                        RegistroEnAeat.Estado.CORRECTO,
                        null,
                        null,
                        OffsetDateTime.parse("2024-11-27T11:54:12+01:00")));
    }

    @Test
    void sinDatosNoHayRegistrosNiMasPaginas() {
        LectorRespuestaConsulta.Pagina pagina = LectorRespuestaConsulta.leer(respuesta("N", "SinDatos", "", ""));

        assertThat(pagina.registros()).isEmpty();
        assertThat(pagina.siguiente()).isNull();
    }

    @Test
    void conPaginacionDevuelveLaClaveDeLaSiguiente() {
        LectorRespuestaConsulta.Pagina pagina = LectorRespuestaConsulta.leer(
                respuesta("S", "ConDatos", registro("FA/1", HUELLA_FA1, "Correcto", ""), clavePaginacion("FA/1")));

        assertThat(pagina.siguiente())
                .isEqualTo(new IdFactura(new Nif("89890001K"), "FA/1", LocalDate.of(2024, 11, 27)));
    }

    /** El XSD usa el masculino; su documentación y el ejemplo del servicio, el femenino. */
    @ParameterizedTest
    @CsvSource({
        "Correcto, CORRECTO",
        "Correcta, CORRECTO",
        "AceptadoConErrores, ACEPTADO_CON_ERRORES",
        "AceptadaConErrores, ACEPTADO_CON_ERRORES",
        "Anulado, ANULADO",
        "Anulada, ANULADO"
    })
    void entiendeLosEstadosEnLasDosFormas(String valor, RegistroEnAeat.Estado estado) {
        assertThat(leerUno(registro("FA/1", HUELLA_FA1, valor, "")).estado()).isEqualTo(estado);
    }

    @Test
    void unAceptadoConErroresTraeSuCodigoYSuDescripcion() {
        RegistroEnAeat registro =
                leerUno(registro("FA/1", HUELLA_FA1, "AceptadoConErrores", error(2000, "El cálculo no cuadra")));

        assertThat(registro.codigoError()).isEqualTo(2000);
        assertThat(registro.descripcionError()).isEqualTo("El cálculo no cuadra");
    }

    @Test
    void unEstadoDesconocidoNoSeAdivina() {
        assertThatThrownBy(() -> leerUno(registro("FA/1", HUELLA_FA1, "Incorrecto", "")))
                .isInstanceOf(RespuestaIlegibleException.class);
    }

    @Test
    void unaConsultaRechazadaLlegaComoRechazoConSuCodigo() {
        String fault = """
                <env:Envelope xmlns:env="http://schemas.xmlsoap.org/soap/envelope/">
                  <env:Body><env:Fault>
                    <faultcode>env:Client</faultcode>
                    <faultstring>Codigo[4102].El XML no cumple el esquema</faultstring>
                  </env:Fault></env:Body>
                </env:Envelope>
                """;

        assertThatThrownBy(() -> LectorRespuestaConsulta.leer(fault))
                .isInstanceOfSatisfying(
                        EnvioRechazadoException.class,
                        e -> assertThat(e.codigo()).isEqualTo(4102));
    }

    @Test
    void sinResultadoNoEsUnaRespuestaDeConsulta() {
        assertThatThrownBy(() -> LectorRespuestaConsulta.leer("<x/>")).isInstanceOf(RespuestaIlegibleException.class);
    }

    @Test
    void anunciarMasPaginasSinClaveEsIlegible() {
        assertThatThrownBy(() -> LectorRespuestaConsulta.leer(respuesta("S", "ConDatos", "", "")))
                .isInstanceOf(RespuestaIlegibleException.class);
    }

    @Test
    void noExpandeEntidadesExternas() {
        String conEntidad = """
                <?xml version="1.0"?>
                <!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <r>&x;</r>
                """;

        assertThatThrownBy(() -> LectorRespuestaConsulta.leer(conEntidad))
                .isInstanceOf(RespuestaIlegibleException.class);
    }

    private static RegistroEnAeat leerUno(String registro) {
        return LectorRespuestaConsulta.leer(respuesta("N", "ConDatos", registro, ""))
                .registros()
                .getFirst();
    }
}
