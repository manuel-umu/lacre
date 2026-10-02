package dev.lacre.verifactu.consulta;

import dev.lacre.verifactu.registro.IdFactura;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de registros ya guardados: uno por identificador, o la cadena entera de un obligado.
 */
public interface RegistrosGuardados {

    Optional<RegistroGuardado> porId(UUID registroId);

    /** Los que existen de entre esos identificadores, en cualquier orden. */
    List<RegistroGuardado> porIds(Collection<UUID> registroIds);

    /** Recorre la cadena entera del obligado. Una cadena vacía está íntegra. */
    VerificacionDeCadena verificarCadenaDe(UUID obligadoId);

    /** Los registros del obligado de facturas expedidas en ese mes, con la fecha de operación de su XML. */
    List<RegistroDeFactura> deFacturasExpedidasEn(UUID obligadoId, YearMonth mes);

    /** Los registros del obligado de esas facturas, con la fecha de operación de su XML. */
    List<RegistroDeFactura> deFacturas(UUID obligadoId, Collection<IdFactura> facturas);

    /** Posición del último registro de cada obligado con cadena; los que no tienen no aparecen. */
    Map<UUID, Long> ultimaPosicionDeCadaObligado();
}
