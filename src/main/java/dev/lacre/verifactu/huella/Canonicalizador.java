package dev.lacre.verifactu.huella;

import dev.lacre.verifactu.registro.CamposDeHuella;
import dev.lacre.verifactu.registro.RegistroAnterior;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Produce la cadena canónica sobre la que se calcula la huella: orden de campos, separadores y
 * normalización de valores.
 */
public interface Canonicalizador {

    String canonicalizar(CamposDeHuella campos, Optional<RegistroAnterior> registroAnterior,
                         OffsetDateTime fechaHoraHusoGenRegistro);
}
