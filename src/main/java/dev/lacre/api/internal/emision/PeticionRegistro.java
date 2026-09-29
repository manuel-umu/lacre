package dev.lacre.api.internal.emision;

import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.SistemaInformatico;

/**
 * Petición de emisión: alta de un registro o anulación de una factura. Cada
 * implementación aporta su mapeo al dominio y su discriminante de idempotencia.
 */
public interface PeticionRegistro {

    /** La factura que la petición identifica: la que se expide, o la que se anula. */
    IdFacturaDto factura();

    /** Traduce el contrato al modelo fiscal. El sistema informático lo aporta la configuración. */
    DatosRegistro aDatos(SistemaInformatico sistemaInformatico);

    /** Distingue esta petición de otra sobre la misma factura en la huella de idempotencia. */
    String discriminante();

    /** Un indicador omitido vale «N». */
    static boolean si(Boolean indicador) {
        return Boolean.TRUE.equals(indicador);
    }
}
