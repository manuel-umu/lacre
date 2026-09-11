package dev.lacre.api.internal.consulta;

import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificación de la cadena de un obligado.
 * <p>
 * Va bajo {@code /v1/obligados/{nif}} y no bajo {@code /v1/registros} porque el recurso que se
 * verifica es la cadena entera, que es del obligado: no hay un registro del que colgarla.
 * <p>
 * Lo que responde <strong>no es una verificación completa</strong>, y la respuesta lo dice en su
 * campo {@code alcance}. Ver {@link RespuestaVerificacion}.
 */
@RestController
@RequestMapping("/v1/obligados")
class CadenaController {

    private final Obligados obligados;
    private final RegistrosGuardados registros;

    CadenaController(Obligados obligados, RegistrosGuardados registros) {
        this.obligados = obligados;
        this.registros = registros;
    }

    @GetMapping("/{nif}/cadena")
    RespuestaVerificacion cadena(@PathVariable String nif) {
        Nif delObligado = new Nif(nif);
        ObligadoTributario obligado = obligados.findByNif(delObligado)
                .orElseThrow(() -> new ObligadoDesconocidoException(delObligado));

        return RespuestaVerificacion.de(
                obligado.nif().valor(), registros.verificarCadenaDe(obligado.id()));
    }
}
