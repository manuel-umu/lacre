package dev.lacre.remision.internal.adaptador;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuración del módulo {@code remision}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesAeat.class)
class ConfiguracionRemision {}
