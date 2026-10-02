package dev.lacre.identidad;

import dev.lacre.shared.Nif;

/**
 * Puerto hacia el almacén de certificados: el propio de cada obligado o, si no lo tiene, el del
 * presentador.
 */
public interface AlmacenCertificados {

    /**
     * @throws CertificadoNoDisponibleException si no hay certificado para ese obligado, o si no
     *         se puede abrir
     */
    CertificadoDeObligado de(Nif nif);

    /** Qué certificado se usaría para el obligado, sin abrirlo. */
    OrigenCertificado origenDe(Nif nif);
}
