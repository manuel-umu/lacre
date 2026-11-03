package dev.lacre.api.internal.emision;

import dev.lacre.verifactu.emision.AnomaliaPrevia;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta a un alta o anulación aceptadas: identificador, posición y huella del registro. No
 * implica que la AEAT lo haya aceptado; el desenlace se consulta en {@code GET /v1/registros/{id}}.
 *
 * @param urlQr  la que va dentro del código QR de la factura; nula en la anulación, que no se
 *               imprime
 * @param avisos anomalías de la cadena y descuadres de totales detectados al registrar; vacío si no
 *               hubo ninguno
 */
public record RespuestaRegistro(UUID registroId, long posicion, String huella, String urlQr, List<Aviso> avisos) {

    public RespuestaRegistro {
        avisos = List.copyOf(avisos);
    }

    /**
     * Anomalía de la comprobación previa del art. 7.i de la OM HAC/1177/2024, o descuadre de los
     * totales del registro. No impidió registrar: la facturación no debe interrumpirse.
     */
    public record Aviso(String codigo, String mensaje) {

        static Aviso de(AnomaliaPrevia anomalia) {
            return new Aviso(anomalia.name(), anomalia.mensaje());
        }

        static Aviso de(Descuadre descuadre) {
            return new Aviso(descuadre.name(), descuadre.mensaje());
        }

        static Aviso deCodigo(String codigo) {
            return Arrays.stream(Descuadre.values())
                            .anyMatch(descuadre -> descuadre.name().equals(codigo))
                    ? de(Descuadre.valueOf(codigo))
                    : de(AnomaliaPrevia.valueOf(codigo));
        }
    }
}
