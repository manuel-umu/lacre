package dev.lacre.remision;

import java.util.UUID;

/** Se ha intentado cambiar el desenlace de un envío ya resuelto. */
public class EnvioYaResueltoException extends RuntimeException {

    public EnvioYaResueltoException(UUID envioId, EstadoEnvio actual, EstadoEnvio pretendido) {
        super("El envío " + envioId + " ya está " + actual + " y no puede pasar a " + pretendido);
    }

    public EnvioYaResueltoException(UUID envioId, String motivo) {
        super("El envío " + envioId + " " + motivo);
    }
}
