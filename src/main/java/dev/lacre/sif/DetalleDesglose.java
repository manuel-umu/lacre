package dev.lacre.sif;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Una línea del desglose de impuestos, {@code DetalleType} del XSD.
 * <p>
 * Solo {@link #calificacion()} y {@link #baseImponibleOimporteNoSujeto()} son obligatorios;
 * el resto son {@code minOccurs="0"} en el esquema y aquí pueden ser nulos. Se ha preferido
 * admitir nulos a envolver seis componentes en {@code Optional}: un record de nueve campos con
 * seis {@code Optional} es peor de construir y de leer, y la ausencia se traduce
 * directamente a «el elemento no se emite» al serializar.
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
