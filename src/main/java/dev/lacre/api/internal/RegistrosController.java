package dev.lacre.api.internal;

import dev.lacre.remision.Envios;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * La API que llama el ERP al expedir o anular una factura, y para saber en qué quedó.
 * <p>
 * La versión va en la ruta. A partir de aquí romper el contrato cuesta dinero ajeno, y un
 * {@code /v1} se ve de un vistazo en un {@code curl}, cosa que una negociación por cabecera no.
 * <p>
 * <strong>{@code Idempotency-Key} es obligatoria</strong> en los dos {@code POST}. Podría
 * admitirse su ausencia y tratar la petición como única, pero entonces el camino fácil sería el
 * inseguro: el integrador que no lee la documentación es exactamente el que duplicará registros
 * al reintentar. Se exige desde el primer día para que no haya una versión del contrato en la
 * que fuera opcional.
 * <p>
 * Todavía <strong>sin autenticación</strong>: las credenciales por integrador son la Fase 7.2 y
 * son una decisión de producto que no conviene tomar de tapadillo. Estos endpoints no deben
 * exponerse fuera de la red del cliente hasta entonces, y el de consulta menos que ninguno:
 * devuelve datos de facturación de cualquier obligado a quien tenga el identificador.
 */
@RestController
@RequestMapping("/v1/registros")
class RegistrosController {

    private final Emisiones emisiones;
    private final RegistrosGuardados registros;
    private final Envios envios;

    RegistrosController(Emisiones emisiones, RegistrosGuardados registros, Envios envios) {
        this.emisiones = emisiones;
        this.registros = registros;
        this.envios = envios;
    }

    /**
     * Un 201 significa que el registro está en la cadena y en el outbox, no que la AEAT lo haya
     * aceptado: la remisión es asíncrona y reintentable, porque la norma exige el registro al
     * expedir, no que la AEAT esté disponible.
     */
    @PostMapping("/alta")
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaRegistro alta(@RequestHeader("Idempotency-Key") String clave,
                           @Valid @RequestBody PeticionAlta peticion) {
        return emisiones.emitir(clave, peticion);
    }

    /**
     * Anular <strong>añade</strong> un eslabón a la cadena, no borra el del alta: la cadena es
     * de solo inserción, y el registro de anulación enlaza con el anterior igual que cualquier
     * otro. Por eso devuelve su propio {@code registroId} y su propia huella.
     */
    @PostMapping("/anulacion")
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaRegistro anulacion(@RequestHeader("Idempotency-Key") String clave,
                                @Valid @RequestBody PeticionAnulacion peticion) {
        return emisiones.emitir(clave, peticion);
    }

    /**
     * Dónde quedó el registro y qué contestó la AEAT. Es la otra mitad del 201: allí se responde
     * que está guardado, y aquí en qué acabó su remisión.
     */
    @GetMapping("/{id}")
    EstadoDelRegistro estado(@PathVariable UUID id) {
        RegistroGuardado registro = registros.porId(id)
                .orElseThrow(() -> new RegistroDesconocidoException(id));

        return EstadoDelRegistro.de(registro, envios.findByRegistroId(id).orElse(null));
    }
}
