package dev.lacre.verifactu.registro;

/**
 * Clave del tipo de factura, lista L2 del anexo de la Orden HAC/1177/2024. El nombre de cada
 * constante es el código que viaja en el XML y en la huella.
 */
public enum TipoFactura {

    /** Factura (art. 6, 7.2 y 7.3 del RD 1619/2012). */
    F1,

    /** Factura simplificada y facturas sin identificación del destinatario art. 6.1.d) RD 1619/2012. */
    F2,

    /** Factura emitida en sustitución de facturas simplificadas facturadas y declaradas. */
    F3,

    /** Factura rectificativa (art. 80.1, 80.2 y error fundado en derecho). */
    R1,

    /** Factura rectificativa (art. 80.3). */
    R2,

    /** Factura rectificativa (art. 80.4). */
    R3,

    /** Factura rectificativa (resto). */
    R4,

    /** Factura rectificativa en facturas simplificadas. */
    R5;

    /** Código tal y como aparece en el XML y en la huella. */
    public String codigo() {
        return name();
    }

    /** Si es una de las cinco claves de factura rectificativa. */
    public boolean esRectificativa() {
        return name().charAt(0) == 'R';
    }
}
