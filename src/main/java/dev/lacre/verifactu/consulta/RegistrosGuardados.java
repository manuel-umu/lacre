package dev.lacre.verifactu.consulta;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de registros ya guardados: uno por identificador, o la cadena entera de un obligado.
 */
public interface RegistrosGuardados {

    Optional<RegistroGuardado> porId(UUID registroId);

    /** Recorre la cadena entera del obligado. Una cadena vacía está íntegra. */
    VerificacionDeCadena verificarCadenaDe(UUID obligadoId);

    /** Posición del último registro de cada obligado con cadena; los que no tienen no aparecen. */
    Map<UUID, Long> ultimaPosicionDeCadaObligado();
}
