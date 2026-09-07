package dev.lacre.sif.registro;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.Nif;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.shared.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonaFisicaJuridicaTest {

    @Test
    void unDestinatarioEspanolSeIdentificaConNif() {
        PersonaFisicaJuridica cliente = new PersonaFisicaJuridica("Cliente SL", new Nif("A28015865"));

        assertThat(cliente.identificador()).isInstanceOf(Nif.class);
    }

    @Test
    void unDestinatarioExtranjeroSeIdentificaConIdOtro() {
        IdOtro pasaporte = new IdOtro("FR", TipoIdentificacion.PASAPORTE, "12AB34567");

        PersonaFisicaJuridica cliente = new PersonaFisicaJuridica("Jean Dupont", pasaporte);

        assertThat(cliente.identificador()).isEqualTo(pasaporte);
    }

    @Test
    void nifEIdOtroSonLasDosUnicasFormasDeIdentificar() {
        assertThat(IdentificadorFiscal.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Nif.class, IdOtro.class);
    }

    @Test
    void exigeNombreEIdentificador() {
        assertThatThrownBy(() -> new PersonaFisicaJuridica(" ", new Nif("12345678Z")))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new PersonaFisicaJuridica("Cliente", null))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void recortaElNombreYRechazaMasDeCientoVeinteCaracteres() {
        assertThat(new PersonaFisicaJuridica("  Cliente SL  ", new Nif("12345678Z")).nombreRazon())
                .isEqualTo("Cliente SL");

        assertThatThrownBy(() -> new PersonaFisicaJuridica("X".repeat(121), new Nif("12345678Z")))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("120");
    }
}
