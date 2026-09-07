package dev.lacre.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImporteTest {

    @Test
    void fijaLaEscalaADosDecimales() {
        assertThat(Importe.de("10").valor()).isEqualTo(new BigDecimal("10.00"));
        assertThat(Importe.de("10.5").valor()).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void redondeaHalfUpAlejandoseDelCero() {
        assertThat(Importe.de("10.005")).isEqualTo(Importe.de("10.01"));
        assertThat(Importe.de("10.004")).isEqualTo(Importe.de("10.00"));
        assertThat(Importe.de("-0.005")).isEqualTo(Importe.de("-0.01"));
    }

    @Test
    void laEscalaFijaHaceConsistenteElEquals() {
        assertThat(Importe.de("10")).isEqualTo(Importe.de("10.00"));
        assertThat(Importe.de("10")).hasSameHashCodeAs(Importe.de("10.000"));
    }

    @Test
    void sumaYResta() {
        assertThat(Importe.de("10.50").sumar(Importe.de("0.75"))).isEqualTo(Importe.de("11.25"));
        assertThat(Importe.de("10.50").restar(Importe.de("11.00"))).isEqualTo(Importe.de("-0.50"));
        assertThat(Importe.de("10.50").negado()).isEqualTo(Importe.de("-10.50"));
    }

    @Test
    void admiteNegativosPorqueLasRectificativasLosNecesitan() {
        assertThat(Importe.de("-100.00").valor().signum()).isNegative();
    }

    @Test
    void ceroYComparacion() {
        assertThat(Importe.CERO.esCero()).isTrue();
        assertThat(Importe.de("0.001").esCero()).isTrue();
        assertThat(Importe.de("-5.00")).isLessThan(Importe.de("5.00"));
    }

    @Test
    void aceptaHastaDoceDigitosEnterosYRechazaElTrece() {
        assertThat(Importe.de("999999999999.99").valor().toPlainString()).isEqualTo("999999999999.99");
        assertThat(Importe.de("-999999999999.99").valor().toPlainString()).isEqualTo("-999999999999.99");

        assertThatThrownBy(() -> Importe.de("1000000000000.00"))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("12 dígitos enteros");
        assertThatThrownBy(() -> Importe.de("-1000000000000.00"))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void elRedondeoNoPuedeColarUnImporteFueraDeLimite() {
        assertThatThrownBy(() -> Importe.de("999999999999.995"))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void rechazaNuloYNoNumerico() {
        assertThatThrownBy(() -> Importe.de((String) null)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Importe.de("no soy un número")).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new Importe(null)).isInstanceOf(ValorInvalidoException.class);
    }
}
