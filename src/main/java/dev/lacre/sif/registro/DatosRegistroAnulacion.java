package dev.lacre.sif.registro;

import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Contenido de un registro de facturación de anulación,
 * {@code RegistroFacturacionAnulacionType} del XSD.
 * <p>
 * Mucho más pequeño que el de alta: una anulación no lleva desglose ni importes, solo dice qué
 * factura se anula y quién lo hace. Por eso no necesita builder.
 *
 * @param idFactura      identifica la factura que se anula, no una nueva
 * @param rechazoPrevio  aquí es un booleano y no el enum {@link RechazoPrevio} del alta: el
 *                       esquema solo admite {@code S} o {@code N} para las anulaciones, sin la
 *                       {@code X} de «no existe en la AEAT»
 * @param generador      obligatorio en la práctica cuando {@code generadoPor} no es el
 *                       expedidor, aunque el esquema lo deje opcional
 */
public record DatosRegistroAnulacion(
        IdFactura idFactura,
        String refExterna,
        boolean sinRegistroPrevio,
        boolean rechazoPrevio,
        GeneradoPor generadoPor,
        PersonaFisicaJuridica generador,
        SistemaInformatico sistemaInformatico) implements DatosRegistro {

    public static final int MAXIMO_LONGITUD_REF_EXTERNA = 60;

    public DatosRegistroAnulacion {
        if (idFactura == null) {
            throw new ValorInvalidoException("La identificación de la factura anulada es obligatoria");
        }
        refExterna = Textos.opcional(refExterna, MAXIMO_LONGITUD_REF_EXTERNA, "La referencia externa");
        if (sistemaInformatico == null) {
            throw new ValorInvalidoException("El sistema informático es obligatorio");
        }
    }

    @Override
    public TipoRegistro tipo() {
        return TipoRegistro.ANULACION;
    }
}
