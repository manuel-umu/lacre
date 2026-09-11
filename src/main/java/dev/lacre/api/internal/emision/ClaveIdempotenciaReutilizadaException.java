package dev.lacre.api.internal.emision;

/**
 * La misma {@code Idempotency-Key} ha llegado con otra factura distinta.
 * <p>
 * No se responde con el registro que ya existía, aunque técnicamente sería lo más cómodo: el
 * ERP daría por registrada una factura que no lo está, y no habría forma de que se enterase.
 * Un 409 es ruidoso a propósito.
 */
public class ClaveIdempotenciaReutilizadaException extends RuntimeException {

    public ClaveIdempotenciaReutilizadaException(String clave) {
        super("La clave de idempotencia '" + clave + "' ya se usó para otra factura de este "
                + "obligado. Usa una clave distinta por cada factura.");
    }
}
