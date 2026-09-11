package dev.lacre.verifactu.desglose;

import dev.lacre.shared.Importe;
import dev.lacre.shared.ValorInvalidoException;

import java.util.List;
import java.util.Objects;

/** Desglose de impuestos de un registro de alta, {@code DesgloseType} del XSD: de 1 a 12 líneas. */
public record Desglose(List<DetalleDesglose> detalles) {

    public static final int MAXIMO_DETALLES = 12;

    public Desglose {
        if (detalles == null || detalles.isEmpty()) {
            throw new ValorInvalidoException("El desglose debe tener al menos una línea de detalle");
        }
        if (detalles.size() > MAXIMO_DETALLES) {
            throw new ValorInvalidoException(
                    "El desglose admite como máximo " + MAXIMO_DETALLES + " líneas de detalle, y tiene "
                            + detalles.size());
        }
        // List.of lanza NullPointerException en contains(null).
        if (detalles.stream().anyMatch(Objects::isNull)) {
            throw new ValorInvalidoException("El desglose no admite líneas de detalle nulas");
        }
        detalles = List.copyOf(detalles);
    }

    public static Desglose de(DetalleDesglose... detalles) {
        return new Desglose(List.of(detalles));
    }

    /** Suma de bases imponibles o importes no sujetos de todas las líneas. */
    public Importe totalBases() {
        return detalles.stream()
                .map(DetalleDesglose::baseImponibleOimporteNoSujeto)
                .reduce(Importe.CERO, Importe::sumar);
    }

    /**
     * Suma de cuotas repercutidas y recargos de equivalencia de todas las líneas.
     * Es el valor con el que la AEAT contrasta {@code CuotaTotal}.
     */
    public Importe totalCuotas() {
        return detalles.stream()
                .map(DetalleDesglose::cuotas)
                .reduce(Importe.CERO, Importe::sumar);
    }

    /**
     * Bases más cuotas: el valor con el que la AEAT contrasta {@code ImporteTotal}.
     */
    public Importe totalConImpuestos() {
        return totalBases().sumar(totalCuotas());
    }
}
