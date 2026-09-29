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
 * Controlador REST de verificación de la cadena de un obligado. La respuesta declara su
 * {@code alcance}; ver {@link RespuestaVerificacion}.
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
        ObligadoTributario obligado =
                obligados.findByNif(delObligado).orElseThrow(() -> new ObligadoDesconocidoException(delObligado));

        return RespuestaVerificacion.de(obligado.nif().valor(), registros.verificarCadenaDe(obligado.id()));
    }
}
