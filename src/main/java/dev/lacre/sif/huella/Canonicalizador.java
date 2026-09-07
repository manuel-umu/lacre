package dev.lacre.sif.huella;

import dev.lacre.shared.Huella;
import dev.lacre.sif.registro.DatosRegistroAlta;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Produce la cadena de texto sobre la que se calcula la huella de un registro.
 * <p>
 * Concentra todo lo que depende de la especificación de la AEAT: el orden de los campos, el
 * separador, la normalización de cada valor y cómo se representa la ausencia de huella
 * anterior en el primer registro de la cadena.
 */
public interface Canonicalizador {

    String canonicalizar(DatosRegistroAlta datos, Optional<Huella> huellaAnterior,
                         OffsetDateTime fechaHoraHusoGenRegistro);
}
