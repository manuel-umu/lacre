package dev.lacre.api.internal;

import dev.lacre.api.internal.consulta.RegistroDesconocidoException;
import dev.lacre.api.internal.emision.ClaveIdempotenciaReutilizadaException;
import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.shared.ValorInvalidoException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Errores que un integrador entiende sin leernos el código: qué campo va mal y por qué.
 * <p>
 * Se responde con {@code ProblemDetail} (RFC 9457), que es lo que Spring ya sabe producir y lo
 * que un cliente HTTP moderno espera. Descartado un tipo de error propio: sería el mismo JSON
 * con otro nombre.
 * <p>
 * Además del texto va un {@code codigo} estable. El {@code detail} es para una persona y puede
 * reescribirse; el código es para el {@code switch} del integrador y no cambia.
 * <p>
 * Extiende {@link ResponseEntityExceptionHandler} para heredar el tratamiento de las excepciones
 * de Spring MVC —JSON ilegible, cabecera que falta, método no admitido—, que si no saldrían con
 * un cuerpo distinto al del resto.
 */
@RestControllerAdvice
class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    /**
     * Los datos no forman un registro válido. Casi siempre lo lanza un constructor del dominio
     * citando la regla que se incumple, así que el mensaje se pasa tal cual: reescribirlo aquí
     * perdería la única parte útil.
     */
    @ExceptionHandler(ValorInvalidoException.class)
    ProblemDetail valorInvalido(ValorInvalidoException e) {
        return problema(HttpStatus.BAD_REQUEST, "Datos de facturación no válidos",
                e.getMessage(), "validacion");
    }

    /**
     * 422 y no 404: el recurso al que se llamó existe y el JSON está bien formado; lo que no
     * existe es el obligado que la factura dice tener por emisor. Tampoco 400, porque no hay
     * nada que corregir en el cuerpo: hay que dar de alta al obligado.
     */
    @ExceptionHandler(ObligadoDesconocidoException.class)
    ProblemDetail obligadoDesconocido(ObligadoDesconocidoException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Obligado tributario no dado de alta",
                e.getMessage(), "obligado-desconocido");
    }

    /**
     * Aquí 404 sí es lo correcto, al contrario que con el obligado: lo que identifica el recurso
     * es la ruta, y no hay ningún registro en esa ruta.
     */
    @ExceptionHandler(RegistroDesconocidoException.class)
    ProblemDetail registroDesconocido(RegistroDesconocidoException e) {
        return problema(HttpStatus.NOT_FOUND, "Registro no encontrado",
                e.getMessage(), "registro-desconocido");
    }

    @ExceptionHandler(ClaveIdempotenciaReutilizadaException.class)
    ProblemDetail claveReutilizada(ClaveIdempotenciaReutilizadaException e) {
        return problema(HttpStatus.CONFLICT, "Clave de idempotencia reutilizada",
                e.getMessage(), "clave-idempotencia-reutilizada");
    }

    /**
     * Las violaciones de Bean Validation, campo a campo. La respuesta de serie dice solo
     * «Invalid request content», que obliga a quien integra a adivinar cuál de los veinticinco
     * campos falta.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders cabeceras, HttpStatusCode estado,
            WebRequest peticion) {

        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();

        ProblemDetail cuerpo = problema(HttpStatus.BAD_REQUEST, "Petición mal formada",
                "La petición incumple " + errores.size() + " restricción(es) del contrato.",
                "validacion");
        cuerpo.setProperty("errores", errores);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle,
                                          String codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return problema;
    }
}
