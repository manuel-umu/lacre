package dev.lacre.verifactu.emision;

import dev.lacre.shared.Huella;

import java.util.Set;
import java.util.UUID;

/**
 * Identidad, posición y huella de un registro recién añadido a la cadena, con lo que la
 * comprobación previa encontró al escribirlo.
 *
 * @param posicion número de eslabón dentro de la cadena del obligado, desde 1
 * @param avisos   vacío si la cadena estaba sana; nunca impide que el registro exista
 */
public record RegistroEmitido(UUID id, long posicion, Huella huella, Set<AnomaliaPrevia> avisos) {

    public RegistroEmitido {
        avisos = Set.copyOf(avisos);
    }
}
