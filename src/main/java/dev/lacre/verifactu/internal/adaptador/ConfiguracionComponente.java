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
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cablea el núcleo puro de {@code verifactu} con Spring. El núcleo no conoce anotaciones; es aquí
 * donde se le da un reloj, una identidad y un generador de identificadores.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesComponente.class)
class ConfiguracionComponente {

    /**
     * La zona del reloj <strong>entra en el cálculo de la huella</strong>, porque
     * {@code FechaHoraHusoGenRegistro} se serializa con su huso. Por eso es configuración y no
     * una constante: un obligado en Canarias factura en {@code Atlantic/Canary}, que difiere en
     * una hora del peninsular, y con la zona equivocada la huella no cuadraría con la que
     * recalcula la AEAT.
     *
     * @implNote TODO Cuando el módulo {@code identidad} gestione varios obligados, la zona pasa a
     * ser un dato de cada obligado y no del despliegue.
     */
    @Bean
    Clock relojDelObligado(PropiedadesComponente propiedades) {
        return Clock.system(ZoneId.of(propiedades.zonaHoraria()));
    }

    @Bean
    EncadenadorRegistros encadenadorRegistros(Clock reloj) {
        return new EncadenadorRegistros(reloj, new CanonicalizadorAeat());
    }

    /**
     * Identidad que viaja en el bloque {@code SistemaInformatico} de cada registro. Debe
     * coincidir con la declaración responsable de quien comercializa el sistema.
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

    /** Inyectado y no llamado directamente, para que los tests puedan fijar los identificadores. */
    @Bean
    Supplier<UUID> generadorDeIdentificadores() {
        return UUID::randomUUID;
    }
}
