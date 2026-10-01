package dev.lacre.consola.internal;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EnviosDeObligado;
import dev.lacre.remision.ResumenDeEnvios;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/** Páginas de la consola: el inicio de sesión, la vista general y el detalle de un obligado. */
@Controller
@RequestMapping("/consola")
class ConsolaController {

    /** Envíos que se listan de cada tipo en el detalle de un obligado. */
    static final int MAXIMO_POR_LISTA = 50;

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

    @GetMapping("/obligados/{nif}")
    String detalle(@PathVariable String nif, Model modelo) {
        ObligadoTributario obligado = obligadoDe(nif);
        FilaObligado fila = FilaObligado.de(
                obligado,
                registros.ultimaPosicionDeCadaObligado().get(obligado.id()),
                envios.porObligado().get(obligado.id()),
                OffsetDateTime.now(reloj));
        List<EnvioRegistro> pendientes = envios.pendientesDe(obligado.id(), MAXIMO_POR_LISTA);
        List<EnvioRegistro> conErrores = envios.conErroresDe(obligado.id(), MAXIMO_POR_LISTA);

        modelo.addAttribute("obligado", obligado);
        modelo.addAttribute("fila", fila);
        modelo.addAttribute("pendientes", conSuRegistro(pendientes, obligado));
        modelo.addAttribute("pendientesSinListar", fila.envios().pendientes() - pendientes.size());
        modelo.addAttribute("conErrores", conSuRegistro(conErrores, obligado));
        return "consola/detalle";
    }

    /** La verificación de la cadena, que recalcula todas sus huellas: solo bajo demanda. */
    @GetMapping("/obligados/{nif}/cadena")
    String verificacion(@PathVariable String nif, Model modelo) {
        modelo.addAttribute(
                "verificacion", registros.verificarCadenaDe(obligadoDe(nif).id()));
        return "consola/detalle :: verificacion";
    }

    private ObligadoTributario obligadoDe(String nif) {
        try {
            return obligados
                    .findByNif(new Nif(nif))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (ValorInvalidoException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private List<FilaEnvio> conSuRegistro(List<EnvioRegistro> lista, ObligadoTributario obligado) {
        Map<UUID, RegistroGuardado> porId =
                registros.porIds(lista.stream().map(EnvioRegistro::registroId).toList()).stream()
                        .collect(Collectors.toMap(RegistroGuardado::id, Function.identity()));
        return lista.stream()
                .map(envio -> new FilaEnvio(envio, porId.get(envio.registroId()), obligado.zonaHoraria()))
                .toList();
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
