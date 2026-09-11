package dev.lacre.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * NIF español (DNI, NIE o CIF) validado con su carácter de control y normalizado a mayúsculas
 * sin espacios.
 */
public record Nif(String valor) implements IdentificadorFiscal {

    private static final Pattern DNI = Pattern.compile("[0-9]{8}[A-Z]");
    private static final Pattern NIE = Pattern.compile("[XYZ][0-9]{7}[A-Z]");
    private static final Pattern CIF = Pattern.compile("[ABCDEFGHJKLMNPQRSUVW][0-9]{7}[0-9A-J]");

    private static final String LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String LETRAS_CIF = "JABCDEFGHI";
    private static final String ORGANIZACIONES_CON_CONTROL_ALFABETICO = "PQRSNW";
    private static final String ORGANIZACIONES_CON_CONTROL_NUMERICO = "ABEH";

    public Nif {
        if (valor == null) {
            throw new NifInvalidoException("El NIF no puede ser nulo");
        }
        valor = valor.trim().toUpperCase(Locale.ROOT);
        if (!esValido(valor)) {
            throw new NifInvalidoException("NIF inválido: " + valor);
        }
    }

    private static boolean esValido(String candidato) {
        if (DNI.matcher(candidato).matches()) {
            return controlModulo23(candidato.substring(0, 8), candidato.charAt(8));
        }
        if (NIE.matcher(candidato).matches()) {
            String prefijoNumerico = String.valueOf("XYZ".indexOf(candidato.charAt(0)));
            return controlModulo23(prefijoNumerico + candidato.substring(1, 8), candidato.charAt(8));
        }
        if (CIF.matcher(candidato).matches()) {
            return controlCif(candidato);
        }
        return false;
    }

    private static boolean controlModulo23(String ochoDigitos, char control) {
        return LETRAS_DNI.charAt(Integer.parseInt(ochoDigitos) % 23) == control;
    }

    private static boolean controlCif(String candidato) {
        int suma = 0;
        for (int posicion = 1; posicion <= 7; posicion++) {
            int digito = candidato.charAt(posicion) - '0';
            if (posicion % 2 == 0) {
                suma += digito;
            } else {
                int doble = digito * 2;
                suma += doble / 10 + doble % 10;
            }
        }
        int digitoControl = (10 - suma % 10) % 10;
        char control = candidato.charAt(8);
        char organizacion = candidato.charAt(0);

        if (ORGANIZACIONES_CON_CONTROL_ALFABETICO.indexOf(organizacion) >= 0) {
            return control == LETRAS_CIF.charAt(digitoControl);
        }
        if (ORGANIZACIONES_CON_CONTROL_NUMERICO.indexOf(organizacion) >= 0) {
            return control == (char) ('0' + digitoControl);
        }
        return control == (char) ('0' + digitoControl) || control == LETRAS_CIF.charAt(digitoControl);
    }
}
