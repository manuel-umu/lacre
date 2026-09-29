package dev.lacre.api.internal.obligados;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el alta de obligados tributarios. Idempotente por NIF: 201 si lo crea y
 * 200 si ya existía.
 */
@RestController
class ObligadosController {

    private final AltasDeObligados altas;

    ObligadosController(AltasDeObligados altas) {
        this.altas = altas;
    }

    @PutMapping("/v1/obligados/{nif}")
    ResponseEntity<RespuestaObligado> alta(@PathVariable String nif, @Valid @RequestBody PeticionObligado peticion) {
        AltasDeObligados.Alta alta = altas.darDeAlta(nif, peticion);
        return ResponseEntity.status(alta.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(RespuestaObligado.de(alta.obligado()));
    }
}
