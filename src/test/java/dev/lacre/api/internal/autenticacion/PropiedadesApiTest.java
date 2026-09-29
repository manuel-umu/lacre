package dev.lacre.api.internal.autenticacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Validación de la clave de la API, sin Spring. */
class PropiedadesApiTest {

    @Test
    void sinClaveNoSeArranca() {
        assertThatThrownBy(() -> new PropiedadesApi(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LACRE_API_CLAVE");
    }

    @Test
    void unaClaveEnBlancoEsComoNoTenerla() {
        assertThatThrownBy(() -> new PropiedadesApi("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LACRE_API_CLAVE");
    }

    @Test
    void unaClaveCortaSeRechazaYDiceCuantoFalta() {
        assertThatThrownBy(() -> new PropiedadesApi("demasiado-corta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos " + PropiedadesApi.MINIMO_LONGITUD_CLAVE);
    }

    /** Recorre el arranque real: sin la propiedad, el contexto de Spring no levanta. */
    @Test
    void sinClaveLaAplicacionNoLevanta() {
        new ApplicationContextRunner()
                .withUserConfiguration(HabilitaLasPropiedades.class)
                .withPropertyValues("lacre.api.clave=")
                // Se comprueba la traza entera: Spring envuelve el fallo de enlace.
                .run(contexto ->
                        assertThat(contexto).hasFailed().getFailure().hasStackTraceContaining("LACRE_API_CLAVE"));
    }

    @Test
    void conUnaClaveSuficienteLevanta() {
        new ApplicationContextRunner()
                .withUserConfiguration(HabilitaLasPropiedades.class)
                .withPropertyValues("lacre.api.clave=" + "a".repeat(PropiedadesApi.MINIMO_LONGITUD_CLAVE))
                .run(contexto -> assertThat(contexto).hasSingleBean(PropiedadesApi.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropiedadesApi.class)
    static class HabilitaLasPropiedades {}

    @Test
    void laClaveSeRecortaPorLosExtremos() {
        String treintaYDos = "a".repeat(PropiedadesApi.MINIMO_LONGITUD_CLAVE);

        assertThat(new PropiedadesApi("  " + treintaYDos + "\n").clave()).isEqualTo(treintaYDos);
    }

    @Test
    void elRecorteOcurreAntesDeMedirLaLongitud() {
        assertThatThrownBy(() -> new PropiedadesApi(" ".repeat(40) + "ab" + " ".repeat(40)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos " + PropiedadesApi.MINIMO_LONGITUD_CLAVE);
    }
}
