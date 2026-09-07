package dev.lacre.sif;

/**
 * Calificación de la operación, lista L9 del anexo de la Orden HAC/1177/2024.
 * <p>
 * El nombre de cada constante es el código que viaja en el XML; no es un identificador
 * interno y renombrarlo rompe la conformidad.
 */
public enum CalificacionOperacion implements Calificacion {

    /** Operación sujeta y no exenta, sin inversión del sujeto pasivo. */
    S1,

    /** Operación sujeta y no exenta, con inversión del sujeto pasivo. */
    S2,

    /** Operación no sujeta, artículos 7, 14 y otros. */
    N1,

    /** Operación no sujeta por reglas de localización. */
    N2;

    @Override
    public String codigo() {
        return name();
    }
}
