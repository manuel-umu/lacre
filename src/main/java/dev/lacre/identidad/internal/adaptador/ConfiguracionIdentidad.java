package dev.lacre.identidad.internal.adaptador;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Cablea la configuración del módulo. Lo demás lo descubre Spring por componentes. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesCertificados.class)
class ConfiguracionIdentidad {
}
