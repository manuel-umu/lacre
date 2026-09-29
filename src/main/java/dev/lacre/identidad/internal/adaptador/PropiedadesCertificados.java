package dev.lacre.identidad.internal.adaptador;

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

    public PropiedadesCertificados {
        contrasenas = contrasenas == null ? Map.of() : Map.copyOf(contrasenas);
    }
}
