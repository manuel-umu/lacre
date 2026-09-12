package dev.lacre.api.internal.emision;

import dev.lacre.verifactu.emision.AnomaliaPrevia;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta a un alta o anulación aceptadas: identificador, posición y huella del registro. No
 * implica que la AEAT lo haya aceptado; el desenlace se consulta en {@code GET /v1/registros/{id}}.
 *
 * @param avisos anomalías de la cadena detectadas al registrar; vacío si no hubo ninguna
 */
public record RespuestaRegistro(UUID registroId, long posicion, String huella, List<Aviso> avisos) {

    public RespuestaRegistro {
        avisos = List.copyOf(avisos);
    }

    /**
     * Anomalía de la comprobación previa del art. 7.i de la OM HAC/1177/2024. No impidió
     * registrar: la facturación no debe interrumpirse, pero la cadena necesita atención.
     */
    public record Aviso(String codigo, String mensaje) {

        static Aviso de(AnomaliaPrevia anomalia) {
            return new Aviso(anomalia.name(), anomalia.mensaje());
        }
    }
}
