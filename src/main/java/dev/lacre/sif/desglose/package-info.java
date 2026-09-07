/**
 * Desglose de impuestos del registro de alta, con sus catálogos.
 * <p>
 * {@code Calificacion}, {@code CalificacionOperacion} y {@code OperacionExenta} viven juntas
 * por obligación del lenguaje: una jerarquía sellada y sus subtipos permitidos deben estar en
 * el mismo paquete mientras el proyecto no sea un módulo JPMS.
 *
 * @see dev.lacre.sif.desglose.Desglose
 */
@org.springframework.modulith.NamedInterface("desglose")
package dev.lacre.sif.desglose;
