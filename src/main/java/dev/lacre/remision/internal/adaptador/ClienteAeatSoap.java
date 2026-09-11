package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.AlmacenCertificados;
import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.remision.RespuestaRemision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;

/**
 * Cliente del servicio web de la AEAT: SOAP 1.1 {@code document}/{@code literal} sobre HTTPS con
 * autenticación mutua, con {@link HttpClient} de la JDK. Un cliente por obligado, porque el TLS
 * se autentica con el certificado de cada uno.
 */
@Component
class ClienteAeatSoap implements ClienteAeat {

    private static final Logger log = LoggerFactory.getLogger(ClienteAeatSoap.class);

    /** El WSDL declara {@code soapAction=""} para esta operación. */
    private static final String SOAP_ACTION = "";

    private static final String TIPO_CONTENIDO = "text/xml; charset=utf-8";

    private final PropiedadesAeat propiedades;
    private final AlmacenCertificados certificados;

    ClienteAeatSoap(PropiedadesAeat propiedades, AlmacenCertificados certificados) {
        this.propiedades = propiedades;
        this.certificados = certificados;
    }

    @Override
    public RespuestaRemision remitir(ObligadoTributario obligado, List<String> registros) {
        String sobre = EscritorLote.envolver(obligado, registros);
        CertificadoDeObligado certificado = certificados.de(obligado.nif());

        HttpRequest peticion = HttpRequest.newBuilder(URI.create(propiedades.endpoint()))
                .header("Content-Type", TIPO_CONTENIDO)
                .header("SOAPAction", SOAP_ACTION)
                .timeout(propiedades.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(sobre, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> respuesta;
        try (HttpClient cliente = clienteDe(certificado)) {
            respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RemisionFallidaException("fallo de transporte contra " + propiedades.endpoint(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemisionFallidaException("la remisión se interrumpió", e);
        }

        if (respuesta.statusCode() != 200) {
            // Cuerpo y cabeceras van a DEBUG, no al mensaje: pueden traer datos del obligado.
            log.debug("La AEAT respondió HTTP {} con cabeceras {} y cuerpo: {}",
                    respuesta.statusCode(), respuesta.headers().map(), respuesta.body());
            throw new RemisionFallidaException(
                    "la AEAT respondió HTTP " + respuesta.statusCode() + ", no un mensaje SOAP");
        }

        try {
            RespuestaRemision leida = LectorRespuestaAeat.leer(respuesta.body());
            log.info("Lote de {} registros remitido por {}: envío {}, esperar {} s antes del siguiente",
                    registros.size(), obligado.nif().valor(), leida.estado(),
                    leida.tiempoEspera().toSeconds());
            return leida;
        } catch (RespuestaIlegibleException e) {
            // El cuerpo va a DEBUG, no al mensaje: puede traer datos del obligado.
            log.debug("Respuesta no interpretable de la AEAT (HTTP {}): {}",
                    respuesta.statusCode(), respuesta.body());
            throw new RemisionFallidaException("la respuesta no se pudo interpretar", e);
        }
    }

    /** Almacén de confianza del sistema, que valida el certificado de servidor de la AEAT. */
    private HttpClient clienteDe(CertificadoDeObligado certificado) {
        try {
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(certificado.gestoresDeClave(), null, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(contexto)
                    .connectTimeout(propiedades.timeout())
                    .build();
        } catch (GeneralSecurityException e) {
            throw new RemisionFallidaException("no se pudo preparar el TLS mutuo", e);
        }
    }
}
