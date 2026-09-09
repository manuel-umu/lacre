package dev.lacre.remision.internal.adaptador;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Cablea la configuración del módulo. Lo demás lo descubre Spring por componentes. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesAeat.class)
class ConfiguracionRemision {
}
