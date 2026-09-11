package dev.lacre.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Huella SHA-256 de un registro de facturación: 64 caracteres hexadecimales en mayúsculas.
 * No se recortan espacios.
 */
public record Huella(String valor) {

    private static final Pattern HEXADECIMAL_DE_64 = Pattern.compile("[0-9A-F]{64}");

    public Huella {
        if (valor == null) {
            throw new ValorInvalidoException("La huella no puede ser nula");
        }
        valor = valor.toUpperCase(Locale.ROOT);
        if (!HEXADECIMAL_DE_64.matcher(valor).matches()) {
            throw new ValorInvalidoException(
                    "La huella debe ser 64 caracteres hexadecimales: " + valor);
        }
    }
}
