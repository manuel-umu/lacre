package dev.lacre.remision;

/**
 * La AEAT ha rechazado el envío entero con un SOAP Fault, sin procesar ningún registro. Hereda
 * de {@link RemisionFallidaException}: el lote queda pendiente y suma un intento.
 */
public class EnvioRechazadoException extends RemisionFallidaException {

    private final Integer codigo;

    public EnvioRechazadoException(Integer codigo, String descripcion) {
        super("la AEAT rechazó el envío completo"
                + (codigo == null ? "" : " con el código " + codigo) + ": " + descripcion);
        this.codigo = codigo;
    }

    /** Código del catálogo de la AEAT, o {@code null} si el Fault no lo traía. */
    public Integer codigo() {
        return codigo;
    }
}
