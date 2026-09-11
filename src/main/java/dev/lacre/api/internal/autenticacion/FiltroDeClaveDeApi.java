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
 * Deja pasar a {@code /v1/**} solo con la clave del despliegue.
 * <p>
 * Un filtro de servlet y no Spring Security. Para una sola clave estática, el starter de
 * security traería una cadena de filtros, un contexto de autenticación y sus valores por
 * defecto a cambio de sustituir estas treinta líneas, y sería una dependencia nueva en un stack
 * que {@code CLAUDE.md} declara fijo. Si algún día hacen falta roles, reglas por método o
 * cabeceras de seguridad, ese es el momento de cambiarlo, y entonces sí lo vale.
 * <p>
 * Tres detalles que no son adorno:
 * <ul>
 * <li><strong>Se comparan los resúmenes SHA-256, no las claves.</strong> Así la comparación es
 *     de 32 bytes contra 32 bytes pase lo que pase, y ni siquiera la longitud de la clave
 *     presentada se filtra por el tiempo de respuesta.</li>
 * <li><strong>{@link MessageDigest#isEqual} y no {@code equals}.</strong> Recorre los dos
 *     arreglos enteros siempre; un {@code equals} sale en el primer byte distinto y eso se
 *     mide.</li>
 * <li><strong>Falta la cabecera y clave incorrecta dan la misma respuesta.</strong>
 *     Distinguirlas convertiría el endpoint en un oráculo.</li>
 * </ul>
 * La clave presentada <strong>no se registra nunca</strong>. El aviso lleva la ruta y el origen,
 * que es lo que sirve para ver un ataque por fuerza bruta.
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
