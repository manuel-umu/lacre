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

    /** Rechaza los caracteres que el XML 1.0 no admite: controles salvo tab y saltos, sustitutos sueltos y U+FFFE/U+FFFF. */
    public static void exigirAdmitidoEnXml(String valor, String campo) {
        valor.codePoints()
                .filter(caracter -> !admitidoEnXml(caracter))
                .findFirst()
                .ifPresent(caracter -> {
                    throw new ValorInvalidoException("%s contiene un carácter que no admite el XML de la AEAT: U+%04X"
                            .formatted(campo, caracter));
                });
    }

    private static boolean admitidoEnXml(int caracter) {
        return caracter == 0x9
                || caracter == 0xA
                || caracter == 0xD
                || (caracter >= 0x20 && caracter <= 0xD7FF)
                || (caracter >= 0xE000 && caracter <= 0xFFFD)
                || caracter >= 0x10000;
    }

    private static String dentroDelMaximo(String valor, int maximo, String campo) {
        exigirAdmitidoEnXml(valor, campo);
        if (valor.length() > maximo) {
            throw new ValorInvalidoException(
                    campo + " admite como máximo " + maximo + " caracteres y tiene " + valor.length());
        }
        return valor;
    }
}
