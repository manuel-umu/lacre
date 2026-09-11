package dev.lacre.api.internal.emision;

import java.util.UUID;

/**
 * Lo que el ERP recibe de un alta o de una anulación aceptadas. La misma forma para las dos: lo
 * que cambia entre ellas es el contenido del registro, no lo que hay que devolver.
 * <p>
 * La {@code huella} es el dato con valor para quien integra: es la que debe figurar en la
 * factura y la que encadena con la siguiente. El {@code registroId} sirve para consultar
 * después en qué acabó la remisión a la AEAT, que es asíncrona.
 * <p>
 * <strong>No dice que la AEAT lo haya aceptado</strong>, porque en este momento no lo sabe
 * nadie: lo que garantiza un 201 es que el registro está en la cadena y en el outbox, que es
 * lo que la norma exige al expedir. El desenlace llega después, y se consulta con
 * {@code GET /v1/registros/{id}}.
 */
public record RespuestaRegistro(UUID registroId, long posicion, String huella) {
}
