package dev.lacre.remision;

import java.util.UUID;

/** El envío forma parte de un lote que se está remitiendo en este momento. */
public class EnvioEnCursoException extends RuntimeException {

    public EnvioEnCursoException(UUID envioId) {
        super("El envío " + envioId + " se está remitiendo ahora mismo");
    }
}
