package dev.lacre.api.internal.emision;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL base del servicio de cotejo que va en el código QR de la factura. Sin valor por defecto:
 * un QR del entorno equivocado sale impreso en cada factura.
 * <ul>
 * <li>{@code https://www2.agenciatributaria.gob.es/wlpl/TIKE-CONT/ValidarQR?}: producción</li>
 * <li>{@code https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?}: pruebas</li>
 * </ul>
 */
@ConfigurationProperties("lacre.qr")
public record PropiedadesQr(String urlBase) {

    public PropiedadesQr {
        urlBase = urlBase == null ? "" : urlBase.trim();
        if (urlBase.isEmpty()) {
            throw new IllegalStateException("Falta lacre.qr.url-base, la URL del servicio de "
                    + "cotejo que va dentro del código QR de cada factura");
        }
        if (!urlBase.endsWith("?")) {
            throw new IllegalStateException(
                    "lacre.qr.url-base debe terminar en '?': los cuatro parámetros se le añaden "
                            + "detrás. Recibido: " + urlBase);
        }
    }
}
