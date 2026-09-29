package dev.lacre.verifactu.desglose;

import dev.lacre.shared.ValorInvalidoException;
import java.util.Set;

/**
 * Clave del régimen de IVA o IGIC de la operación, listas L8A y L8B del anexo de la
 * Orden HAC/1177/2024.
 */
public record ClaveRegimen(String codigo) {

    /** Códigos admitidos por el XSD. */
    private static final Set<String> CODIGOS = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "14", "15", "17", "18", "19", "20", "21");

    /** Claves para las que la AEAT no contrasta los totales contra el desglose. */
    private static final Set<String> SIN_CUADRE_DE_TOTALES = Set.of("03", "05", "06", "08", "09");

    public ClaveRegimen {
        if (codigo == null) {
            throw new ValorInvalidoException("La clave de régimen no puede ser nula");
        }
        codigo = codigo.strip();
        if (!CODIGOS.contains(codigo)) {
            throw new ValorInvalidoException("Clave de régimen desconocida: " + codigo);
        }
    }

    /** Si esta clave exime al registro de que sus totales cuadren con el desglose. */
    public boolean excluyeCuadreDeTotales() {
        return SIN_CUADRE_DE_TOTALES.contains(codigo);
    }
}
