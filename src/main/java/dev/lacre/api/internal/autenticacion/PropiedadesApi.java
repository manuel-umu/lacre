package dev.lacre.api.internal.autenticacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clave con la que el ERP se autentica ante la API: una por despliegue, inyectada desde el
 * entorno y comparada en tiempo constante por {@link FiltroDeClaveDeApi}.
 */
@ConfigurationProperties("lacre.api")
public record PropiedadesApi(String clave) {

    /** Longitud mínima de la clave; se genera con {@code openssl rand -base64 32}. */
    public static final int MINIMO_LONGITUD_CLAVE = 32;

    public PropiedadesApi {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("""
                    Falta la clave de la API. lacre no arranca sin ella: una API de facturación \
                    abierta deja el registro fiscal de sus obligados al alcance de cualquiera que \
                    llegue al puerto.

                    Configúrala en la variable de entorno LACRE_API_CLAVE. Para generarla:
                        openssl rand -base64 32""");
        }
        clave = clave.strip();
        if (clave.length() < MINIMO_LONGITUD_CLAVE) {
            throw new IllegalStateException(
                    "La clave de la API (LACRE_API_CLAVE) necesita al menos "
                            + MINIMO_LONGITUD_CLAVE + " caracteres y tiene " + clave.length()
                            + ". Genérala con: openssl rand -base64 32");
        }
    }
}
