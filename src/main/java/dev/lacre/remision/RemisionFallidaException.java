package dev.lacre.remision;

/**
 * No se ha podido completar una remisión: la AEAT no contestó, contestó un error de transporte,
 * o contestó algo ilegible.
 * <p>
 * <strong>No es un rechazo.</strong> Un rechazo viene dentro de una respuesta correcta y es un
 * desenlace: el registro no quedó presentado y hay que subsanarlo. Esto otro es no saber qué pasó,
 * y ante la duda el envío se queda en {@code PENDIENTE} para reintentarlo.
 */
public class RemisionFallidaException extends RuntimeException {

    public RemisionFallidaException(String motivo) {
        super("No se pudo remitir a la AEAT: " + motivo);
    }

    public RemisionFallidaException(String motivo, Throwable causa) {
        super("No se pudo remitir a la AEAT: " + motivo, causa);
    }
}
