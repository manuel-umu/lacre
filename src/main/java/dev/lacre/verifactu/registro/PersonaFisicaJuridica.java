package dev.lacre.verifactu.registro;

import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Persona física o jurídica, española o extranjera, {@code PersonaFisicaJuridicaType} del XSD.
 * Sirve para destinatarios, terceros y el titular del sistema informático.
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
