package dev.lacre.shared;

/**
 * Los datos incumplen una validación de negocio por la que la AEAT rechazaría el registro.
 * {@link #codigoAeat()} es el código de esa validación en el catálogo de errores de la AEAT.
 */
public class ReglaAeatIncumplidaException extends ValorInvalidoException {

    private final String codigoAeat;

    public ReglaAeatIncumplidaException(String codigoAeat, String mensaje) {
        super(mensaje);
        this.codigoAeat = codigoAeat;
    }

    public String codigoAeat() {
        return codigoAeat;
    }

    /** Lanza la excepción con ese código y ese mensaje si la regla no se cumple. */
    public static void exigir(boolean cumple, String codigoAeat, String mensaje) {
        if (!cumple) {
            throw new ReglaAeatIncumplidaException(codigoAeat, mensaje);
        }
    }
}
