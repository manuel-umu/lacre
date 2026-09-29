package dev.lacre.api.internal.obligados;

import dev.lacre.identidad.ObligadoTributario;

/** Un obligado tal y como queda tras darlo de alta o cambiar sus datos. */
public record RespuestaObligado(String nif, String nombreRazon, String zonaHoraria) {

    static RespuestaObligado de(ObligadoTributario obligado) {
        return new RespuestaObligado(
                obligado.nif().valor(),
                obligado.nombreRazon(),
                obligado.zonaHoraria().getId());
    }
}
