package dev.lacre.api.internal.autenticacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La credencial con la que el ERP llama a la API.
 * <p>
 * <strong>Una sola clave por despliegue</strong>, y es deliberado: lacre lo despliega el
 * cliente en su propia infraestructura (ADR 0005), así que un despliegue es un ERP y el ERP es
 * la frontera de confianza. Que cada clave declarase para qué obligados vale solo haría falta
 * si varios terceros compartieran una misma instalación, y ese no es el modelo. Si algún día lo
 * es, esto se convierte en una tabla de {@code identidad} y el filtro pasa a comprobar también
 * el obligado de la petición.
 * <p>
 * <strong>La clave nunca se escribe en el YAML.</strong> Viene de una variable de entorno o de
 * un fichero de secretos montado en el contenedor, igual que las contraseñas de los PKCS#12.
 * <p>
 * Se guarda y se compara en claro, no hasheada. El razonamiento: el valor configurado
 * <em>es</em> el secreto de todas formas —quien pueda leer la configuración del proceso ya ha
 * ganado—, y exigir que el operador la hashee antes de configurarla añade un paso y una
 * herramienta a cambio de nada. Lo que sí importa es que la comparación sea en tiempo constante
 * y que el valor no acabe en un log; de eso se ocupa {@link FiltroDeClaveDeApi}.
 */
@ConfigurationProperties("lacre.api")
public record PropiedadesApi(String clave) {

    /**
     * Una clave corta es adivinable, y esto protege el registro fiscal de un obligado. Treinta y
     * dos caracteres son unos 190 bits si se generan al azar, que es lo que debe hacerse:
     * {@code openssl rand -base64 32}.
     */
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
