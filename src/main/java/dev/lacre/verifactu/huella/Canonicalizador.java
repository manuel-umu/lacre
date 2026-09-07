package dev.lacre.verifactu.huella;

import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.RegistroAnterior;

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

    String canonicalizar(DatosRegistro datos, Optional<RegistroAnterior> registroAnterior,
                         OffsetDateTime fechaHoraHusoGenRegistro);
}
