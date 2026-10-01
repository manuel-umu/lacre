package dev.lacre.consola.internal;

import dev.lacre.identidad.Obligados;
import dev.lacre.remision.EnviosDeObligado;
import dev.lacre.remision.ResumenDeEnvios;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Páginas de la consola: el inicio de sesión y la vista general de los obligados. */
@Controller
@RequestMapping("/consola")
class ConsolaController {

    private final Obligados obligados;
    private final RegistrosGuardados registros;
    private final ResumenDeEnvios envios;
    private final Clock reloj;

    ConsolaController(Obligados obligados, RegistrosGuardados registros, ResumenDeEnvios envios, Clock reloj) {
        this.obligados = obligados;
        this.registros = registros;
        this.envios = envios;
        this.reloj = reloj;
    }

    @GetMapping("/entrar")
    String entrar() {
        return "consola/entrar";
    }

    @GetMapping
    String inicio(Model modelo) {
        modelo.addAttribute("filas", filas());
        return "consola/inicio";
    }

    /** Solo la tabla, para que htmx la refresque. */
    @GetMapping("/obligados")
    String tabla(Model modelo) {
        modelo.addAttribute("filas", filas());
        return "consola/inicio :: tabla";
    }

    private List<FilaObligado> filas() {
        Map<UUID, Long> posiciones = registros.ultimaPosicionDeCadaObligado();
        Map<UUID, EnviosDeObligado> porObligado = envios.porObligado();
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        return StreamSupport.stream(obligados.findAll().spliterator(), false)
                .map(obligado ->
                        FilaObligado.de(obligado, posiciones.get(obligado.id()), porObligado.get(obligado.id()), ahora))
                .sorted(Comparator.comparing(FilaObligado::nif))
                .toList();
    }
}
