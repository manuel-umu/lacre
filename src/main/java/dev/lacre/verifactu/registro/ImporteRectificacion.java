package dev.lacre.verifactu.registro;

import dev.lacre.shared.Importe;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Base y cuota sustituidas en una factura rectificativa por sustitución,
 * {@code DesgloseRectificacionType} del XSD.
 *
 * @param cuotaRecargoRectificado opcional: puede ser nulo
 */
public record ImporteRectificacion(Importe baseRectificada, Importe cuotaRectificada,
                                   Importe cuotaRecargoRectificado) {

    public ImporteRectificacion {
        if (baseRectificada == null) {
            throw new ValorInvalidoException("La base rectificada es obligatoria");
        }
        if (cuotaRectificada == null) {
            throw new ValorInvalidoException("La cuota rectificada es obligatoria");
        }
    }
}
