package dev.lacre.identidad;

/**
 * No se ha podido obtener el certificado de un obligado. El mensaje nunca incluye la
 * contraseña.
 */
public class CertificadoNoDisponibleException extends RuntimeException {

    public CertificadoNoDisponibleException(Object origen, String motivo) {
        super("Certificado no disponible (" + origen + "): " + motivo);
    }

    public CertificadoNoDisponibleException(Object origen, String motivo, Throwable causa) {
        super("Certificado no disponible (" + origen + "): " + motivo, causa);
    }
}
