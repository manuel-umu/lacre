package dev.lacre.sif;

import dev.lacre.shared.ValorInvalidoException;

import java.util.Set;

/**
 * Clave que identifica el régimen de IVA o IGIC de la operación, listas L8A y L8B del anexo
 * de la Orden HAC/1177/2024.
 * <p>
 * Es un record y no un enum a propósito. Los códigos empiezan por dígito, así que las
 * constantes tendrían que llamarse {@code C01}, {@code C02}… y el XSD no documenta el
 * significado de ninguna: ponerles nombre sería inventarlo. Envolver el código validado no
 * pierde nada, porque no hay lógica que dependa de un valor concreto.
 */
public record ClaveRegimen(String codigo) {

    /** Los 18 valores del XSD. Faltan el 12, el 13 y el 16: no existen en el catálogo. */
    private static final Set<String> CODIGOS = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "14", "15", "17", "18", "19", "20", "21");

    /**
     * Claves para las que la AEAT no contrasta {@code CuotaTotal} ni {@code ImporteTotal}
     * contra el desglose, según los apartados 16 y 17 de las validaciones oficiales.
     */
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
