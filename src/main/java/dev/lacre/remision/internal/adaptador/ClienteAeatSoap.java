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
 * Habla con el servicio web de la AEAT: SOAP 1.1 en modo {@code document}/{@code literal} sobre
 * HTTPS con autenticación mutua, según el
 * <a href="../../../../../../docs/adr/0006-cliente-soap-a-mano.md">ADR 0006</a>.
 * <p>
 * Sin JAX-WS ni Spring-WS: es <strong>una</strong> operación, sin WS-Security ni WS-Addressing,
 * cuyo cuerpo describe íntegramente un XSD que ya validamos. El sobre lo pone
 * {@link EscritorLote} y la respuesta la lee {@link LectorRespuestaAeat}.
 * <p>
 * <strong>Un {@link HttpClient} por obligado, y no uno compartido</strong>: el TLS mutuo se
 * autentica con el certificado de <em>cada</em> obligado, así que el material de clave es parte
 * de la conexión y no puede compartirse entre ellos. Reutilizar un cliente sería remitir por
 * cuenta de quien no toca.
 */
@Component
class ClienteAeatSoap implements ClienteAeat {

    private static final Logger log = LoggerFactory.getLogger(ClienteAeatSoap.class);

    /** El WSDL declara {@code soapAction=""} para esta operación: la cabecera va, pero vacía. */
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
            // Mismo criterio que abajo: el cuerpo va a DEBUG y no al mensaje. Un 403 se ha visto
            // de verdad contra el Portal de Pruebas —certificado no autorizado para el
            // servicio—, y sin poder mirar lo que venga es un callejón sin salida.
            // Las cabeceras importan tanto como el cuerpo: un 3xx no trae explicación en el
            // cuerpo, la trae en Location, y un 302 contra este servicio significa casi siempre
            // que no llegó un certificado de cliente utilizable.
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
            // El cuerpo va a DEBUG y no al mensaje de la excepción: puede traer datos del
            // obligado, y el mensaje acaba en el log de quien integra. Pero sin poder verlo,
            // una respuesta que no entendemos es indiagnosticable, y eso es peor: quien opera
            // esto necesita saber qué contestó la AEAT. Se activa cuando hace falta.
            log.debug("Respuesta no interpretable de la AEAT (HTTP {}): {}",
                    respuesta.statusCode(), respuesta.body());
            throw new RemisionFallidaException("la respuesta no se pudo interpretar", e);
        }
    }

    /**
     * El almacén de confianza va a {@code null} a propósito: eso deja el del sistema, que es
     * quien valida el certificado de servidor de la AEAT. Sustituirlo por uno propio obligaría a
     * mantener las CA a mano y a arreglarlo cada vez que la AEAT rote la suya.
     */
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
