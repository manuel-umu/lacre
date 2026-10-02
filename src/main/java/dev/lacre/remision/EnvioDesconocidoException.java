package dev.lacre.remision;

import java.util.UUID;

/** No hay ningún envío con ese identificador entre los del obligado. */
public class EnvioDesconocidoException extends RuntimeException {

    public EnvioDesconocidoException(UUID envioId) {
        super("No existe el envío " + envioId);
    }
}
