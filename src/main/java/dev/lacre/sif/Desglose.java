package dev.lacre.sif;

import dev.lacre.shared.Importe;
import dev.lacre.shared.ValorInvalidoException;

import java.util.List;
import java.util.Objects;

/**
 * Desglose de impuestos de un registro de alta, {@code DesgloseType} del XSD.
 * <p>
 * El esquema admite entre 1 y 12 líneas de detalle. El límite superior no es un número
 * redondo elegido por nosotros: viene de {@code maxOccurs="12"}.
 */
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
        // Con stream y no con contains(null): las listas inmutables de List.of lanzan
        // NullPointerException al preguntarles por null.
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
