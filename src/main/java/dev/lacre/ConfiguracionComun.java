package dev.lacre;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Las dos fuentes de indeterminismo del sistema, en un solo sitio y detrás de una interfaz para
 * que los tests puedan fijarlas: el reloj y el generador de identificadores.
 * <p>
 * Viven en el paquete raíz, que Modulith ignora, y no en la configuración de un módulo: los
 * usan {@code verifactu} y {@code remision}, y declararlos en uno de los dos dejaría al otro
 * dependiendo en silencio de beans ajenos que desaparecerían al extraer la librería.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionComun {

    /**
     * Solo aporta el instante. La zona con la que se fecha cada registro —que <strong>entra en
     * el cálculo de la huella</strong>— la pone el obligado en cada llamada, porque una misma
     * instalación factura para obligados peninsulares y canarios.
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    Supplier<UUID> generadorDeIdentificadores() {
        return UUID::randomUUID;
    }
}
