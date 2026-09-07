/**
 * Motor de huella: el puerto que produce la cadena canónica y el servicio de dominio que
 * encadena un registro con el anterior.
 * <p>
 * {@code Canonicalizador} concentra todo lo que depende de la especificación de la AEAT
 * —orden de campos, normalización, marca del primer registro—, y por eso su implementación
 * vive en {@code internal}.
 *
 * @see dev.lacre.verifactu.huella.EncadenadorRegistros
 */
@org.springframework.modulith.NamedInterface("huella")
package dev.lacre.verifactu.huella;
