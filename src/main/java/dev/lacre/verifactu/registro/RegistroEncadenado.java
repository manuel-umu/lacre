package dev.lacre.verifactu.registro;

import dev.lacre.shared.Huella;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro con su huella calculada y su enlace al anterior. La posición en la cadena la asigna
 * la persistencia.
 *
 * @param registroAnterior         vacío en el primer registro de la cadena
 * @param fechaHoraHusoGenRegistro con huso horario y truncada a segundos
 */
public record RegistroEncadenado(
        DatosRegistro datos,
        Optional<RegistroAnterior> registroAnterior,
        OffsetDateTime fechaHoraHusoGenRegistro,
        Huella huella) {

    public RegistroEncadenado {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(registroAnterior, "registroAnterior");
        Objects.requireNonNull(fechaHoraHusoGenRegistro, "fechaHoraHusoGenRegistro");
        Objects.requireNonNull(huella, "huella");
    }
}
