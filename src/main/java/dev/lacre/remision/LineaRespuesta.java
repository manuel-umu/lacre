package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.registro.IdFactura;

/**
 * Lo que la AEAT responde sobre un registro concreto del lote.
 *
 * @param codigoError código del catálogo de la AEAT, nulo si no hubo error
 */
public record LineaRespuesta(IdFactura idFactura, EstadoRegistroAeat estado,
                             Integer codigoError, String descripcionError) {

    public LineaRespuesta {
        if (idFactura == null || estado == null) {
            throw new ValorInvalidoException("La respuesta identifica la factura y su estado");
        }
    }

    /**
     * Traduce la respuesta al estado del outbox. Es el único sitio donde se hace, y donde vive el
     * caso que no se ve venir: un rechazo con el código <strong>3000</strong> no es un rechazo,
     * es que el registro <em>ya estaba presentado</em>. Tratarlo como fallo llevaría a reintentar
     * indefinidamente algo que ya está hecho.
     */
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
