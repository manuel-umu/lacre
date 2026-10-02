package dev.lacre.identidad.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Las contraseñas de los certificados, también cuando llegan desde variables de entorno. */
class PropiedadesCertificadosTest {

    @Test
    void laContrasenaDeUnNifConLetraSeEncuentraDesdeUnaVariableDeEntorno() {
        PropiedadesCertificados propiedades = desdeElEntorno(Map.of(
                "LACRE_CERTIFICADOS_DIRECTORIO", "/certificados",
                "LACRE_CERTIFICADOS_CONTRASENAS_89890001K", "de-la-persona",
                "LACRE_CERTIFICADOS_CONTRASENAS_B12345674", "de-la-sociedad"));

        assertThat(propiedades.contrasenas())
                .containsOnly(Map.entry("89890001K", "de-la-persona"), Map.entry("B12345674", "de-la-sociedad"));
    }

    @Test
    void lasClavesEscritasEnMinusculasTambienValen() {
        assertThat(new PropiedadesCertificados("/certificados", Map.of("89890001k", "x")).contrasenas())
                .containsOnlyKeys("89890001K");
    }

    @Test
    void elMismoNifDosVecesEsUnErrorDeConfiguracion() {
        assertThatThrownBy(() -> new PropiedadesCertificados("/c", Map.of("89890001k", "x", "89890001K", "y")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("89890001K");
    }

    private static PropiedadesCertificados desdeElEntorno(Map<String, Object> variables) {
        StandardEnvironment entorno = new StandardEnvironment();
        // Solo la fuente con este nombre recibe la traducción de nombres de variables de entorno.
        entorno.getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return new Binder(ConfigurationPropertySources.get(entorno))
                .bind("lacre.certificados", PropiedadesCertificados.class)
                .get();
    }
}
