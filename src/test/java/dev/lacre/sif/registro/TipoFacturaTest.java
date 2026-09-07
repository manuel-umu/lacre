package dev.lacre.sif.registro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blinda el catálogo L2 contra renombrados accidentales: el nombre de cada constante es el
 * código que viaja en el XML y en la cadena de la huella, no un identificador interno.
 */
class TipoFacturaTest {

    @Test
    void elCatalogoEsExactamenteElDeLaListaL2() {
        assertThat(TipoFactura.values())
                .extracting(TipoFactura::codigo)
                .containsExactlyInAnyOrder("F1", "F2", "F3", "R1", "R2", "R3", "R4", "R5");
    }

    @Test
    void elCodigoCoincideConElNombreDeLaConstante() {
        for (TipoFactura tipo : TipoFactura.values()) {
            assertThat(tipo.codigo()).isEqualTo(tipo.name());
        }
    }
}
