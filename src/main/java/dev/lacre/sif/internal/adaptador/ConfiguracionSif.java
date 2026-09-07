package dev.lacre.sif.internal.adaptador;

import dev.lacre.sif.huella.EncadenadorRegistros;
import dev.lacre.sif.internal.CanonicalizadorAeat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cablea el núcleo puro de {@code sif} con Spring. El núcleo no conoce anotaciones; es aquí
 * donde se le da un reloj y un generador de identificadores.
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionSif {

    /**
     * La zona del reloj <strong>entra en el cálculo de la huella</strong>, porque
     * {@code FechaHoraHusoGenRegistro} se serializa con su huso. Tiene que ser la del obligado,
     * no UTC.
     */
    @Bean
    Clock relojDelObligado() {
        return Clock.system(java.time.ZoneId.of("Europe/Madrid"));
    }

    @Bean
    EncadenadorRegistros encadenadorRegistros(Clock reloj) {
        return new EncadenadorRegistros(reloj, new CanonicalizadorAeat());
    }

    /** Inyectado y no llamado directamente, para que los tests puedan fijar los identificadores. */
    @Bean
    Supplier<UUID> generadorDeIdentificadores() {
        return UUID::randomUUID;
    }
}
