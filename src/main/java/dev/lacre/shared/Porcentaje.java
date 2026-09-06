package dev.lacre.shared;

import java.math.BigDecimal;

/**
 * Tipo impositivo o de recargo, entre 0 y 100, con dos decimales.
 * <p>
 * {@link #aplicarA(Importe)} es el único sitio donde se multiplica un importe por un
 * porcentaje, para que el redondeo fiscal no se improvise en cada cálculo.
 */
public record Porcentaje(BigDecimal valor) {

    private static final BigDecimal CIEN = new BigDecimal("100");
    public static final Porcentaje CERO = new Porcentaje(BigDecimal.ZERO);

    public Porcentaje {
        if (valor == null) {
            throw new ValorInvalidoException("El porcentaje no puede ser nulo");
        }
        valor = valor.setScale(Importe.ESCALA, Importe.REDONDEO);
        if (valor.signum() < 0 || valor.compareTo(CIEN) > 0) {
            throw new ValorInvalidoException("Porcentaje fuera del rango [0, 100]: " + valor);
        }
    }

    public static Porcentaje de(String valor) {
        if (valor == null) {
            throw new ValorInvalidoException("El porcentaje no puede ser nulo");
        }
        try {
            return new Porcentaje(new BigDecimal(valor));
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ValorInvalidoException("Porcentaje no válido: " + valor);
        }
    }

    public Importe aplicarA(Importe base) {
        return new Importe(base.valor().multiply(valor).divide(CIEN, Importe.ESCALA, Importe.REDONDEO));
    }
}
