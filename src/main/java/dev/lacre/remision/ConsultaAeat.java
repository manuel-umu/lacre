package dev.lacre.remision;

import dev.lacre.identidad.ObligadoTributario;
import java.time.YearMonth;

/** Puerto hacia la consulta de registros presentados del servicio web de la AEAT. */
public interface ConsultaAeat {

    /**
     * Los registros que la AEAT tiene del obligado imputados a ese mes, recorriendo sus páginas.
     *
     * @throws RemisionFallidaException si no se pudo hablar con la AEAT, su respuesta no se pudo
     *         interpretar o rechazó la consulta
     */
    ResultadoConsulta consultar(ObligadoTributario obligado, YearMonth periodo);
}
