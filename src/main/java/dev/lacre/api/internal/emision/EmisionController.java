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
 * Lo que el ERP llama al expedir o anular una factura.
 * <p>
 * La versión va en la ruta. A partir de aquí romper el contrato cuesta dinero ajeno, y un
 * {@code /v1} se ve de un vistazo en un {@code curl}, cosa que una negociación por cabecera no.
 * <p>
 * <strong>{@code Idempotency-Key} es obligatoria</strong> en los dos. Podría admitirse su
 * ausencia y tratar la petición como única, pero entonces el camino fácil sería el inseguro: el
 * integrador que no lee la documentación es exactamente el que duplicará registros al
 * reintentar. Se exige desde el primer día para que no haya una versión del contrato en la que
 * fuera opcional.
 * <p>
 * Comparte el prefijo {@code /v1/registros} con {@code consulta.EstadoController}, que sirve el
 * {@code GET}. Son dos clases porque emitir y consultar son dos conceptos, no porque sean dos
 * verbos: la escritura arrastra transacción, cerrojo e idempotencia, y la lectura no arrastra
 * nada.
 */
@RestController
@RequestMapping("/v1/registros")
class EmisionController {

    private final Emisiones emisiones;

    EmisionController(Emisiones emisiones) {
        this.emisiones = emisiones;
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
}
