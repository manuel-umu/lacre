package dev.lacre.identidad.internal.adaptador;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Dónde están los certificados de los obligados y con qué se abren.
 * <p>
 * Un fichero PKCS#12 por obligado, nombrado con su NIF, en un directorio que monta quien
 * despliega. Rotar un certificado es sustituir su fichero.
 * <p>
 * <strong>Las contraseñas no se escriben en el {@code application.yaml} del repositorio.</strong>
 * Vienen de variables de entorno o de un fichero de secretos montado en el contenedor; el mapa
 * está aquí para poder inyectarlas por obligado, porque cada PKCS#12 trae la suya.
 *
 * @param directorio ruta del volumen con los {@code <NIF>.p12}
 * @param contrasenas contraseña de cada fichero, con el NIF como clave
 */
@ConfigurationProperties("lacre.certificados")
public record PropiedadesCertificados(String directorio, Map<String, String> contrasenas) {

    public PropiedadesCertificados {
        contrasenas = contrasenas == null ? Map.of() : Map.copyOf(contrasenas);
    }
}
