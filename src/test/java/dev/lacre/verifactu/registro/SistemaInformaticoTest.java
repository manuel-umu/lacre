package dev.lacre.verifactu.registro;

import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SistemaInformaticoTest {

    private static final PersonaFisicaJuridica PRODUCTOR =
            new PersonaFisicaJuridica("lacre", new Nif("12345678Z"));

    @Test
    void lacreSeDeclaraSoloVerifactuYDeUnSoloObligado() {
        SistemaInformatico sistema = Registros.sistemaInformatico();

        assertThat(sistema.tipoUsoPosibleSoloVerifactu()).isTrue();
        assertThat(sistema.tipoUsoPosibleMultiOT()).isFalse();
        assertThat(sistema.indicadorMultiplesOT()).isFalse();
    }

    /**
     * El XSD no puede expresar esta relación, pero es contradictoria: declarar que se está
     * dando servicio a varios obligados con un sistema que no admite varios obligados.
     */
    @Test
    void noPuedeServirAVariosObligadosSiNoLosAdmite() {
        assertThatThrownBy(() -> new SistemaInformatico(PRODUCTOR, "lacre", "01", "0.0.1", "0001",
                true, false, true))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("varios obligados");
    }

    @Test
    void unSistemaMultiobligadoSiPuedeDeclararQueLoEsta() {
        SistemaInformatico sistema = new SistemaInformatico(PRODUCTOR, "otro", "02", "1.0", "0002",
                false, true, true);

        assertThat(sistema.indicadorMultiplesOT()).isTrue();
    }

    @Test
    void respetaLosLimitesDeLongitudDelXsd() {
        assertThatThrownBy(() -> new SistemaInformatico(PRODUCTOR, "X".repeat(31), "01", "1.0", "0001",
                true, false, false)).isInstanceOf(ValorInvalidoException.class).hasMessageContaining("30");
        assertThatThrownBy(() -> new SistemaInformatico(PRODUCTOR, "lacre", "123", "1.0", "0001",
                true, false, false)).isInstanceOf(ValorInvalidoException.class).hasMessageContaining("2");
        assertThatThrownBy(() -> new SistemaInformatico(PRODUCTOR, "lacre", "01", "X".repeat(51), "0001",
                true, false, false)).isInstanceOf(ValorInvalidoException.class).hasMessageContaining("50");
        assertThatThrownBy(() -> new SistemaInformatico(PRODUCTOR, "lacre", "01", "1.0", "X".repeat(101),
                true, false, false)).isInstanceOf(ValorInvalidoException.class).hasMessageContaining("100");
    }

    @Test
    void exigeProductorYCamposDeTexto() {
        assertThatThrownBy(() -> new SistemaInformatico(null, "lacre", "01", "1.0", "0001",
                true, false, false)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new SistemaInformatico(PRODUCTOR, "  ", "01", "1.0", "0001",
                true, false, false)).isInstanceOf(ValorInvalidoException.class);
    }
}
