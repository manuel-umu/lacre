package dev.lacre.remision.internal.adaptador;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endpoint de la AEAT y tiempo máximo de espera de la conexión.
 * <ul>
 * <li>{@code https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP}:
 *     producción</li>
 * <li>{@code https://www10.agenciatributaria.gob.es/…}: producción con certificado de sello</li>
 * <li>{@code https://prewww1.aeat.es/…}: pruebas</li>
 * <li>{@code https://prewww10.aeat.es/…}: pruebas con certificado de sello</li>
 * </ul>
 *
 * @param timeout distinto del tiempo de espera entre envíos que devuelve la AEAT
 */
@ConfigurationProperties("lacre.aeat")
public record PropiedadesAeat(String endpoint, Duration timeout) {

    public PropiedadesAeat {
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }
}
