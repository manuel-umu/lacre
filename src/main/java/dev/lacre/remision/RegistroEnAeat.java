package dev.lacre.remision;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.registro.IdFactura;
import java.time.OffsetDateTime;

/**
 * Un registro tal como lo devuelve la consulta de la AEAT: el vigente de la factura.
 *
 * @param huella nula si la AEAT no la devuelve
 * @param presentado nulo si la AEAT no devuelve {@code DatosPresentacion}
 */
public record RegistroEnAeat(
        IdFactura idFactura,
        Huella huella,
        Estado estado,
        Integer codigoError,
        String descripcionError,
        OffsetDateTime presentado) {

    /** {@code EstadoRegistroType} de {@code RespuestaConsultaLR.xsd}. */
    public enum Estado {
        CORRECTO,
        ACEPTADO_CON_ERRORES,
        ANULADO
    }
}
