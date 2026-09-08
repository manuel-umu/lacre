package dev.lacre.remision;

import java.util.UUID;

/**
 * Se ha intentado cambiar el desenlace de un envío que ya lo tenía.
 * <p>
 * No es una comprobación de cortesía: los cuatro estados finales son terminales porque un
 * registro ya presentado ante la AEAT no se vuelve a presentar. Si esto salta, alguien está a
 * punto de duplicar una presentación o de borrar la constancia de un rechazo.
 */
public class EnvioYaResueltoException extends RuntimeException {

    public EnvioYaResueltoException(UUID envioId, EstadoEnvio actual, EstadoEnvio pretendido) {
        super("El envío " + envioId + " ya está " + actual + " y no puede pasar a " + pretendido);
    }
}
