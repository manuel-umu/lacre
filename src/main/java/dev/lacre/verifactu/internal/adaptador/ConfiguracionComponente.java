package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Nif;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.SistemaInformatico;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Cableado con Spring del núcleo de {@code verifactu}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesComponente.class)
class ConfiguracionComponente {

    @Bean
    EncadenadorRegistros encadenadorRegistros(Clock reloj) {
        return new EncadenadorRegistros(reloj, new CanonicalizadorAeat());
    }

    /**
     * Identidad del bloque {@code SistemaInformatico}; debe coincidir con la declaración
     * responsable.
     */
    @Bean
    SistemaInformatico sistemaInformatico(PropiedadesComponente propiedades) {
        return new SistemaInformatico(
                new PersonaFisicaJuridica(propiedades.nombreRazon(), new Nif(propiedades.nif())),
                propiedades.nombreSistemaInformatico(),
                propiedades.idSistemaInformatico(),
                propiedades.version(),
                propiedades.numeroInstalacion(),
                true,
                propiedades.multiObligado(),
                propiedades.sirveAVariosObligados());
    }
}
