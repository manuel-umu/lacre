package dev.lacre.sif.registro;

/**
 * Identifica si una factura rectificativa lo es por sustitución o por diferencias, lista L3
 * del anexo de la Orden HAC/1177/2024.
 */
public enum ClaveTipoRectificativa {

    /** Sustitutiva: rectifica sustituyendo los importes de la factura original. */
    S,

    /** Incremental: rectifica por diferencias respecto de la factura original. */
    I;

    public String codigo() {
        return name();
    }
}
