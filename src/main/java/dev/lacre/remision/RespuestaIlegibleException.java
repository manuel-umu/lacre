package dev.lacre.remision;

/**
 * La AEAT ha respondido algo que no se puede interpretar.
 * <p>
 * Es distinto de que responda un rechazo: un rechazo se entiende y se guarda. Esto es que el
 * documento no dice lo que el esquema promete, y entonces no se sabe si el registro quedó
 * presentado o no. El envío se deja como estaba, para que se reintente.
 */
public class RespuestaIlegibleException extends RuntimeException {

    public RespuestaIlegibleException(String motivo) {
        super("Respuesta de la AEAT ilegible: " + motivo);
    }

    public RespuestaIlegibleException(String motivo, Throwable causa) {
        super("Respuesta de la AEAT ilegible: " + motivo, causa);
    }
}
