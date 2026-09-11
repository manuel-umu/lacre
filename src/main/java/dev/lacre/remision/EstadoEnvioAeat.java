package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;

/**
 * Desenlace del envío en conjunto, {@code EstadoEnvioType} del XSD de respuesta. Es del lote,
 * no de cada registro.
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
