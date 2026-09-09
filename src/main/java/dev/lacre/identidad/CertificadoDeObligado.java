package dev.lacre.identidad;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;

/**
 * Certificado electrónico con el que un obligado se autentica ante la AEAT.
 * <p>
 * La AEAT exige certificado cualificado reconocido y autenticación mutua: quien remite es el
 * obligado, no nosotros.
 * <p>
 * <strong>La contraseña no sale de aquí.</strong> No hay forma de leerla desde fuera: lo que se
 * expone son los {@link KeyManager} ya construidos, que es lo único que necesita la capa de
 * transporte. Es deliberado —una contraseña que circula acaba en un log— y por eso la clase no
 * es un {@code record}, que expondría sus componentes.
 */
public final class CertificadoDeObligado {

    private final KeyStore almacen;
    private final char[] contrasena;
    private final X509Certificate certificado;

    private CertificadoDeObligado(KeyStore almacen, char[] contrasena, X509Certificate certificado) {
        this.almacen = almacen;
        this.contrasena = contrasena;
        this.certificado = certificado;
    }

    /**
     * @throws CertificadoNoDisponibleException si el almacén no contiene ninguna clave privada;
     *         un PKCS#12 solo con certificados no sirve para autenticarse
     */
    public static CertificadoDeObligado desde(KeyStore almacen, char[] contrasena, Object origen) {
        Objects.requireNonNull(almacen, "almacen");
        Objects.requireNonNull(contrasena, "contrasena");
        try {
            for (String alias : Collections.list(almacen.aliases())) {
                if (almacen.isKeyEntry(alias)
                        && almacen.getCertificate(alias) instanceof X509Certificate x509) {
                    return new CertificadoDeObligado(almacen, contrasena.clone(), x509);
                }
            }
        } catch (GeneralSecurityException e) {
            throw new CertificadoNoDisponibleException(origen, "no se pudo leer su contenido", e);
        }
        throw new CertificadoNoDisponibleException(origen, "no contiene ninguna clave privada");
    }

    /** Lo que necesita la capa de transporte para el TLS mutuo, y nada más. */
    public KeyManager[] gestoresDeClave() {
        try {
            KeyManagerFactory factoria =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factoria.init(almacen, contrasena);
            return factoria.getKeyManagers();
        } catch (GeneralSecurityException e) {
            throw new CertificadoNoDisponibleException(titular(), "no se pudo preparar para TLS", e);
        }
    }

    public Instant caducaEn() {
        return certificado.getNotAfter().toInstant();
    }

    public boolean caducadoA(Instant momento) {
        return !caducaEn().isAfter(momento);
    }

    /** Nombre distinguido del titular, útil en trazas y en el aviso de caducidad. */
    public String titular() {
        return certificado.getSubjectX500Principal().getName();
    }
}
