package dev.lacre.sif.registro;

import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Persona física o jurídica, española o extranjera, {@code PersonaFisicaJuridicaType} del XSD.
 * <p>
 * Sirve para destinatarios, terceros y el titular del sistema informático. El identificador es
 * un {@link IdentificadorFiscal}, de modo que un cliente extranjero sin NIF español se
 * represente con {@code IdOtro} sin necesidad de campos anulables.
 */
public record PersonaFisicaJuridica(String nombreRazon, IdentificadorFiscal identificador) {

    public static final int MAXIMO_LONGITUD_NOMBRE = 120;

    public PersonaFisicaJuridica {
        if (nombreRazon == null || nombreRazon.isBlank()) {
            throw new ValorInvalidoException("El nombre o razón social es obligatorio");
        }
        nombreRazon = nombreRazon.strip();
        if (nombreRazon.length() > MAXIMO_LONGITUD_NOMBRE) {
            throw new ValorInvalidoException(
                    "El nombre o razón social admite como máximo " + MAXIMO_LONGITUD_NOMBRE
                            + " caracteres y tiene " + nombreRazon.length());
        }
        if (identificador == null) {
            throw new ValorInvalidoException("El identificador fiscal es obligatorio");
        }
    }
}
