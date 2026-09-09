package dev.lacre.identidad;

import dev.lacre.shared.Nif;

/**
 * Puerto de {@code identidad} hacia donde vivan los certificados de los obligados.
 * <p>
 * Es puerto y no detalle porque el sitio donde se guardan es una decisión de despliegue: hoy son
 * ficheros PKCS#12 en un volumen que monta el cliente, y esa elección es deliberada —lacre
 * <strong>no</strong> guarda claves privadas ajenas en su base de datos, que es lo que le ahorra
 * a un comprador la conversación más difícil de una venta fiscal—.
 */
public interface AlmacenCertificados {

    /**
     * @throws CertificadoNoDisponibleException si no hay certificado para ese obligado, o si no
     *         se puede abrir
     */
    CertificadoDeObligado de(Nif nif);
}
