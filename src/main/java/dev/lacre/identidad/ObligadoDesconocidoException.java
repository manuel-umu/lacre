package dev.lacre.identidad;

import dev.lacre.shared.Nif;

import java.util.UUID;

/** Se ha pedido operar por cuenta de un obligado que no está dado de alta. */
public class ObligadoDesconocidoException extends RuntimeException {

    public ObligadoDesconocidoException(UUID id) {
        super("No hay ningún obligado tributario con identificador " + id);
    }

    /** Por NIF, tal y como llega desde la API. */
    public ObligadoDesconocidoException(Nif nif) {
        super("No hay ningún obligado tributario con NIF " + nif.valor());
    }
}
