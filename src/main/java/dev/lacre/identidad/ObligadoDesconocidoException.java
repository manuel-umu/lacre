package dev.lacre.identidad;

import java.util.UUID;

/**
 * Se ha pedido facturar por cuenta de un obligado que no está dado de alta.
 * <p>
 * Antes esto reventaba con una violación de clave ajena al insertar el registro, un error de
 * base de datos que no dice nada a quien integra la API.
 */
public class ObligadoDesconocidoException extends RuntimeException {

    public ObligadoDesconocidoException(UUID id) {
        super("No hay ningún obligado tributario con identificador " + id);
    }
}
