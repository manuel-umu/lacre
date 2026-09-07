package dev.lacre.sif.registro;

/**
 * Quién genera un registro de anulación, lista L5 del anexo de la Orden HAC/1177/2024.
 * <p>
 * No coincide con {@link EmitidaPor}, que es del registro de alta y solo tiene dos valores:
 * aquí se añade el propio expedidor de la factura anulada.
 */
public enum GeneradoPor {

    /** Expedidor: el obligado a expedir la factura que se anula. */
    E,

    /** Destinatario. */
    D,

    /** Tercero. */
    T;

    public String codigo() {
        return name();
    }
}
