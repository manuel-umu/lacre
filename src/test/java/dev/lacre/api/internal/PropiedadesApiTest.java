package dev.lacre.api.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que impide desplegar lacre con la puerta abierta o con una cerradura de juguete.
 * <p>
 * Sin Spring: la validación vive en el constructor del record, así que no hace falta arrancar
 * nada para comprobarla.
 */
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

    /** Una clave corta es adivinable, y lo que protege es el registro fiscal de un obligado. */
    @Test
    void unaClaveCortaSeRechazaYDiceCuantoFalta() {
        assertThatThrownBy(() -> new PropiedadesApi("demasiado-corta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos " + PropiedadesApi.MINIMO_LONGITUD_CLAVE);
    }

    /**
     * El constructor puede lanzar todo lo que quiera, pero lo que hay que demostrar es que
     * <strong>el arranque se para</strong>. Este es el único test que recorre el camino de
     * verdad: propiedad ausente → enlace de {@code @ConfigurationProperties} → contexto que no
     * levanta. Sin él, un cambio en cómo se enlazan las propiedades podría dejar la clave a nulo
     * y la API abierta sin que nada se pusiera rojo.
     */
    @Test
    void sinClaveLaAplicacionNoLevanta() {
        new ApplicationContextRunner()
                .withUserConfiguration(HabilitaLasPropiedades.class)
                .withPropertyValues("lacre.api.clave=")
                // Contra la traza y no contra el mensaje de arriba: Spring envuelve el fallo de
                // enlace, y lo que ve quien despliega —y lo que aquí importa— es que el nombre de
                // la variable de entorno acaba impreso en la consola.
                .run(contexto -> assertThat(contexto).hasFailed()
                        .getFailure().hasStackTraceContaining("LACRE_API_CLAVE"));
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
    static class HabilitaLasPropiedades {
    }

    @Test
    void laClaveSeRecortaPorLosExtremos() {
        String treintaYDos = "a".repeat(PropiedadesApi.MINIMO_LONGITUD_CLAVE);

        assertThat(new PropiedadesApi("  " + treintaYDos + "\n").clave()).isEqualTo(treintaYDos);
    }

    /**
     * El recorte ocurre <strong>antes</strong> de medir: una clave de espacios con dos caracteres
     * dentro no puede colarse por tener la longitud justa.
     */
    @Test
    void elRecorteOcurreAntesDeMedirLaLongitud() {
        assertThatThrownBy(() -> new PropiedadesApi(" ".repeat(40) + "ab" + " ".repeat(40)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos " + PropiedadesApi.MINIMO_LONGITUD_CLAVE);
    }
}
