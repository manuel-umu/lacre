package dev.lacre.api.internal.emision;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita la URL base del servicio de cotejo que va en el código QR. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesQr.class)
class ConfiguracionEmision {}
