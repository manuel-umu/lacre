package dev.lacre.remision;

/**
 * No se ha podido completar una remisión: sin respuesta, error de transporte o respuesta
 * ilegible. No es un rechazo; el envío queda pendiente para reintentarlo.
 */
public class RemisionFallidaException extends RuntimeException {

    public RemisionFallidaException(String motivo) {
        super("No se pudo remitir a la AEAT: " + motivo);
    }

    public RemisionFallidaException(String motivo, Throwable causa) {
        super("No se pudo remitir a la AEAT: " + motivo, causa);
    }
}
