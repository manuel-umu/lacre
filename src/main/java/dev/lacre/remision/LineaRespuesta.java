package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;

/**
 * Respuesta de la AEAT sobre un registro concreto del lote, identificado por factura y tipo de
 * operación.
 *
 * @param tipo        del bloque {@code Operacion/TipoOperacion} de la respuesta
 * @param codigoError código del catálogo de la AEAT, nulo si no hubo error
 */
public record LineaRespuesta(IdFactura idFactura, TipoRegistro tipo, EstadoRegistroAeat estado,
                             Integer codigoError, String descripcionError) {

    public LineaRespuesta {
        if (idFactura == null || estado == null || tipo == null) {
            throw new ValorInvalidoException(
                    "La respuesta identifica la factura, el tipo de operación y su estado");
        }
    }

    /** Traduce la respuesta al estado del outbox. Un rechazo con código 3000 es un duplicado. */
    public EstadoEnvio desenlace() {
        return switch (estado) {
            case CORRECTO -> EstadoEnvio.ACEPTADO;
            case ACEPTADO_CON_ERRORES -> EstadoEnvio.ACEPTADO_CON_ERRORES;
            case INCORRECTO -> esDuplicado() ? EstadoEnvio.DUPLICADO : EstadoEnvio.RECHAZADO;
        };
    }

    public boolean esDuplicado() {
        return codigoError != null && codigoError == EstadoEnvio.CODIGO_REGISTRO_DUPLICADO;
    }
}
