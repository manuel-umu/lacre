package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.AlmacenCertificados;
import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.ConsultaAeat;
import dev.lacre.remision.RegistroEnAeat;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.remision.ResultadoConsulta;
import dev.lacre.verifactu.registro.IdFactura;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cliente del servicio web de la AEAT, para remitir y para consultar: SOAP 1.1
 * {@code document}/{@code literal} sobre HTTPS con
 * autenticación mutua, con {@link HttpClient} de la JDK. Un cliente por obligado, porque el TLS
 * se autentica con el certificado de cada uno.
 */
@Component
class ClienteAeatSoap implements ClienteAeat, ConsultaAeat {

    private static final Logger log = LoggerFactory.getLogger(ClienteAeatSoap.class);

    /** El WSDL declara {@code soapAction=""} para esta operación. */
    private static final String SOAP_ACTION = "";

    private static final String TIPO_CONTENIDO = "text/xml; charset=utf-8";

    /** Páginas de 10.000 registros que se piden como mucho en una consulta. */
    static final int MAXIMO_PAGINAS = 10;

    private final PropiedadesAeat propiedades;
    private final AlmacenCertificados certificados;

    ClienteAeatSoap(PropiedadesAeat propiedades, AlmacenCertificados certificados) {
        this.propiedades = propiedades;
        this.certificados = certificados;
    }

    @Override
    public RespuestaRemision remitir(ObligadoTributario obligado, List<String> registros) {
        String cuerpo = intercambiar(obligado, EscritorLote.envolver(obligado, registros));
        try {
            RespuestaRemision leida = LectorRespuestaAeat.leer(cuerpo);
            log.info(
                    "Lote de {} registros remitido por {}: envío {}, esperar {} s antes del siguiente",
                    registros.size(),
                    obligado.nif().valor(),
                    leida.estado(),
                    leida.tiempoEspera().toSeconds());
            return leida;
        } catch (RespuestaIlegibleException e) {
            // El cuerpo va a DEBUG, no al mensaje: puede traer datos del obligado.
            log.debug("Respuesta no interpretable de la AEAT: {}", cuerpo);
            throw new RemisionFallidaException("la respuesta no se pudo interpretar", e);
        }
    }

    /** Recorre las páginas de la consulta hasta la última o hasta {@link #MAXIMO_PAGINAS}. */
    @Override
    public ResultadoConsulta consultar(ObligadoTributario obligado, YearMonth periodo) {
        // La clave de paginación puede repetir el último registro de la página anterior.
        Set<RegistroEnAeat> registros = new LinkedHashSet<>();
        IdFactura clave = null;
        for (int pagina = 0; pagina < MAXIMO_PAGINAS; pagina++) {
            String cuerpo = intercambiar(obligado, EscritorConsulta.envolver(obligado, periodo, clave));
            LectorRespuestaConsulta.Pagina leida;
            try {
                leida = LectorRespuestaConsulta.leer(cuerpo);
            } catch (RespuestaIlegibleException e) {
                log.debug("Respuesta de consulta no interpretable de la AEAT: {}", cuerpo);
                throw new RemisionFallidaException("la respuesta de la consulta no se pudo interpretar", e);
            }
            registros.addAll(leida.registros());
            if (leida.siguiente() == null) {
                log.info("Consulta de {} en {}: {} registros", obligado.nif().valor(), periodo, registros.size());
                return new ResultadoConsulta(List.copyOf(registros), true);
            }
            clave = leida.siguiente();
        }
        log.warn(
                "La consulta de {} en {} pasa de {} páginas; se corta",
                obligado.nif().valor(),
                periodo,
                MAXIMO_PAGINAS);
        return new ResultadoConsulta(List.copyOf(registros), false);
    }

    /** Envía el sobre con el certificado del obligado y devuelve el cuerpo de una respuesta HTTP 200. */
    private String intercambiar(ObligadoTributario obligado, String sobre) {
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
            throw new RemisionFallidaException("la comunicación con la AEAT se interrumpió", e);
        }

        if (respuesta.statusCode() != 200) {
            // Cuerpo y cabeceras van a DEBUG, no al mensaje: pueden traer datos del obligado.
            log.debug(
                    "La AEAT respondió HTTP {} con cabeceras {} y cuerpo: {}",
                    respuesta.statusCode(),
                    respuesta.headers().map(),
                    respuesta.body());
            throw new RemisionFallidaException(
                    "la AEAT respondió HTTP " + respuesta.statusCode() + ", no un mensaje SOAP");
        }
        return respuesta.body();
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
