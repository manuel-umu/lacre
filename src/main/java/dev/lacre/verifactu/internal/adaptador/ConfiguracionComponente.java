package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Nif;
import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.SistemaInformatico;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cableado con Spring del núcleo de {@code verifactu}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesComponente.class)
class ConfiguracionComponente {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionComponente.class);

    /** El mismo para calcular la huella de un registro nuevo y para verificar los guardados. */
    @Bean
    Canonicalizador canonicalizador() {
        return new CanonicalizadorAeat();
    }

    @Bean
    EncadenadorRegistros encadenadorRegistros(Clock reloj, Canonicalizador canonicalizador) {
        return new EncadenadorRegistros(reloj, canonicalizador);
    }

    /**
     * Identidad del bloque {@code SistemaInformatico}; debe coincidir con la declaración
     * responsable.
     */
    @Bean
    SistemaInformatico sistemaInformatico(PropiedadesComponente propiedades) {
        SistemaInformatico sistema = new SistemaInformatico(
                new PersonaFisicaJuridica(propiedades.nombreRazon(), new Nif(propiedades.nif())),
                propiedades.nombreSistemaInformatico(),
                propiedades.idSistemaInformatico(),
                propiedades.version(),
                propiedades.numeroInstalacion(),
                true,
                propiedades.multiObligado(),
                propiedades.sirveAVariosObligados());
        if (sistema.productorDeEjemplo()) {
            log.warn(
                    "El productor del sistema informático es el NIF de ejemplo {}: la AEAT rechazará los registros"
                            + " con el código 1110. Se configura con LACRE_PRODUCTOR_NIF y LACRE_PRODUCTOR_NOMBRE.",
                    SistemaInformatico.NIF_DE_EJEMPLO.valor());
        }
        return sistema;
    }
}
