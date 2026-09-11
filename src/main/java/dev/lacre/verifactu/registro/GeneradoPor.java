package dev.lacre.verifactu.registro;

/** Quién genera un registro de anulación, lista L5 del anexo de la Orden HAC/1177/2024. */
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
