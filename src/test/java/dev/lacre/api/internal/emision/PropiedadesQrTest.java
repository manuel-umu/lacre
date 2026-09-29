package dev.lacre.api.internal.emision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Validación de la URL base del código QR, sin Spring. */
class PropiedadesQrTest {

    private static final String PRUEBAS = "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?";

    @Test
    void sinUrlBaseNoSeArranca() {
        assertThatThrownBy(() -> new PropiedadesQr(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lacre.qr.url-base");
    }

    @Test
    void unaUrlEnBlancoEsComoNoTenerla() {
        assertThatThrownBy(() -> new PropiedadesQr("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lacre.qr.url-base");
    }

    /** Sin el interrogante, el primer parámetro se pegaría a la ruta del servicio. */
    @Test
    void unaUrlQueNoTerminaEnInterroganteSeRechaza() {
        assertThatThrownBy(() -> new PropiedadesQr(PRUEBAS.substring(0, PRUEBAS.length() - 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe terminar en '?'");
    }

    /** Recorre el arranque real: sin la propiedad, el contexto de Spring no levanta. */
    @Test
    void sinUrlBaseLaAplicacionNoLevanta() {
        new ApplicationContextRunner()
                .withUserConfiguration(HabilitaLasPropiedades.class)
                .withPropertyValues("lacre.qr.url-base=")
                .run(contexto ->
                        assertThat(contexto).hasFailed().getFailure().hasStackTraceContaining("lacre.qr.url-base"));
    }

    @Test
    void conLaUrlDePruebasLevanta() {
        new ApplicationContextRunner()
                .withUserConfiguration(HabilitaLasPropiedades.class)
                .withPropertyValues("lacre.qr.url-base=" + PRUEBAS)
                .run(contexto -> assertThat(contexto).hasSingleBean(PropiedadesQr.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropiedadesQr.class)
    static class HabilitaLasPropiedades {}
}
