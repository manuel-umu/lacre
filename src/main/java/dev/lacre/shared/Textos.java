package dev.lacre.shared;

/** Validación y recorte de los campos de texto con longitud máxima. */
public final class Textos {

    private Textos() {}

    /** Exige un texto no vacío que no supere el máximo, y lo devuelve recortado. */
    public static String obligatorio(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValorInvalidoException(campo + " es obligatorio");
        }
        return dentroDelMaximo(valor.strip(), maximo, campo);
    }

    /** Admite nulo; un texto en blanco se normaliza a nulo. */
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
