package dev.lacre.api.internal;

import java.util.UUID;

/**
 * Se ha consultado un registro que no existe.
 * <p>
 * Aquí sí es 404, al contrario que el obligado no censado: lo que identifica el recurso es la
 * ruta, y no hay ningún recurso en esa ruta.
 */
class RegistroDesconocidoException extends RuntimeException {

    RegistroDesconocidoException(UUID id) {
        super("No hay ningún registro de facturación con identificador " + id);
    }
}
