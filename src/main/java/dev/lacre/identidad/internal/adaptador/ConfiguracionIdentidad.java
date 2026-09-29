package dev.lacre.identidad.internal.adaptador;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuración del módulo {@code identidad}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesCertificados.class)
class ConfiguracionIdentidad {}
