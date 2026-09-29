package dev.lacre.remision.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.remision.EnvioRechazadoException;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.EstadoEnvioAeat;
import dev.lacre.remision.EstadoRegistroAeat;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Lectura de la respuesta de la AEAT sobre documentos con la forma de
 * {@code RespuestaSuministro.xsd}: los cuatro desenlaces, varias líneas, el SOAP Fault y lo
 * ilegible.
 */
class LectorRespuestaAeatTest {

    private static String respuesta(String estadoEnvio, String lineas) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <tikR:RespuestaRegFactuSistemaFacturacion
                        xmlns:tikR="https://www2.agenciatributaria.gob.es/RespuestaSuministro.xsd"
                        xmlns:tik="https://www2.agenciatributaria.gob.es/SuministroInformacion.xsd">
                      <tikR:CSV>A-CSV-DE-PRUEBA</tikR:CSV>
                      <tikR:Cabecera>
                        <tik:ObligadoEmision>
                          <tik:NombreRazon>Obligado de prueba SL</tik:NombreRazon>
                          <tik:NIF>89890001K</tik:NIF>
                        </tik:ObligadoEmision>
                      </tikR:Cabecera>
                      <tikR:TiempoEsperaEnvio>60</tikR:TiempoEsperaEnvio>
                      <tikR:EstadoEnvio>%s</tikR:EstadoEnvio>
                      %s
                    </tikR:RespuestaRegFactuSistemaFacturacion>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(estadoEnvio, lineas);
    }

    private static String linea(String numSerie, String estado, String error) {
        return linea(numSerie, "Alta", estado, error);
    }

    /** El bloque {@code Operacion} es obligatorio en el esquema. */
    private static String linea(String numSerie, String tipoOperacion, String estado, String error) {
        return """
                <tikR:RespuestaLinea>
                  <tikR:IDFactura>
                    <tik:IDEmisorFactura>89890001K</tik:IDEmisorFactura>
                    <tik:NumSerieFactura>%s</tik:NumSerieFactura>
                    <tik:FechaExpedicionFactura>01-01-2024</tik:FechaExpedicionFactura>
                  </tikR:IDFactura>
                  <tikR:Operacion>
                    <tik:TipoOperacion>%s</tik:TipoOperacion>
                  </tikR:Operacion>
                  <tikR:EstadoRegistro>%s</tikR:EstadoRegistro>
                  %s
                </tikR:RespuestaLinea>
                """.formatted(numSerie, tipoOperacion, estado, error);
    }

    // --- Los cuatro desenlaces ---

    @Test
    void unEnvioCorrectoSeLeeEntero() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("Correcto", linea("FA/1", "Correcto", "")));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.CORRECTO);
        assertThat(respuesta.csv()).isEqualTo("A-CSV-DE-PRUEBA");
        assertThat(respuesta.tiempoEspera()).isEqualTo(Duration.ofSeconds(60));
        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.idFactura().numSerieFactura()).isEqualTo("FA/1");
            assertThat(linea.idFactura().fechaExpedicion()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(linea.estado()).isEqualTo(EstadoRegistroAeat.CORRECTO);
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.ACEPTADO);
        });
    }

    @Test
    void aceptadoConErroresConservaElCodigoQueHaySubsanar() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta(
                "Correcto",
                linea(
                        "FA/1",
                        "AceptadoConErrores",
                        "<tikR:CodigoErrorRegistro>2000</tikR:CodigoErrorRegistro>"
                                + "<tikR:DescripcionErrorRegistro>Huella incorrecta</tikR:DescripcionErrorRegistro>")));

        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.codigoError()).isEqualTo(2000);
            assertThat(linea.descripcionError()).isEqualTo("Huella incorrecta");
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.ACEPTADO_CON_ERRORES);
        });
    }

    @Test
    void unRegistroRechazadoSeLeeComoRechazado() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(
                respuesta(
                        "Incorrecto",
                        linea(
                                "FA/1",
                                "Incorrecto",
                                "<tikR:CodigoErrorRegistro>1130</tikR:CodigoErrorRegistro>"
                                        + "<tikR:DescripcionErrorRegistro>Caracteres no permitidos</tikR:DescripcionErrorRegistro>")));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.INCORRECTO);
        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.RECHAZADO);
            assertThat(linea.esDuplicado()).isFalse();
        });
    }

    /** Un {@code Incorrecto} con código 3000 es un duplicado, no un rechazo. */
    @Test
    void unRechazoPorDuplicadoNoEsUnRechazo() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta(
                "Incorrecto",
                linea(
                        "FA/1",
                        "Incorrecto",
                        "<tikR:CodigoErrorRegistro>3000</tikR:CodigoErrorRegistro>"
                                + "<tikR:DescripcionErrorRegistro>Registro duplicado.</tikR:DescripcionErrorRegistro>"
                                + "<tikR:RegistroDuplicado>"
                                + "<tik:IdPeticionRegistroDuplicado>PET-1</tik:IdPeticionRegistroDuplicado>"
                                + "</tikR:RegistroDuplicado>")));

        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.esDuplicado()).isTrue();
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.DUPLICADO);
        });
    }

    /** {@code RegistroDuplicado} trae el código del registro original, no el de la línea. */
    @Test
    void elCodigoDelRegistroOriginalNoSustituyeAlDeLaLinea() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta(
                "Incorrecto",
                linea(
                        "FA/1",
                        "Incorrecto",
                        "<tikR:CodigoErrorRegistro>3000</tikR:CodigoErrorRegistro>"
                                + "<tikR:DescripcionErrorRegistro>Registro de facturación duplicado.</tikR:DescripcionErrorRegistro>"
                                + "<tikR:RegistroDuplicado>"
                                + "<tik:IdPeticionRegistroDuplicado>PET-1</tik:IdPeticionRegistroDuplicado>"
                                + "<tik:EstadoRegistroDuplicado>AceptadaConErrores</tik:EstadoRegistroDuplicado>"
                                + "<tik:CodigoErrorRegistro>2007</tik:CodigoErrorRegistro>"
                                + "<tik:DescripcionErrorRegistro>No debe informarse como primer registro.</tik:DescripcionErrorRegistro>"
                                + "</tikR:RegistroDuplicado>")));

        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.codigoError()).isEqualTo(3000);
            assertThat(linea.descripcionError()).isEqualTo("Registro de facturación duplicado.");
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.DUPLICADO);
        });
    }

    // --- Varias líneas ---

    /** Tres líneas de las que solo la de en medio trae error. */
    @Test
    void separaBienLasLineasAunqueSoloAlgunasTraiganError() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta(
                "ParcialmenteCorrecto",
                linea("FA/1", "Correcto", "")
                        + linea("FA/2", "Incorrecto", "<tikR:CodigoErrorRegistro>1130</tikR:CodigoErrorRegistro>")
                        + linea("FA/3", "Correcto", "")));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.PARCIALMENTE_CORRECTO);
        assertThat(respuesta.lineas())
                .extracting(linea -> linea.idFactura().numSerieFactura())
                .containsExactly("FA/1", "FA/2", "FA/3");
        assertThat(respuesta.lineas()).extracting(LineaRespuesta::codigoError).containsExactly(null, 1130, null);
    }

    @Test
    void distingueElTipoDeOperacionDeCadaLinea() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta(
                "Correcto", linea("FA/1", "Alta", "Correcto", "") + linea("FA/1", "Anulacion", "Correcto", "")));

        assertThat(respuesta.lineas())
                .extracting(LineaRespuesta::tipo)
                .containsExactly(TipoRegistro.ALTA, TipoRegistro.ANULACION);
    }

    @Test
    void unTipoDeOperacionDesconocidoHaceIlegibleLaRespuesta() {
        assertThatThrownBy(() ->
                        LectorRespuestaAeat.leer(respuesta("Correcto", linea("FA/1", "Rectificacion", "Correcto", ""))))
                .isInstanceOf(RespuestaIlegibleException.class)
                .hasMessageContaining("Rectificacion");
    }

    @Test
    void unEnvioSinLineasSeLeeIgual() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("Incorrecto", ""));

        assertThat(respuesta.lineas()).isEmpty();
    }

    // --- Lo que no se puede leer ---

    // --- Rechazo del envío completo, que llega como SOAP Fault ---

    /**
     * Documento real devuelto por la AEAT: un rechazo del envío completo llega como Fault, sin
     * {@code EstadoEnvio} ni líneas.
     */
    @Test
    void unRechazoDelEnvioCompletoLlegaComoSoapFault() {
        String fault = """
                <?xml version="1.0" encoding="UTF-8"?>
                <env:Envelope xmlns:env="http://schemas.xmlsoap.org/soap/envelope/">
                  <env:Body>
                    <env:Fault>
                      <faultcode>env:Client</faultcode>
                      <faultstring>Codigo[4104].Error en la cabecera: el valor del campo NIF del bloque ObligadoEmision no está identificado.. NIF:99999999R</faultstring>
                    </env:Fault>
                  </env:Body>
                </env:Envelope>
                """;

        assertThatThrownBy(() -> LectorRespuestaAeat.leer(fault))
                .isInstanceOf(EnvioRechazadoException.class)
                .hasMessageContaining("4104")
                .hasMessageContaining("no está identificado");
    }

    /** Sin código reconocible, el texto del Fault sigue llegando entero. */
    @Test
    void unFaultSinCodigoTambienEsUnRechazo() {
        String fault = """
                <env:Envelope xmlns:env="http://schemas.xmlsoap.org/soap/envelope/"><env:Body>
                  <env:Fault><faultcode>env:Server</faultcode>
                  <faultstring>Servicio no disponible</faultstring></env:Fault>
                </env:Body></env:Envelope>
                """;

        assertThatThrownBy(() -> LectorRespuestaAeat.leer(fault))
                .isInstanceOf(EnvioRechazadoException.class)
                .hasMessageContaining("Servicio no disponible");
    }

    /** Un rechazo del envío hereda de {@code RemisionFallidaException}: el lote queda pendiente. */
    @Test
    void unRechazoDelEnvioSeTrataComoFalloDeRemision() {
        assertThat(new EnvioRechazadoException(4104, "lo que sea")).isInstanceOf(RemisionFallidaException.class);
    }

    @Test
    void sinEstadoDeEnvioLaRespuestaEsIlegible() {
        assertThatThrownBy(() -> LectorRespuestaAeat.leer(
                        "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"/>"))
                .isInstanceOf(RespuestaIlegibleException.class)
                .hasMessageContaining("EstadoEnvio");
    }

    @Test
    void unXmlRotoNoSeConfundeConUnRechazo() {
        assertThatThrownBy(() -> LectorRespuestaAeat.leer("<esto no es xml"))
                .isInstanceOf(RespuestaIlegibleException.class);
    }
}
