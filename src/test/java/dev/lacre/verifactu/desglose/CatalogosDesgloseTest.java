package dev.lacre.verifactu.desglose;

import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.registro.TipoFactura;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fija los catálogos del desglose: el nombre de cada constante es formato de intercambio. */
class CatalogosDesgloseTest {

    @Test
    void elCatalogoDeImpuestosEsElDeLaListaL1() {
        assertThat(Impuesto.values())
                .extracting(Impuesto::codigo)
                .containsExactly("01", "02", "03", "05");
    }

    @Test
    void elCatalogoDeCalificacionEsElDeLaListaL9() {
        assertThat(CalificacionOperacion.values())
                .extracting(CalificacionOperacion::codigo)
                .containsExactlyInAnyOrder("S1", "S2", "N1", "N2");
    }

    @Test
    void elCatalogoDeExencionesVaDeE1AE8() {
        assertThat(OperacionExenta.values())
                .extracting(OperacionExenta::codigo)
                .containsExactlyInAnyOrder("E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8");
    }

    @Test
    void calificacionYExencionSonLasDosUnicasCalificacionesPosibles() {
        assertThat(Calificacion.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(CalificacionOperacion.class, OperacionExenta.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "14", "15", "17", "18", "19", "20", "21"})
    void aceptaLasDieciochoClavesDeRegimenDelXsd(String codigo) {
        assertThat(new ClaveRegimen(codigo).codigo()).isEqualTo(codigo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "13", "16", "00", "22", "1", "IVA", ""})
    void rechazaClavesDeRegimenQueNoEstanEnElCatalogo(String codigo) {
        assertThatThrownBy(() -> new ClaveRegimen(codigo))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void rechazaLaClaveDeRegimenNula() {
        assertThatThrownBy(() -> new ClaveRegimen(null)).isInstanceOf(ValorInvalidoException.class);
    }
}
