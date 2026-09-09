package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.EstadoEnvioAeat;
import dev.lacre.remision.EstadoRegistroAeat;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.remision.RespuestaRemision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La lectura de la respuesta de la AEAT, sobre documentos con la forma que fija
 * {@code RespuestaSuministro.xsd}.
 * <p>
 * Aquí se cubren a nivel de documento los desenlaces que exige {@code CLAUDE.md} —aceptado,
 * aceptado con errores y rechazado—, más el duplicado, que es el que no se ve venir. El timeout
 * y el transporte son del cliente HTTP y se prueban con WireMock.
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

    // --- Los cuatro desenlaces ---

    @Test
    void unEnvioCorrectoSeLeeEntero() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(
                respuesta("Correcto", linea("FA/1", "Correcto", "")));

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
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("Correcto",
                linea("FA/1", "AceptadoConErrores",
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
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("Incorrecto",
                linea("FA/1", "Incorrecto",
                        "<tikR:CodigoErrorRegistro>1130</tikR:CodigoErrorRegistro>"
                                + "<tikR:DescripcionErrorRegistro>Caracteres no permitidos</tikR:DescripcionErrorRegistro>")));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.INCORRECTO);
        assertThat(respuesta.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.desenlace()).isEqualTo(EstadoEnvio.RECHAZADO);
            assertThat(linea.esDuplicado()).isFalse();
        });
    }

    /**
     * El caso que no se ve venir: un {@code Incorrecto} con código 3000 significa que el registro
     * <strong>ya estaba presentado</strong>. Leerlo como rechazo llevaría a reintentar para
     * siempre algo que ya está hecho.
     */
    @Test
    void unRechazoPorDuplicadoNoEsUnRechazo() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("Incorrecto",
                linea("FA/1", "Incorrecto",
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

    // --- Varias líneas ---

    /**
     * Una línea se cierra cuando empieza la siguiente, porque sus dos últimos campos son
     * opcionales y no hay nada que marque su fin. Con tres líneas de las que solo la de en medio
     * trae error, un fallo en esa lógica se ve enseguida: el error se pegaría a la equivocada.
     */
    @Test
    void separaBienLasLineasAunqueSoloAlgunasTraiganError() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("ParcialmenteCorrecto",
                linea("FA/1", "Correcto", "")
                        + linea("FA/2", "Incorrecto",
                        "<tikR:CodigoErrorRegistro>1130</tikR:CodigoErrorRegistro>")
                        + linea("FA/3", "Correcto", "")));

        assertThat(respuesta.estado()).isEqualTo(EstadoEnvioAeat.PARCIALMENTE_CORRECTO);
        assertThat(respuesta.lineas()).extracting(linea -> linea.idFactura().numSerieFactura())
                .containsExactly("FA/1", "FA/2", "FA/3");
        assertThat(respuesta.lineas()).extracting(LineaRespuesta::codigoError)
                .containsExactly(null, 1130, null);
    }

    @Test
    void unEnvioSinLineasSeLeeIgual() {
        RespuestaRemision respuesta = LectorRespuestaAeat.leer(respuesta("Incorrecto", ""));

        assertThat(respuesta.lineas()).isEmpty();
    }

    // --- Lo que no se puede leer ---

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
