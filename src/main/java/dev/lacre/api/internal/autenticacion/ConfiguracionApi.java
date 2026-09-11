package dev.lacre.api.internal.autenticacion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Cablea la API con Spring: hoy, solo su filtro de autenticación.
 * <p>
 * El filtro se registra con {@link FilterRegistrationBean} y <strong>no</strong> como
 * {@code @Component}. Un {@code Filter} declarado como bean lo registra Spring Boot solo, y para
 * <em>todas</em> las rutas: acabaría aplicándose también a {@code /actuator/health}, que tiene
 * que contestar a la sonda del contenedor sin credencial. Así queda acotado a {@code /v1/*}, que
 * es lo que hay que proteger, y se ve en una línea.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesApi.class)
class ConfiguracionApi {

    @Bean
    FilterRegistrationBean<FiltroDeClaveDeApi> filtroDeClaveDeApi(PropiedadesApi propiedades,
                                                                 ObjectMapper json) {
        FilterRegistrationBean<FiltroDeClaveDeApi> registro = new FilterRegistrationBean<>();
        registro.setFilter(new FiltroDeClaveDeApi(propiedades, json));
        registro.addUrlPatterns("/v1/*");
        return registro;
    }
}
