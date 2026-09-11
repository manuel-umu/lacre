package dev.lacre.api.internal.autenticacion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Autentica las peticiones a {@code /v1/**} con la clave del despliegue. Compara resúmenes
 * SHA-256 en tiempo constante, responde igual ante cabecera ausente y clave incorrecta, y nunca
 * registra la clave presentada.
 */
class FiltroDeClaveDeApi extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroDeClaveDeApi.class);

    private static final String PREFIJO = "Bearer ";

    private final byte[] resumenEsperado;
    private final ObjectMapper json;

    FiltroDeClaveDeApi(PropiedadesApi propiedades, ObjectMapper json) {
        this.resumenEsperado = sha256(propiedades.clave());
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        if (!autorizada(peticion)) {
            rechazar(peticion, respuesta);
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private boolean autorizada(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            return false;
        }
        return MessageDigest.isEqual(resumenEsperado, sha256(cabecera.substring(PREFIJO.length())));
    }

    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta)
            throws IOException {
        log.warn("Petición sin credencial válida a {} desde {}",
                peticion.getRequestURI(), peticion.getRemoteAddr());

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Falta la cabecera Authorization con la clave de la API, o no es válida. "
                        + "El formato es: Authorization: Bearer <clave>");
        problema.setTitle("No autenticado");
        problema.setProperty("codigo", "no-autenticado");

        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(respuesta.getOutputStream(), problema);
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", imposible);
        }
    }
}
