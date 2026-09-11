package dev.lacre.identidad;

import dev.lacre.shared.Nif;

/** Puerto hacia el almacén de certificados de los obligados. */
public interface AlmacenCertificados {

    /**
     * @throws CertificadoNoDisponibleException si no hay certificado para ese obligado, o si no
     *         se puede abrir
     */
    CertificadoDeObligado de(Nif nif);
}
