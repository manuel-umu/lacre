package dev.lacre.api.internal;

import dev.lacre.api.internal.consulta.RegistroDesconocidoException;
import dev.lacre.api.internal.emision.ClaveIdempotenciaReutilizadaException;
import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.shared.ValorInvalidoException;
import java.util.List;
import java.util.Map;
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

/**
 * Traduce las excepciones a {@code ProblemDetail} (RFC 9457) con un {@code codigo} estable para
 * el integrador. Solo atiende a los controladores de la API.
 */
@RestControllerAdvice(basePackages = "dev.lacre.api")
class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    /**
     * Los datos no forman un registro válido; el mensaje del dominio se devuelve tal cual, y el
     * código de la AEAT cuando es una validación suya.
     */
    @ExceptionHandler(ValorInvalidoException.class)
    ProblemDetail valorInvalido(ValorInvalidoException e) {
        ProblemDetail problema =
                problema(HttpStatus.BAD_REQUEST, "Datos de facturación no válidos", e.getMessage(), "validacion");
        if (e instanceof ReglaAeatIncumplidaException regla) {
            problema.setProperty("codigoAeat", regla.codigoAeat());
        }
        return problema;
    }

    /** 422: el cuerpo está bien formado, pero el obligado emisor no está dado de alta. */
    @ExceptionHandler(ObligadoDesconocidoException.class)
    ProblemDetail obligadoDesconocido(ObligadoDesconocidoException e) {
        return problema(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Obligado tributario no dado de alta",
                e.getMessage(),
                "obligado-desconocido");
    }

    /** 404: no hay ningún registro en esa ruta. */
    @ExceptionHandler(RegistroDesconocidoException.class)
    ProblemDetail registroDesconocido(RegistroDesconocidoException e) {
        return problema(HttpStatus.NOT_FOUND, "Registro no encontrado", e.getMessage(), "registro-desconocido");
    }

    @ExceptionHandler(ClaveIdempotenciaReutilizadaException.class)
    ProblemDetail claveReutilizada(ClaveIdempotenciaReutilizadaException e) {
        return problema(
                HttpStatus.CONFLICT,
                "Clave de idempotencia reutilizada",
                e.getMessage(),
                "clave-idempotencia-reutilizada");
    }

    /** Violaciones de Bean Validation, campo a campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {

        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();

        ProblemDetail cuerpo = problema(
                HttpStatus.BAD_REQUEST,
                "Petición mal formada",
                "La petición incumple " + errores.size() + " restricción(es) del contrato.",
                "validacion");
        cuerpo.setProperty("errores", errores);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle, String codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return problema;
    }
}
