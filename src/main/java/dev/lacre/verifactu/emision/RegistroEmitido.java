package dev.lacre.verifactu.emision;

import dev.lacre.shared.Huella;

import java.util.UUID;

/**
 * Lo que queda de un registro recién añadido a la cadena: su identidad y su sitio.
 * <p>
 * No devuelve el registro entero ni el XML. Quien emite ya sabe qué pidió; lo que no puede
 * saber es el identificador que se le asigna, la posición que le tocó en la cadena y la huella
 * que la cierra —que es la que debe imprimirse en la factura—.
 *
 * @param posicion empieza en 1. Es el número de eslabón dentro de la cadena del obligado, no un
 *                 número de factura: la serie la lleva el ERP.
 */
public record RegistroEmitido(UUID id, long posicion, Huella huella) {
}
