package dev.lacre.verifactu.registro;

import dev.lacre.shared.Huella;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Enlace con el registro anterior de la cadena, {@code EncadenamientoFacturaAnteriorType} del
 * XSD. El XML lleva la identificación completa; en la huella solo entra {@link #huella()}.
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
