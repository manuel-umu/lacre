package dev.lacre.identidad.internal.adaptador;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ubicación y contraseñas de los certificados de los obligados: un PKCS#12 por obligado,
 * nombrado con su NIF. Las contraseñas se inyectan desde el entorno.
 *
 * @param directorio  ruta del directorio con los {@code <NIF>.p12}
 * @param contrasenas contraseña de cada fichero, con el NIF como clave
 */
@ConfigurationProperties("lacre.certificados")
public record PropiedadesCertificados(String directorio, Map<String, String> contrasenas) {

    /** El NIF de la clave se pasa a mayúsculas: desde una variable de entorno llega en minúsculas. */
    public PropiedadesCertificados {
        Map<String, String> porNif = new HashMap<>();
        if (contrasenas != null) {
            contrasenas.forEach((nif, contrasena) -> {
                if (porNif.put(nif.toUpperCase(Locale.ROOT), contrasena) != null) {
                    throw new IllegalStateException("La contraseña del certificado de " + nif.toUpperCase(Locale.ROOT)
                            + " está configurada dos veces");
                }
            });
        }
        contrasenas = Map.copyOf(porNif);
    }
}
