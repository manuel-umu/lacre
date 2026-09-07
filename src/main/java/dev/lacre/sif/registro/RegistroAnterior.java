package dev.lacre.sif.registro;

import dev.lacre.shared.Huella;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Enlace con el registro inmediatamente anterior de la cadena,
 * {@code EncadenamientoFacturaAnteriorType} del XSD.
 * <p>
 * Lleva la identificación completa de la factura anterior y no solo su huella porque el XML la
 * exige entera. Al cálculo de la huella, en cambio, solo entra {@link #huella()}: el documento
 * de la AEAT es explícito en que el campo concatenado es únicamente el hash anterior.
 */
public record RegistroAnterior(IdFactura idFactura, Huella huella) {

    public RegistroAnterior {
        if (idFactura == null) {
            throw new ValorInvalidoException("La identificación del registro anterior es obligatoria");
        }
        if (huella == null) {
            throw new ValorInvalidoException("La huella del registro anterior es obligatoria");
        }
    }
}
