package dev.lacre.api.internal.correlacion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asigna a cada petición un identificador de correlación que sale en sus trazas y vuelve en la
 * cabecera {@code X-Request-Id}. Respeta el del cliente si es un identificador seguro de escribir
 * en un log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FiltroDeCorrelacion extends OncePerRequestFilter {

    static final String CABECERA = "X-Request-Id";
    static final String CLAVE_MDC = "correlacion";

    private static final Pattern ADMISIBLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final Supplier<UUID> generadorDeIdentificadores;

    FiltroDeCorrelacion(Supplier<UUID> generadorDeIdentificadores) {
        this.generadorDeIdentificadores = generadorDeIdentificadores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String identificador = identificadorDe(peticion.getHeader(CABECERA));
        respuesta.setHeader(CABECERA, identificador);
        MDC.put(CLAVE_MDC, identificador);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(CLAVE_MDC);
        }
    }

    String identificadorDe(String cabecera) {
        if (cabecera != null && ADMISIBLE.matcher(cabecera).matches()) {
            return cabecera;
        }
        return generadorDeIdentificadores.get().toString();
    }
}
