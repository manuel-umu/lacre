package dev.lacre.remision;

/**
 * Situación de un registro respecto de su remisión a la AEAT.
 * <p>
 * Hoy solo existe el estado inicial. Los que devuelve la AEAT —{@code Correcto},
 * {@code AceptadoConErrores} e {@code Incorrecto}— se añaden en la Fase 6.1, junto al cliente
 * que los recibe: nombrarlos antes de ver una respuesta real sería adivinar.
 */
public enum EstadoEnvio {

    /** Creado con el registro, todavía sin despachar. */
    PENDIENTE
}
