package dev.lacre.sif.registro;

/**
 * Contenido de un registro de facturación, sea de alta o de anulación.
 * <p>
 * Ambos comparten cadena: el encadenamiento es por obligado y no distingue el tipo, de modo
 * que una anulación enlaza con el alta anterior igual que un alta con otra alta.
 * <p>
 * Es un tipo sellado y no un campo discriminador para que los {@code switch} que dependen del
 * tipo —la cadena canónica y el XML, que usan campos distintos en cada caso— los compruebe el
 * compilador y no se queden a medias al añadir un tipo nuevo.
 */
public sealed interface DatosRegistro permits DatosRegistroAlta, DatosRegistroAnulacion {

    TipoRegistro tipo();

    /** Identificación de la factura: la que se expide, o la que se anula. */
    IdFactura idFactura();

    SistemaInformatico sistemaInformatico();
}
