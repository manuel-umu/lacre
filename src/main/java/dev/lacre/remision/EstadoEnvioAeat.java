package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;

/**
 * Desenlace del envío en conjunto, {@code EstadoEnvioType} del XSD de respuesta.
 * <p>
 * Es del lote, no de cada registro: con la cabecera correcta, basta que un registro se rechace
 * para que el envío entero sea {@link #PARCIALMENTE_CORRECTO}. Quien decide el destino de cada
 * fila del outbox es su {@code RespuestaLinea}, no esto.
 * <p>
 * Los nombres no coinciden con los del XML —{@code ParcialmenteCorrecto} no es un identificador
 * Java al uso—, así que aquí sí hace falta un código explícito, al contrario que en
 * {@code TipoFactura}, donde el nombre de la constante ES el formato de intercambio.
 */
public enum EstadoEnvioAeat {

    CORRECTO("Correcto"),
    PARCIALMENTE_CORRECTO("ParcialmenteCorrecto"),
    INCORRECTO("Incorrecto");

    private final String codigo;

    EstadoEnvioAeat(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static EstadoEnvioAeat desde(String codigo) {
        for (EstadoEnvioAeat estado : values()) {
            if (estado.codigo.equals(codigo)) {
                return estado;
            }
        }
        throw new ValorInvalidoException("Estado de envío desconocido: " + codigo);
    }
}
