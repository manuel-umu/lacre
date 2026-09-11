package dev.lacre.verifactu.desglose;

/** Calificación de una línea de desglose: calificada o exenta, nunca ambas ni ninguna. */
public sealed interface Calificacion permits CalificacionOperacion, OperacionExenta {

    /** Código tal y como debe aparecer en el XML. */
    String codigo();
}
