package dev.lacre.identidad.internal.adaptador;

import dev.lacre.identidad.AlmacenCertificados;
import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.CertificadoNoDisponibleException;
import dev.lacre.identidad.OrigenCertificado;
import dev.lacre.shared.Nif;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lee los certificados de un directorio: un PKCS#12 por obligado, nombrado con su NIF, y un
 * {@code presentador.p12} opcional que remite por los obligados que no tienen el suyo.
 */
@Component
class AlmacenCertificadosEnDisco implements AlmacenCertificados {

    private static final Logger log = LoggerFactory.getLogger(AlmacenCertificadosEnDisco.class);

    /** Margen con el que se avisa de la caducidad. */
    private static final Duration AVISO_DE_CADUCIDAD = Duration.ofDays(30);

    /** Nombre, sin extensión, del fichero del presentador. */
    static final String PRESENTADOR = "presentador";

    private final PropiedadesCertificados propiedades;
    private final Clock reloj;

    AlmacenCertificadosEnDisco(PropiedadesCertificados propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Override
    public CertificadoDeObligado de(Nif nif) {
        Path propio = ficheroDe(nif.valor());
        if (Files.isReadable(propio)) {
            CertificadoDeObligado certificado = abrir(propio, contrasenaDe(nif), nif.valor());
            avisarSiCaduca("del obligado " + nif.valor(), certificado);
            return certificado;
        }
        Path presentador = ficheroDe(PRESENTADOR);
        if (!Files.isReadable(presentador)) {
            throw new CertificadoNoDisponibleException(
                    nif.valor(),
                    "no hay fichero legible en " + propio + " ni certificado del presentador en " + presentador);
        }
        String contrasena = propiedades.presentador().contrasena();
        if (contrasena == null) {
            throw new CertificadoNoDisponibleException(
                    nif.valor(),
                    "no tiene certificado propio y el del presentador no tiene contraseña configurada "
                            + "(LACRE_CERTIFICADOS_PRESENTADOR_CONTRASENA)");
        }
        CertificadoDeObligado certificado = abrir(presentador, contrasena.toCharArray(), "presentador");
        avisarSiCaduca("del presentador", certificado);
        return certificado;
    }

    @Override
    public OrigenCertificado origenDe(Nif nif) {
        if (sinDirectorio()) {
            return OrigenCertificado.NINGUNO;
        }
        if (Files.isReadable(ficheroDe(nif.valor()))) {
            return OrigenCertificado.PROPIO;
        }
        return Files.isReadable(ficheroDe(PRESENTADOR)) ? OrigenCertificado.PRESENTADOR : OrigenCertificado.NINGUNO;
    }

    private CertificadoDeObligado abrir(Path fichero, char[] contrasena, String origen) {
        KeyStore almacen;
        try (InputStream entrada = Files.newInputStream(fichero)) {
            almacen = KeyStore.getInstance("PKCS12");
            almacen.load(entrada, contrasena);
        } catch (IOException e) {
            // Una contraseña incorrecta llega como IOException.
            throw new CertificadoNoDisponibleException(
                    origen, "no se pudo abrir su fichero; ¿ruta o contraseña equivocadas?", e);
        } catch (GeneralSecurityException e) {
            throw new CertificadoNoDisponibleException(origen, "no es un PKCS#12 legible", e);
        }
        return CertificadoDeObligado.desde(almacen, contrasena, origen);
    }

    private boolean sinDirectorio() {
        return propiedades.directorio() == null || propiedades.directorio().isBlank();
    }

    private Path ficheroDe(String nombre) {
        if (sinDirectorio()) {
            throw new CertificadoNoDisponibleException(
                    nombre, "no hay directorio de certificados configurado en lacre.certificados.directorio");
        }
        return Path.of(propiedades.directorio(), nombre + ".p12");
    }

    private char[] contrasenaDe(Nif nif) {
        String contrasena = propiedades.contrasenas().get(nif.valor());
        if (contrasena == null) {
            throw new CertificadoNoDisponibleException(nif.valor(), "no hay contraseña configurada para este obligado");
        }
        return contrasena.toCharArray();
    }

    /** Un certificado caducado se carga y se denuncia en el log; no impide cargarlo. */
    private void avisarSiCaduca(String de, CertificadoDeObligado certificado) {
        Instant ahora = reloj.instant();
        if (certificado.caducadoA(ahora)) {
            log.error(
                    "El certificado {} CADUCÓ el {}: no podrá remitir a la AEAT " + "hasta que se sustituya el fichero",
                    de,
                    certificado.caducaEn());
        } else if (certificado.caducadoA(ahora.plus(AVISO_DE_CADUCIDAD))) {
            log.warn(
                    "El certificado {} caduca el {}, dentro de menos de {} días",
                    de,
                    certificado.caducaEn(),
                    AVISO_DE_CADUCIDAD.toDays());
        }
    }
}
