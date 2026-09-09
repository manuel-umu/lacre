package dev.lacre.remision.internal.adaptador;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.lacre.identidad.AlmacenCertificados;
import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.EstadoEnvioAeat;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.shared.Nif;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El adaptador de la AEAT contra WireMock, con los cuatro escenarios que exige {@code CLAUDE.md}:
 * aceptado, aceptado con errores, rechazado y timeout.
 * <p>
 * <strong>WireMock sirve por HTTP, no por HTTPS con TLS mutuo</strong>, y es deliberado. Lo que
 * se prueba aquí es lo nuestro: qué se envía, qué cabeceras lleva y cómo se interpreta lo que
 * vuelve. El TLS mutuo es cableado de la JDK —un {@code SSLContext} con unos {@code KeyManager}—
 * y montar una autoridad certificadora de pruebas para volver a comprobar que la JDK sabe hacer
 * TLS no probaría nada nuestro. Que los {@code KeyManager} salgan bien del PKCS#12 lo cubre
 * {@code AlmacenCertificadosEnDiscoTest}.
 */
class ClienteAeatSoapTest {

    private static final String RUTA = "/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP";

    private static final ObligadoTributario OBLIGADO = ObligadoTributario.nuevo(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            new Nif("89890001K"), "Obligado de prueba SL", ZoneId.of("Europe/Madrid"));

    private static final String REGISTRO =
            "<sf:RegistroAlta xmlns:sf=\"https://www2.agenciatributaria.gob.es/static_files/common/"
                    + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd\">"
                    + "<sf:IDVersion>1.0</sf:IDVersion></sf:RegistroAlta>";

    private WireMockServer aeat;
    private ClienteAeatSoap cliente;

    @BeforeEach
    void levantarAeatDeMentira() {
        aeat = new WireMockServer(options().dynamicPort());
        aeat.start();
        cliente = new ClienteAeatSoap(
                new PropiedadesAeat(aeat.baseUrl() + RUTA, Duration.ofSeconds(2)),
                almacenDePrueba());
    }

    @AfterEach
    void apagarla() {
        aeat.stop();
    }

    // --- Escenario 1: aceptado ---

