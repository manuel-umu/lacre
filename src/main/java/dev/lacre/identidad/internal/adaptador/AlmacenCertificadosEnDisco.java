package dev.lacre.identidad.internal.adaptador;

import dev.lacre.identidad.AlmacenCertificados;
import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.CertificadoNoDisponibleException;
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

/** Lee los certificados de un directorio: un PKCS#12 por obligado, nombrado con su NIF. */
@Component
class AlmacenCertificadosEnDisco implements AlmacenCertificados {

    private static final Logger log = LoggerFactory.getLogger(AlmacenCertificadosEnDisco.class);

    /** Margen con el que se avisa de la caducidad. */
    private static final Duration AVISO_DE_CADUCIDAD = Duration.ofDays(30);

    private final PropiedadesCertificados propiedades;
    private final Clock reloj;

    AlmacenCertificadosEnDisco(PropiedadesCertificados propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Override
    public CertificadoDeObligado de(Nif nif) {
        Path fichero = ficheroDe(nif);
        char[] contrasena = contrasenaDe(nif);

        KeyStore almacen;
        try (InputStream entrada = Files.newInputStream(fichero)) {
            almacen = KeyStore.getInstance("PKCS12");
            almacen.load(entrada, contrasena);
        } catch (IOException e) {
            // Una contraseña incorrecta llega como IOException.
            throw new CertificadoNoDisponibleException(
                    nif.valor(), "no se pudo abrir su fichero; ¿ruta o contraseña equivocadas?", e);
        } catch (GeneralSecurityException e) {
            throw new CertificadoNoDisponibleException(nif.valor(), "no es un PKCS#12 legible", e);
        }

        CertificadoDeObligado certificado = CertificadoDeObligado.desde(almacen, contrasena, nif.valor());
        avisarSiCaduca(nif, certificado);
        return certificado;
    }

    private Path ficheroDe(Nif nif) {
        if (propiedades.directorio() == null || propiedades.directorio().isBlank()) {
            throw new CertificadoNoDisponibleException(
                    nif.valor(), "no hay directorio de certificados configurado en lacre.certificados.directorio");
        }
        Path fichero = Path.of(propiedades.directorio(), nif.valor() + ".p12");
        if (!Files.isReadable(fichero)) {
            throw new CertificadoNoDisponibleException(nif.valor(), "no hay fichero legible en " + fichero);
        }
        return fichero;
    }

    private char[] contrasenaDe(Nif nif) {
        String contrasena = propiedades.contrasenas().get(nif.valor());
        if (contrasena == null) {
            throw new CertificadoNoDisponibleException(nif.valor(), "no hay contraseña configurada para este obligado");
        }
        return contrasena.toCharArray();
    }

    /** Un certificado caducado se carga y se denuncia en el log; no impide cargarlo. */
    private void avisarSiCaduca(Nif nif, CertificadoDeObligado certificado) {
        Instant ahora = reloj.instant();
        if (certificado.caducadoA(ahora)) {
            log.error(
                    "El certificado del obligado {} CADUCÓ el {}: no podrá remitir a la AEAT "
                            + "hasta que se sustituya el fichero",
                    nif.valor(),
                    certificado.caducaEn());
        } else if (certificado.caducadoA(ahora.plus(AVISO_DE_CADUCIDAD))) {
            log.warn(
                    "El certificado del obligado {} caduca el {}, dentro de menos de {} días",
                    nif.valor(),
                    certificado.caducaEn(),
                    AVISO_DE_CADUCIDAD.toDays());
        }
    }
}
