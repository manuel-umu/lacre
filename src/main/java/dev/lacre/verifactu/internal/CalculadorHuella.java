package dev.lacre.verifactu.internal;

import dev.lacre.shared.Huella;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Aplica SHA-256 a la cadena canónica de un registro.
 * <p>
 * El algoritmo sí está confirmado por la norma; lo que no lo está es sobre qué cadena se
 * aplica, y de eso se ocupa el canonicalizador.
 */
public final class CalculadorHuella {

    private static final String ALGORITMO = "SHA-256";

    private CalculadorHuella() {
    }

    /**
     * @implNote UTF-8 es un supuesto pendiente de confirmar contra la especificación de la AEAT.
     * Pasar de cadena a bytes exige elegir una codificación y no hay forma de escribir esta clase
     * sin decidirla; queda anotado para que conste como supuesto y no como hecho verificado.
     */
    public static Huella calcular(String cadenaCanonica) {
        Objects.requireNonNull(cadenaCanonica, "cadenaCanonica");
        byte[] resumen = nuevoDigest().digest(cadenaCanonica.getBytes(StandardCharsets.UTF_8));
        return new Huella(HexFormat.of().withUpperCase().formatHex(resumen));
    }

    private static MessageDigest nuevoDigest() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITMO + " es obligatorio en toda JVM", e);
        }
    }
}
