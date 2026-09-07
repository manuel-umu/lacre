package dev.lacre.sif;

/**
 * Indicador de rechazo previo por la AEAT, lista L17 del anexo de la Orden HAC/1177/2024.
 * <p>
 * No es un booleano: tiene un tercer valor para el registro que no llegó a existir en la AEAT.
 */
public enum RechazoPrevio {

    /** No ha habido rechazo previo por la AEAT. */
    N,

    /** Ha habido rechazo previo por la AEAT. */
    S,

    /**
     * El registro no existe en la AEAT, con independencia de si hubo rechazo previo. Es el caso
     * de un registro generado en un SIF del obligado que nunca se remitió, por ejemplo al pasar
     * de no VERI*FACTU a VERI*FACTU.
     */
    X;

    public String codigo() {
        return name();
    }
}
