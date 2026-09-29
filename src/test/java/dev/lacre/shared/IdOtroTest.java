package dev.lacre.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdOtroTest {

    @Test
    void elCodigoDePaisSoloEsOpcionalConNifIva() {
        IdOtro sinPais = new IdOtro(null, TipoIdentificacion.NIF_IVA, "IE6388047V");

        assertThat(sinPais.codigoPais()).isNull();
        assertThatThrownBy(() -> new IdOtro(null, TipoIdentificacion.PASAPORTE, "12AB34567"))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1111"));
    }

    @Test
    void unNoCensadoEsDeEspanaConElNifDeUnaPersonaFisica() {
        assertThat(new IdOtro("ES", TipoIdentificacion.NO_CENSADO, "12345678Z").id())
                .isEqualTo("12345678Z");
        assertThatThrownBy(() -> new IdOtro("FR", TipoIdentificacion.NO_CENSADO, "12345678Z"))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1126"));
        assertThatThrownBy(() -> new IdOtro("ES", TipoIdentificacion.NO_CENSADO, "B12345674"))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1131"));
        assertThatThrownBy(() -> new IdOtro("ES", TipoIdentificacion.NO_CENSADO, "X123"))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1131"));
    }

    @Test
    void normalizaElCodigoDePaisYElIdentificador() {
        IdOtro id = new IdOtro(" fr ", TipoIdentificacion.PASAPORTE, "  12AB34567  ");

        assertThat(id.codigoPais()).isEqualTo("FR");
        assertThat(id.id()).isEqualTo("12AB34567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ES", "FR", "DE", "PT", "US", "AD", "ZW"})
    void aceptaLosCodigosIsoDelCatalogoDelXsd(String pais) {
        assertThat(new IdOtro(pais, TipoIdentificacion.NIF_IVA, "X1").codigoPais())
                .isEqualTo(pais);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "ZZ", "ESP", "E", "12"})
    void rechazaCodigosDePaisFueraDelCatalogo(String pais) {
        assertThatThrownBy(() -> new IdOtro(pais, TipoIdentificacion.NIF_IVA, "X1"))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("país");
    }

    @Test
    void exigeTipoEIdentificador() {
        assertThatThrownBy(() -> new IdOtro("FR", null, "X1")).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new IdOtro("FR", TipoIdentificacion.PASAPORTE, "  "))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void rechazaIdentificadoresDeMasDeVeinteCaracteres() {
        assertThat(new IdOtro("FR", TipoIdentificacion.PASAPORTE, "X".repeat(20)).id())
                .hasSize(20);

        assertThatThrownBy(() -> new IdOtro("FR", TipoIdentificacion.PASAPORTE, "X".repeat(21)))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("20");
    }

    @Test
    void elCatalogoDeTiposEmpiezaEnElDosPorqueElUnoNoAplica() {
        assertThat(TipoIdentificacion.values())
                .extracting(TipoIdentificacion::codigo)
                .containsExactly("02", "03", "04", "05", "06", "07");
    }
}
