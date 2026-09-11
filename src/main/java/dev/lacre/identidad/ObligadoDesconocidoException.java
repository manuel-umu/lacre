package dev.lacre.identidad;

import dev.lacre.shared.Nif;

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

    /**
     * Por NIF: es como llega desde la API, donde el obligado se deduce del emisor de la factura
     * y no de un identificador interno que el ERP no tiene por qué conocer.
     */
    public ObligadoDesconocidoException(Nif nif) {
        super("No hay ningún obligado tributario con NIF " + nif.valor());
    }
}
