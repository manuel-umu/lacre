package dev.lacre.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cantidad monetaria con escala fija de 2 decimales y redondeo {@code HALF_UP}. Admite valores
 * negativos.
 */
public record Importe(BigDecimal valor) implements Comparable<Importe> {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Límite de dígitos enteros de {@code ImporteSgn12.2Type}. */
    public static final int MAXIMO_DIGITOS_ENTEROS = 12;

    private static final BigDecimal LIMITE = BigDecimal.TEN.pow(MAXIMO_DIGITOS_ENTEROS);

    // Debe declararse después de LIMITE: el constructor canónico lo usa.
    public static final Importe CERO = new Importe(BigDecimal.ZERO);

    public Importe {
        if (valor == null) {
            throw new ValorInvalidoException("El importe no puede ser nulo");
        }
        valor = valor.setScale(ESCALA, REDONDEO);
        if (valor.abs().compareTo(LIMITE) >= 0) {
            throw new ValorInvalidoException("El importe excede los " + MAXIMO_DIGITOS_ENTEROS
                    + " dígitos enteros que admite la AEAT: " + valor.toPlainString());
        }
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
