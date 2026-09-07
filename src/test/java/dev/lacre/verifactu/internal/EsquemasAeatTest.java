package dev.lacre.verifactu.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.validation.Schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Que estos tests pasen demuestra que la validación contra los esquemas de la AEAT no necesita
 * red: el resolutor lanza ante cualquier recurso que no esté en el classpath, así que si algo
 * intentara salir a internet, esto se pondría rojo en vez de depender de que w3.org responda.
 */
class EsquemasAeatTest {

    @Test
    void compilaElEsquemaDeAltaYAnulacionSinSalirAInternet() {
        Schema esquema = EsquemasAeat.suministroLr();

        assertThat(esquema).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SuministroLR.xsd", "SuministroInformacion.xsd", "RespuestaSuministro.xsd",
            "ConsultaLR.xsd", "RespuestaConsultaLR.xsd", "EventosSIF.xsd"})
    void todosLosEsquemasDeLaAeatCompilanEnLocal(String fichero) {
        assertThat(EsquemasAeat.compilar(fichero)).isNotNull();
    }

    @Test
    void elEsquemaDeFirmaDeLaW3cEstaVendorizado() {
        assertThat(EsquemasAeat.class.getResource("/xsd/w3c/xmldsig-core-schema.xsd")).isNotNull();
    }

    @Test
    void seNiegaACompilarUnEsquemaDelQueNoHayCopiaLocal() {
        assertThatThrownBy(() -> EsquemasAeat.compilar("InventadoPorAlguien.xsd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay copia local");
    }
}
