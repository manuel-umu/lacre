package dev.lacre.api.internal;

import dev.lacre.verifactu.consulta.VerificacionDeCadena;

import java.util.List;

/**
 * Veredicto sobre la cadena entera de un obligado.
 * <p>
 * {@code alcance} no es decoración y no debe quitarse: hoy vale {@code ENLACES}, que significa
 * que se ha comprobado que cada eslabón enlaza con el anterior y que no falta ninguno,
 * <strong>pero que no se ha recalculado ninguna huella</strong>. Sin ese campo, un
 * {@code "intacta": true} prometería una verificación que no se ha hecho, que es peor que no
 * ofrecer el endpoint.
 *
 * @param roturas vacía si la cadena encaja; en orden de posición
 */
public record RespuestaVerificacion(
        String nifObligado, long registros, boolean intacta, String alcance, List<Rotura> roturas) {

    public record Rotura(long posicion, String motivo) {
    }

    static RespuestaVerificacion de(String nif, VerificacionDeCadena verificacion) {
        return new RespuestaVerificacion(
                nif,
                verificacion.registros(),
                verificacion.intacta(),
                verificacion.alcance().name(),
                verificacion.roturas().stream()
                        .map(rotura -> new Rotura(rotura.posicion(), rotura.motivo().name()))
                        .toList());
    }
}