    @Test
    void unEnvioAceptadoDevuelveElCsvYLosDesenlacesDeCadaLinea() {
        responder(200, respuesta("Correcto", "60",
                linea("FA/1", "Correcto", "")));

        RespuestaRemision respuesta = cliente.remitir(OBLIGADO, List.of(REGISTRO));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.CORRECTO);
        assertThat(respuesta.csv()).isEqualTo("A-CSV-DE-PRUEBA");
        assertThat(respuesta.tiempoEspera()).isEqualTo(Duration.ofSeconds(60));
        assertThat(respuesta.lineas()).singleElement()
                .satisfies(linea -> assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.ACEPTADO));
    }

    /**
     * El WSDL declara {@code soapAction=""} para esta operación: la cabecera tiene que ir, y
     * tiene que ir vacía. Es SOAP 1.1, donde no es opcional.
     */
    @Test
    void mandaElSobreSoapConLasCabecerasQueExigeElWsdl() {
        responder(200, respuesta("Correcto", "0", linea("FA/1", "Correcto", "")));

        cliente.remitir(OBLIGADO, List.of(REGISTRO));

        // El charset se compara sin distinguir mayúsculas: el token es insensible a la caja por
        // RFC 2045, y algo de la pila lo normaliza a la forma canónica «UTF-8». Fijar la caja
        // exacta sería probar un detalle que no significa nada.
        aeat.verify(postRequestedFor(urlEqualTo(RUTA))
                .withHeader("Content-Type", matching("(?i)text/xml;\s*charset=utf-8"))
                .withHeader("SOAPAction", equalTo(""))
                .withRequestBody(matching("(?s).*<soapenv:Envelope.*"))
                .withRequestBody(matching("(?s).*RegFactuSistemaFacturacion.*"))
                .withRequestBody(matching("(?s).*<sf:IDVersion>1\\.0</sf:IDVersion>.*")));
    }

    // --- Escenario 2: aceptado con errores ---

    @Test
    void unRegistroAceptadoConErroresQuedaPresentadoYConSuCodigo() {
        responder(200, respuesta("Correcto", "60", linea("FA/1", "AceptadoConErrores",
                "<tikR:CodigoErrorRegistro>2000</tikR:CodigoErrorRegistro>"
                        + "<tikR:DescripcionErrorRegistro>Huella incorrecta</tikR:DescripcionErrorRegistro>")));

        RespuestaRemision respuesta = cliente.remitir(OBLIGADO, List.of(REGISTRO));

        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.ACEPTADO_CON_ERRORES);
            assertThat(linea.codigoError()).isEqualTo(2000);
        });
    }

    // --- Escenario 3: rechazado ---

    @Test
    void unRegistroRechazadoLlegaComoRechazoYNoComoFalloDeRemision() {
        responder(200, respuesta("Incorrecto", "60", linea("FA/1", "Incorrecto",
                "<tikR:CodigoErrorRegistro>1130</tikR:CodigoErrorRegistro>")));

        RespuestaRemision respuesta = cliente.remitir(OBLIGADO, List.of(REGISTRO));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.INCORRECTO);
        assertThat(respuesta.lineas()).singleElement()
                .satisfies(linea -> assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.RECHAZADO));
    }

    // --- Escenario 4: timeout ---

    /**
     * Un timeout <strong>no es un rechazo</strong>: no sabemos si la AEAT llegó a registrar el
     * lote. Por eso lanza en vez de devolver un desenlace, y el envío se queda pendiente para
     * reintentarlo. Si el registro sí había entrado, el reintento traerá el código 3000 y se
     * resolverá como DUPLICADO, que es justo para lo que existe ese estado.
     */
    @Test
    void unTimeoutNoSeConfundeConUnRechazo() {
        aeat.stubFor(post(urlEqualTo(RUTA)).willReturn(
                aResponse().withFixedDelay(5000).withStatus(200)));

        assertThatThrownBy(() -> cliente.remitir(OBLIGADO, List.of(REGISTRO)))
                .isInstanceOf(RemisionFallidaException.class)
                .hasMessageContaining("transporte");
    }

    // --- Lo que no es una respuesta ---

    @Test
    void unErrorHttpNoSeInterpretaComoRespuesta() {
        responder(500, "<html>Servicio no disponible</html>");

        assertThatThrownBy(() -> cliente.remitir(OBLIGADO, List.of(REGISTRO)))
                .isInstanceOf(RemisionFallidaException.class)
                .hasMessageContaining("HTTP 500");
    }

    /** El cuerpo de un error no se propaga: puede traer datos del obligado. */
    @Test
    void elCuerpoDeUnErrorNoAcabaEnElMensaje() {
        responder(503, "<html>NIF 89890001K no autorizado</html>");

        assertThatThrownBy(() -> cliente.remitir(OBLIGADO, List.of(REGISTRO)))
                .hasMessageNotContaining("89890001K");
    }

    @Test
    void unaRespuestaIlegibleTampocoEsUnRechazo() {
        responder(200, "<esto no es xml");

        assertThatThrownBy(() -> cliente.remitir(OBLIGADO, List.of(REGISTRO)))
                .isInstanceOf(RemisionFallidaException.class)
                .hasMessageContaining("no se pudo interpretar");
    }

    // --- Apoyo ---

    private void responder(int estado, String cuerpo) {
        aeat.stubFor(post(urlEqualTo(RUTA)).willReturn(aResponse()
                .withStatus(estado)
                .withHeader("Content-Type", "text/xml; charset=utf-8")
                .withBody(cuerpo)));
    }

    private static String respuesta(String estadoEnvio, String espera, String lineas) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <tikR:RespuestaRegFactuSistemaFacturacion
                        xmlns:tikR="https://www2.agenciatributaria.gob.es/RespuestaSuministro.xsd"
                        xmlns:tik="https://www2.agenciatributaria.gob.es/SuministroInformacion.xsd">
                      <tikR:CSV>A-CSV-DE-PRUEBA</tikR:CSV>
                      <tikR:TiempoEsperaEnvio>%s</tikR:TiempoEsperaEnvio>
                      <tikR:EstadoEnvio>%s</tikR:EstadoEnvio>
                      %s
                    </tikR:RespuestaRegFactuSistemaFacturacion>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(espera, estadoEnvio, lineas);
    }

    private static String linea(String numSerie, String estado, String error) {
        return """
                <tikR:RespuestaLinea>
                  <tikR:IDFactura>
                    <tik:IDEmisorFactura>89890001K</tik:IDEmisorFactura>
                    <tik:NumSerieFactura>%s</tik:NumSerieFactura>
                    <tik:FechaExpedicionFactura>01-01-2024</tik:FechaExpedicionFactura>
                  </tikR:IDFactura>
                  <tikR:EstadoRegistro>%s</tikR:EstadoRegistro>
                  %s
                </tikR:RespuestaLinea>
                """.formatted(numSerie, estado, error);
    }

    /** El mismo PKCS#12 autofirmado que usa el almacén en sus tests. */
    private static AlmacenCertificados almacenDePrueba() {
        return nif -> {
            try (InputStream entrada = Files.newInputStream(
                    Path.of("src/test/resources/certificados/89890001K.p12"))) {
                KeyStore almacen = KeyStore.getInstance("PKCS12");
                almacen.load(entrada, "cambiar".toCharArray());
                return CertificadoDeObligado.desde(almacen, "cambiar".toCharArray(), nif.valor());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
