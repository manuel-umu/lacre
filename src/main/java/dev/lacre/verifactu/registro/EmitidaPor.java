package dev.lacre.verifactu.registro;

/**
 * Indica quién expide la factura cuando no lo hace el propio obligado, lista L6 del anexo de
 * la Orden HAC/1177/2024.
 */
public enum EmitidaPor {

    /** La expide el destinatario. */
    D,

    /** La expide un tercero. */
    T;

    public String codigo() {
        return name();
    }
}
