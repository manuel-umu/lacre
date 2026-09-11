package dev.lacre.api.internal.emision;

import dev.lacre.api.internal.emision.alta.PeticionAlta;
import dev.lacre.api.internal.emision.anulacion.PeticionAnulacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para emisiones de factura: alta y anulación. Versión en la ruta e
 * {@code Idempotency-Key} obligatoria.
 */
@RestController
@RequestMapping("/v1/registros")
class EmisionController {

    private final Emisiones emisiones;

    EmisionController(Emisiones emisiones) {
        this.emisiones = emisiones;
    }

    /**
     * Un 201 significa que el registro está en la cadena y en el outbox; la remisión es
     * asíncrona.
     */
    @PostMapping("/alta")
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaRegistro alta(@RequestHeader("Idempotency-Key") String clave,
                           @Valid @RequestBody PeticionAlta peticion) {
        return emisiones.emitir(clave, peticion);
    }

    /** Anular añade un eslabón a la cadena; no borra el del alta. */
    @PostMapping("/anulacion")
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaRegistro anulacion(@RequestHeader("Idempotency-Key") String clave,
                                @Valid @RequestBody PeticionAnulacion peticion) {
        return emisiones.emitir(clave, peticion);
    }
}
