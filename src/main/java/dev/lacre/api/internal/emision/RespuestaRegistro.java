package dev.lacre.api.internal.emision;

import java.util.UUID;

/**
 * Respuesta a un alta o anulación aceptadas: identificador, posición y huella del registro. No
 * implica que la AEAT lo haya aceptado; el desenlace se consulta en {@code GET /v1/registros/{id}}.
 */
public record RespuestaRegistro(UUID registroId, long posicion, String huella) {
}
