package dev.lacre.verifactu.desglose;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Línea del desglose de impuestos, {@code DetalleType} del XSD. Solo {@link #calificacion()} y
 * {@link #baseImponibleOimporteNoSujeto()} son obligatorios; el resto admite nulo.
 */
public record DetalleDesglose(
        Impuesto impuesto,
        ClaveRegimen claveRegimen,
        Calificacion calificacion,
        Porcentaje tipoImpositivo,
        Importe baseImponibleOimporteNoSujeto,
        Importe baseImponibleACoste,
        Importe cuotaRepercutida,
        Porcentaje tipoRecargoEquivalencia,
        Importe cuotaRecargoEquivalencia) {

    public DetalleDesglose {
        if (calificacion == null) {
            throw new ValorInvalidoException(
                    "Toda línea de desglose debe estar calificada o exenta");
        }
        if (baseImponibleOimporteNoSujeto == null) {
            throw new ValorInvalidoException(
                    "La base imponible o importe no sujeto es obligatoria en cada línea de desglose");
        }
    }

    /** Cuota repercutida más recargo de equivalencia, tratando la ausencia como cero. */
    public Importe cuotas() {
        return oCero(cuotaRepercutida).sumar(oCero(cuotaRecargoEquivalencia));
    }

    private static Importe oCero(Importe importe) {
        return importe == null ? Importe.CERO : importe;
    }
}
