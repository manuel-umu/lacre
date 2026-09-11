package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;

import java.time.Duration;
import java.util.List;

/**
 * Respuesta completa de la AEAT a un envío.
 *
 * @param tiempoEspera espera obligatoria antes del siguiente envío, según el control de flujo
 * @param csv          código seguro de verificación; ausente si el envío se rechazó
 */
public record RespuestaRemision(EstadoEnvioAeat estado, Duration tiempoEspera, String csv,
                                List<LineaRespuesta> lineas) {

    public RespuestaRemision {
        if (estado == null) {
            throw new ValorInvalidoException("La respuesta de la AEAT trae siempre estado de envío");
        }
        tiempoEspera = tiempoEspera == null ? Duration.ZERO : tiempoEspera;
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }
}
