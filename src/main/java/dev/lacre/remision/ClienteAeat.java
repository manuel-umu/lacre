package dev.lacre.remision;

import dev.lacre.identidad.ObligadoTributario;

import java.util.List;

/**
 * Puerto hacia el servicio web de la AEAT.
 * <p>
 * Es puerto de verdad y no una interfaz de adorno: la implementación real habla SOAP sobre TLS
 * mutuo con el certificado del obligado, y los tests hablan con WireMock. Sin esta frontera, el
 * despachador de la Fase 6.3 no se podría probar sin red.
 */
public interface ClienteAeat {

    /**
     * Remite un lote de registros ya serializados, por cuenta del obligado.
     *
     * @param registros fragmentos XML tal y como se guardaron, de 1 a 1000
     * @throws RemisionFallidaException si no se pudo hablar con la AEAT o si respondió algo que no
     *         se puede interpretar. Es distinto de que rechace: un rechazo viene dentro de la
     *         respuesta y significa que la AEAT sí contestó.
     */
    RespuestaRemision remitir(ObligadoTributario obligado, List<String> registros);
}
