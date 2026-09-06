package dev.lacre.shared;

/**
 * Un value object del dominio ha recibido un valor que rompe sus invariantes.
 */
public class ValorInvalidoException extends RuntimeException {

    public ValorInvalidoException(String mensaje) {
        super(mensaje);
    }
}
