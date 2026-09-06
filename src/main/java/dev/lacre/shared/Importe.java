package dev.lacre.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cantidad monetaria con escala fija de 2 decimales y redondeo {@code HALF_UP}.
 * <p>
 * La escala se fija en el constructor, de modo que el {@code equals} generado del
 * record —que en {@link BigDecimal} es sensible a la escala— sea consistente:
 * {@code 10} y {@code 10.00} son el mismo importe. Se admiten valores negativos
 * porque las facturas rectificativas los necesitan.
 */
public record Importe(BigDecimal valor) implements Comparable<Importe> {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    public static final Importe CERO = new Importe(BigDecimal.ZERO);

    public Importe {
        if (valor == null) {
            throw new ValorInvalidoException("El importe no puede ser nulo");
        }
        valor = valor.setScale(ESCALA, REDONDEO);
    }

    public static Importe de(String valor) {
        if (valor == null) {
            throw new ValorInvalidoException("El importe no puede ser nulo");
        }
        try {
            return new Importe(new BigDecimal(valor));
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ValorInvalidoException("Importe no válido: " + valor);
        }
    }

    public static Importe de(long valor) {
        return new Importe(BigDecimal.valueOf(valor));
    }

    public Importe sumar(Importe otro) {
        return new Importe(valor.add(otro.valor));
    }

    public Importe restar(Importe otro) {
        return new Importe(valor.subtract(otro.valor));
    }

    public Importe negado() {
        return new Importe(valor.negate());
    }

    public boolean esCero() {
        return valor.signum() == 0;
    }

    @Override
    public int compareTo(Importe otro) {
        return valor.compareTo(otro.valor);
    }
}
