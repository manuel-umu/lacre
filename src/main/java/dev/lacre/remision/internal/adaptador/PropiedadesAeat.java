package dev.lacre.remision.internal.adaptador;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * A dónde se remite y cuánto se espera.
 * <p>
 * El endpoint es configuración y no una constante porque el WSDL declara cuatro, y elegir el que
 * toca es decisión de despliegue:
 * <ul>
 * <li>{@code https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP}
 *     — producción</li>
 * <li>{@code https://www10.agenciatributaria.gob.es/…} — producción con certificado de sello</li>
 * <li>{@code https://prewww1.aeat.es/…} — pruebas</li>
 * <li>{@code https://prewww10.aeat.es/…} — pruebas con certificado de sello</li>
 * </ul>
 * Los de sello son otro host, no otra ruta: mandar un certificado de sello al host que no toca
 * falla en el TLS, antes de que el mensaje llegue a leerse.
 *
 * @param timeout corte de la espera. No confundir con el {@code TiempoEsperaEnvio} que devuelve la
 *                AEAT, que es control de flujo entre envíos y lo gestiona el despachador.
 */
@ConfigurationProperties("lacre.aeat")
public record PropiedadesAeat(String endpoint, Duration timeout) {

    public PropiedadesAeat {
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }
}
