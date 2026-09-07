package dev.lacre.sif.registro;

/**
 * Tipos de registro de facturación que genera un SIF VERI*FACTU.
 * <p>
 * No hay {@code EVENTO}: el registro de eventos solo es obligatorio en modalidad no
 * VERI*FACTU, que queda fuera del alcance de lacre.
 */
public enum TipoRegistro {

    ALTA,
    ANULACION
}
