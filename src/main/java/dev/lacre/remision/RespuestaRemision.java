package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;

import java.time.Duration;
import java.util.List;

/**
 * Respuesta completa de la AEAT a un envío.
 *
 * @param tiempoEspera lo que hay que esperar antes del siguiente envío. <strong>No es una
 *                     sugerencia</strong>: el mecanismo de control de flujo de la AEAT lo impone,
 *                     y saltárselo es motivo de rechazo. Lo consume el despachador de la 6.3.
 * @param csv código seguro de verificación de la presentación; ausente si el envío se rechazó
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
