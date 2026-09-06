package dev.lacre.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PorcentajeTest {

    @Test
    void aplicaElTipoRedondeandoADosDecimales() {
        assertThat(Porcentaje.de("21").aplicarA(Importe.de("100.00"))).isEqualTo(Importe.de("21.00"));
        assertThat(Porcentaje.de("21").aplicarA(Importe.de("10.99"))).isEqualTo(Importe.de("2.31"));
        assertThat(Porcentaje.de("21").aplicarA(Importe.de("0.07"))).isEqualTo(Importe.de("0.01"));
    }

    @Test
    void admiteLosTiposDeRecargoConDosDecimales() {
        assertThat(Porcentaje.de("5.2").valor().toPlainString()).isEqualTo("5.20");
        assertThat(Porcentaje.de("0.62").aplicarA(Importe.de("1000.00"))).isEqualTo(Importe.de("6.20"));
    }

    @Test
    void aceptaLosExtremosDelRango() {
        assertThat(Porcentaje.CERO.aplicarA(Importe.de("999.99"))).isEqualTo(Importe.CERO);
        assertThat(Porcentaje.de("100").aplicarA(Importe.de("42.00"))).isEqualTo(Importe.de("42.00"));
    }

    @Test
    void rechazaFueraDeRango() {
        assertThatThrownBy(() -> Porcentaje.de("-0.01")).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Porcentaje.de("100.01")).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void rechazaNuloYNoNumerico() {
        assertThatThrownBy(() -> Porcentaje.de(null)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Porcentaje.de("21%")).isInstanceOf(ValorInvalidoException.class);
    }
}
