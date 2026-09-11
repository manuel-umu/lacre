package dev.lacre.api.internal.consulta;

import dev.lacre.remision.Envios;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * En qué quedó un registro: dónde está en la cadena y qué contestó la AEAT.
 * <p>
 * Es la otra mitad del 201 de {@code emision.EmisionController}: allí se responde que está
 * guardado, y aquí en qué acabó su remisión, que es asíncrona.
 */
@RestController
@RequestMapping("/v1/registros")
class EstadoController {

    private final RegistrosGuardados registros;
    private final Envios envios;

    EstadoController(RegistrosGuardados registros, Envios envios) {
        this.registros = registros;
        this.envios = envios;
    }

    @GetMapping("/{id}")
    EstadoDelRegistro estado(@PathVariable UUID id) {
        RegistroGuardado registro = registros.porId(id)
                .orElseThrow(() -> new RegistroDesconocidoException(id));

        return EstadoDelRegistro.de(registro, envios.findByRegistroId(id).orElse(null));
    }
}
