package dev.lacre.sif.registro;

import dev.lacre.shared.Huella;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro de alta con su huella ya calculada y su enlace al registro anterior.
 * <p>
 * No lleva posición dentro de la cadena: esa la asigna la capa de persistencia bajo el
 * cerrojo del obligado, y el dominio puro no tiene forma de generarla.
 *
 * @param registroAnterior          vacío si es el primer registro de la cadena
 * @param fechaHoraHusoGenRegistro  con huso horario y truncado a segundos, porque así entra
 *                                  en el cálculo de la huella y así se serializa al XML
 */
public record RegistroEncadenado(
        DatosRegistroAlta datos,
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
