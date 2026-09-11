package dev.lacre.verifactu.emision;

import dev.lacre.shared.Huella;

import java.util.UUID;

/**
 * Identidad, posición y huella de un registro recién añadido a la cadena.
 *
 * @param posicion número de eslabón dentro de la cadena del obligado, desde 1
 */
public record RegistroEmitido(UUID id, long posicion, Huella huella) {
}
