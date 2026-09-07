package dev.lacre.sif;

/**
 * Calificación de una línea de desglose.
 * <p>
 * El XSD lo modela como un {@code choice} excluyente entre {@code CalificacionOperacion} y
 * {@code OperacionExenta}: una línea está calificada <em>o</em> exenta, nunca ambas ni
 * ninguna. Aquí eso es un tipo sellado, de forma que el compilador impide construir un
 * desglose que el esquema rechazaría, y los {@code switch} sobre él son exhaustivos.
 */
public sealed interface Calificacion permits CalificacionOperacion, OperacionExenta {

    /** Código tal y como debe aparecer en el XML. */
    String codigo();
}
