package dev.lacre.api.internal.consulta;

import dev.lacre.verifactu.consulta.VerificacionDeCadena;
import java.util.List;

/**
 * Veredicto sobre la cadena de un obligado. {@code alcance} indica hasta dónde llega la
 * comprobación: {@code HUELLAS} recalcula además la huella de cada registro.
 *
 * @param roturas vacía si la cadena encaja; en orden de posición
 */
public record RespuestaVerificacion(
        String nifObligado, long registros, boolean intacta, String alcance, List<Rotura> roturas) {

    public record Rotura(long posicion, String motivo) {}

    static RespuestaVerificacion de(String nif, VerificacionDeCadena verificacion) {
        return new RespuestaVerificacion(
                nif,
                verificacion.registros(),
                verificacion.intacta(),
                verificacion.alcance().name(),
                verificacion.roturas().stream()
                        .map(rotura ->
                                new Rotura(rotura.posicion(), rotura.motivo().name()))
                        .toList());
    }
}
