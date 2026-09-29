package dev.lacre.api.internal.autenticacion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Registra el filtro de autenticación acotado a {@code /v1/*}, de modo que
 * {@code /actuator/health} siga abierto.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesApi.class)
class ConfiguracionApi {

    @Bean
    FilterRegistrationBean<FiltroDeClaveDeApi> filtroDeClaveDeApi(PropiedadesApi propiedades, ObjectMapper json) {
        FilterRegistrationBean<FiltroDeClaveDeApi> registro = new FilterRegistrationBean<>();
        registro.setFilter(new FiltroDeClaveDeApi(propiedades, json));
        registro.addUrlPatterns("/v1/*");
        return registro;
    }
}
