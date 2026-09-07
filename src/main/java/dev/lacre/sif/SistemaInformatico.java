package dev.lacre.sif;

import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Datos del sistema informático de facturación que genera el registro,
 * {@code SistemaInformaticoType} del XSD.
 *
 * @param productor                   quien produce y comercializa el SIF, con su identificación fiscal
 * @param nombreSistemaInformatico    nombre comercial del sistema
 * @param idSistemaInformatico        identificador del sistema dentro del productor, 2 caracteres
 * @param version                     versión del sistema
 * @param numeroInstalacion           identificador de esta instalación concreta
 * @param tipoUsoPosibleSoloVerifactu si el sistema solo puede operar en modalidad VERI*FACTU;
 *                                    en lacre es siempre cierto, por la decisión de alcance
 * @param tipoUsoPosibleMultiOT       si el sistema puede dar servicio a varios obligados
 * @param indicadorMultiplesOT        si en esta instalación lo está haciendo efectivamente
 */
public record SistemaInformatico(
        PersonaFisicaJuridica productor,
        String nombreSistemaInformatico,
        String idSistemaInformatico,
        String version,
        String numeroInstalacion,
        boolean tipoUsoPosibleSoloVerifactu,
        boolean tipoUsoPosibleMultiOT,
        boolean indicadorMultiplesOT) {

    public static final int MAXIMO_LONGITUD_NOMBRE = 30;
    public static final int MAXIMO_LONGITUD_ID = 2;
    public static final int MAXIMO_LONGITUD_VERSION = 50;
    public static final int MAXIMO_LONGITUD_NUMERO_INSTALACION = 100;

    public SistemaInformatico {
        if (productor == null) {
            throw new ValorInvalidoException("El productor del sistema informático es obligatorio");
        }
        nombreSistemaInformatico = Textos.obligatorio(
                nombreSistemaInformatico, MAXIMO_LONGITUD_NOMBRE, "El nombre del sistema informático");
        idSistemaInformatico = Textos.obligatorio(
                idSistemaInformatico, MAXIMO_LONGITUD_ID, "El identificador del sistema informático");
        version = Textos.obligatorio(version, MAXIMO_LONGITUD_VERSION, "La versión del sistema informático");
        numeroInstalacion = Textos.obligatorio(
                numeroInstalacion, MAXIMO_LONGITUD_NUMERO_INSTALACION, "El número de instalación");

        if (indicadorMultiplesOT && !tipoUsoPosibleMultiOT) {
            throw new ValorInvalidoException(
                    "Un sistema que no admite varios obligados no puede declarar que está dando servicio a varios");
        }
    }
}
