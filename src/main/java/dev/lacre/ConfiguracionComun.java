package dev.lacre;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj y generador de identificadores de la aplicación, inyectables para que los tests puedan
 * fijarlos.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionComun {

    /** Solo aporta el instante; la zona horaria la aporta cada obligado. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    Supplier<UUID> generadorDeIdentificadores() {
        return UUID::randomUUID;
    }
}
