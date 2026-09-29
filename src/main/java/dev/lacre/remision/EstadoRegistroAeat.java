package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;

/** Desenlace de un registro concreto, {@code EstadoRegistroType} del XSD de respuesta. */
public enum EstadoRegistroAeat {
    CORRECTO("Correcto"),
    ACEPTADO_CON_ERRORES("AceptadoConErrores"),
    INCORRECTO("Incorrecto");

    private final String codigo;

    EstadoRegistroAeat(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static EstadoRegistroAeat desde(String codigo) {
        for (EstadoRegistroAeat estado : values()) {
            if (estado.codigo.equals(codigo)) {
                return estado;
            }
        }
        throw new ValorInvalidoException("Estado de registro desconocido: " + codigo);
    }
}
