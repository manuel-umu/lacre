package dev.lacre.verifactu.desglose;

/**
 * Impuesto de una línea de desglose, lista L1 del anexo de la Orden HAC/1177/2024.
 * <p>
 * A diferencia del resto de catálogos, aquí el código no puede ser el nombre de la constante
 * —empieza por dígito—, así que va en un campo aparte.
 */
public enum Impuesto {

    /** Impuesto sobre el Valor Añadido. */
    IVA("01"),

    /** Impuesto sobre la Producción, los Servicios y la Importación de Ceuta y Melilla. */
    IPSI("02"),

    /** Impuesto General Indirecto Canario. */
    IGIC("03"),

    /** Otros. El código 04 no existe en el catálogo. */
    OTROS("05");

    private final String codigo;

    Impuesto(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
