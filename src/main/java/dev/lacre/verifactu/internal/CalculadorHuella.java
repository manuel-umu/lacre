package dev.lacre.verifactu.internal;

import dev.lacre.shared.Huella;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Aplica SHA-256 a la cadena canónica de un registro, codificada en UTF-8. */
public final class CalculadorHuella {

    private static final String ALGORITMO = "SHA-256";

    private CalculadorHuella() {}

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
