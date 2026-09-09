package dev.lacre.remision;

/**
 * La AEAT ha rechazado el envío <strong>entero</strong> y ha contestado con un SOAP Fault.
 * <p>
 * Es uno de los 44 códigos que {@code errores.properties} clasifica como «rechazo del envío
 * completo»: fallos de estructura, de cabecera o de autorización, que no llegan a mirar los
 * registros. Por eso no viene una {@code RespuestaSuministro} con una línea por registro, sino un
 * Fault: <strong>no hay nada que responder de cada línea porque no se ha procesado ninguna</strong>.
 * <p>
 * Hereda de {@link RemisionFallidaException} a propósito: ningún registro quedó presentado, así
 * que el outbox debe hacer exactamente lo mismo que ante un fallo de transporte —dejar el lote
 * pendiente y sumar un intento—. La diferencia es que aquí sí sabemos por qué, y el código lo
 * dice.
 * <p>
 * Ojo: <strong>reintentar puede no arreglarlo nunca</strong>. Un 4104 —NIF no identificado— se
 * repetirá igual mañana. Quien opere esto necesita ver el código, y para eso viaja en el mensaje.
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
