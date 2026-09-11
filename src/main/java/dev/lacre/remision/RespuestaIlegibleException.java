package dev.lacre.remision;

/** La AEAT ha respondido algo que no se puede interpretar. */
public class RespuestaIlegibleException extends RuntimeException {

    public RespuestaIlegibleException(String motivo) {
        super("Respuesta de la AEAT ilegible: " + motivo);
    }

    public RespuestaIlegibleException(String motivo, Throwable causa) {
        super("Respuesta de la AEAT ilegible: " + motivo, causa);
    }
}
