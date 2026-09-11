package dev.lacre.verifactu.registro;

/** Indicador de rechazo previo por la AEAT, lista L17 del anexo de la Orden HAC/1177/2024. */
public enum RechazoPrevio {

    /** No ha habido rechazo previo por la AEAT. */
    N,

    /** Ha habido rechazo previo por la AEAT. */
    S,

    /** El registro no existe en la AEAT, hubiera o no rechazo previo. */
    X;

    public String codigo() {
        return name();
    }
}
