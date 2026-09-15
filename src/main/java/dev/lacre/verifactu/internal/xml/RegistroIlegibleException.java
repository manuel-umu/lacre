package dev.lacre.verifactu.internal.xml;

/** El XML guardado de un registro no se puede leer, o le falta algo que entra en su huella. */
public class RegistroIlegibleException extends RuntimeException {

    public RegistroIlegibleException(String motivo) {
        super("Registro ilegible: " + motivo);
    }

    public RegistroIlegibleException(String motivo, Throwable causa) {
        super("Registro ilegible: " + motivo, causa);
    }
}
