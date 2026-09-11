package dev.lacre.verifactu.registro;

/**
 * Contenido de un registro de facturación, de alta o de anulación. Ambos comparten cadena.
 * Tipo sellado, para que los {@code switch} sobre él sean exhaustivos.
 */
public sealed interface DatosRegistro permits DatosRegistroAlta, DatosRegistroAnulacion {

    TipoRegistro tipo();

    /** Identificación de la factura: la que se expide, o la que se anula. */
    IdFactura idFactura();

    SistemaInformatico sistemaInformatico();
}
