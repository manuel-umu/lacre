package dev.lacre.api.internal.obligados;

import dev.lacre.shared.ValorInvalidoException;
import jakarta.validation.constraints.NotBlank;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Datos de un obligado tal y como los envía el cliente. El NIF no va aquí: identifica el recurso y
 * viaja en la ruta.
 *
 * @param zonaHoraria identificador IANA, como {@code Europe/Madrid} o {@code Atlantic/Canary}
 */
public record PeticionObligado(
        @NotBlank String nombreRazon, @NotBlank String zonaHoraria) {

    /**
     * Solo zonas con reglas propias: un desfase fijo como {@code +01:00} o {@code UTC} no tiene
     * horario de verano y declararía un huso falso durante medio año.
     */
    ZoneId zona() {
        ZoneId zona;
        try {
            zona = ZoneId.of(zonaHoraria.strip());
        } catch (DateTimeException e) {
            throw new ValorInvalidoException("Zona horaria desconocida: " + zonaHoraria
                    + ". Use un identificador IANA, como Europe/Madrid o Atlantic/Canary");
        }
        if (zona.getRules().isFixedOffset()) {
            throw new ValorInvalidoException("La zona horaria " + zonaHoraria
                    + " es un desfase fijo, sin horario de verano, y el huso de los registros "
                    + "sería falso media parte del año. Use Europe/Madrid, Atlantic/Canary o "
                    + "Africa/Ceuta");
        }
        return zona;
    }
}
