package dev.lacre.api.internal.consulta;

import java.util.UUID;

/** Se ha consultado un registro que no existe. */
public class RegistroDesconocidoException extends RuntimeException {

    public RegistroDesconocidoException(UUID id) {
        super("No hay ningún registro de facturación con identificador " + id);
    }
}
