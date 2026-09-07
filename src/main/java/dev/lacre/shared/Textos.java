package dev.lacre.shared;

/**
 * Validación de los campos de texto del registro de facturación, cuyos límites de longitud
 * fija el XSD de la AEAT ({@code TextMax30Type}, {@code TextMax120Type}…).
 * <p>
 * Se recortan los espacios de los extremos, igual que exige el documento de la huella para el
 * cálculo del hash.
 */
public final class Textos {

    private Textos() {
    }

    /** Exige un texto no vacío que no supere el máximo, y lo devuelve recortado. */
    public static String obligatorio(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValorInvalidoException(campo + " es obligatorio");
        }
        return dentroDelMaximo(valor.strip(), maximo, campo);
    }

    /** Admite nulo. Un texto en blanco se normaliza a nulo: para la AEAT es lo mismo. */
    public static String opcional(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return dentroDelMaximo(valor.strip(), maximo, campo);
    }

    private static String dentroDelMaximo(String valor, int maximo, String campo) {
        if (valor.length() > maximo) {
            throw new ValorInvalidoException(
                    campo + " admite como máximo " + maximo + " caracteres y tiene " + valor.length());
        }
        return valor;
    }
}
