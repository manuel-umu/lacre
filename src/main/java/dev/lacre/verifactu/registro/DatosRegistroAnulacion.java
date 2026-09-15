package dev.lacre.verifactu.registro;

import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Contenido de un registro de facturación de anulación,
 * {@code RegistroFacturacionAnulacionType} del XSD.
 *
 * @param idFactura     la factura que se anula
 * @param rechazoPrevio booleano, a diferencia de {@link RechazoPrevio} en el alta: el esquema
 *                      solo admite {@code S} o {@code N} en anulaciones
 * @param generador     quien genera la anulación cuando no es el expedidor
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

    @Override
    public CamposDeHuella camposDeHuella() {
        return new CamposDeHuella.Anulacion(idFactura);
    }

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
