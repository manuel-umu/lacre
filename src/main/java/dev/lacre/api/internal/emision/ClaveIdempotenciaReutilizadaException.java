package dev.lacre.api.internal.emision;

/**
 * La misma {@code Idempotency-Key} ha llegado con una factura distinta. Se responde 409 en vez
 * de devolver el registro existente.
 */
public class ClaveIdempotenciaReutilizadaException extends RuntimeException {

    public ClaveIdempotenciaReutilizadaException(String clave) {
        super("La clave de idempotencia '" + clave + "' ya se usó para otra factura de este "
                + "obligado. Usa una clave distinta por cada factura.");
    }
}
