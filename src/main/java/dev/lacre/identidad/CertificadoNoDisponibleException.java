package dev.lacre.identidad;

/**
 * No se ha podido obtener el certificado de un obligado.
 * <p>
 * El mensaje nombra el origen y el motivo, pero <strong>nunca la contraseña</strong>: es un error
 * que va a acabar en un log de alguien que integra, y una contraseña filtrada ahí no se recupera.
 */
public class CertificadoNoDisponibleException extends RuntimeException {

    public CertificadoNoDisponibleException(Object origen, String motivo) {
        super("Certificado no disponible (" + origen + "): " + motivo);
    }

    public CertificadoNoDisponibleException(Object origen, String motivo, Throwable causa) {
        super("Certificado no disponible (" + origen + "): " + motivo, causa);
    }
}
