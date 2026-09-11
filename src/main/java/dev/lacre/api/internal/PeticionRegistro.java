package dev.lacre.api.internal;

/**
 * Lo que el ERP pide: dar de alta un registro o anular una factura.
 * <p>
 * Sellado y no un campo discriminador, por la misma razón que {@code DatosRegistro} en el
 * dominio: los dos sitios que dependen del tipo —el mapeo y la huella de idempotencia— lo
 * resuelven con un {@code switch} que el compilador comprueba, y añadir un tercer tipo de
 * petición no puede quedarse a medias.
 * <p>
 * Ambas comparten cadena, cerrojo y tabla de idempotencia: para {@code Emisiones} son el mismo
 * caso de uso con distinto contenido.
 */
public sealed interface PeticionRegistro permits PeticionAlta, PeticionAnulacion {

    /** La factura que la petición identifica: la que se expide, o la que se anula. */
    IdFacturaDto factura();
}
