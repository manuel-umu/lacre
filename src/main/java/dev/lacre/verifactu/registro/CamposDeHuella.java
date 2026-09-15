package dev.lacre.verifactu.registro;

import dev.lacre.shared.Importe;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Los campos de un registro que entran en su huella, según el art. 13 de la OM HAC/1177/2024. Lo
 * demás del registro no la afecta. La huella anterior y la fecha de generación se aportan aparte.
 */
public sealed interface CamposDeHuella {

    IdFactura idFactura();

    /** Alta: identificación, tipo de factura, cuota total e importe total. */
    record Alta(IdFactura idFactura, TipoFactura tipoFactura, Importe cuotaTotal,
                Importe importeTotal) implements CamposDeHuella {

        public Alta {
            if (idFactura == null || tipoFactura == null || cuotaTotal == null
                    || importeTotal == null) {
                throw new ValorInvalidoException("La huella de un alta necesita identificación, "
                        + "tipo de factura, cuota total e importe total");
            }
        }
    }

    /** Anulación: solo la identificación de la factura anulada. */
    record Anulacion(IdFactura idFactura) implements CamposDeHuella {

        public Anulacion {
            if (idFactura == null) {
                throw new ValorInvalidoException(
                        "La huella de una anulación necesita la identificación de la factura");
            }
        }
    }
}
