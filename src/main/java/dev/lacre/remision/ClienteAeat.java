package dev.lacre.remision;

import dev.lacre.identidad.ObligadoTributario;
import java.util.List;

/** Puerto hacia el servicio web de la AEAT. */
public interface ClienteAeat {

    /**
     * Remite un lote de registros ya serializados por cuenta del obligado.
     *
     * @param registros fragmentos XML tal y como se guardaron, de 1 a 1000
     * @throws RemisionFallidaException si no se pudo hablar con la AEAT o su respuesta no se pudo
     *         interpretar; un rechazo, en cambio, viene dentro de la respuesta
     */
    RespuestaRemision remitir(ObligadoTributario obligado, List<String> registros);
}
