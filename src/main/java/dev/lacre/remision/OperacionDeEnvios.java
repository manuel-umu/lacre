package dev.lacre.remision;

import java.util.UUID;

/**
 * Acciones del operador sobre los envíos de un obligado: apartar y reanudar los pendientes, y dar
 * por atendidos los errores de la AEAT.
 */
public interface OperacionDeEnvios {

    /**
     * @throws EnvioDesconocidoException si el envío no existe o es de otro obligado
     * @throws EnvioEnCursoException si está en un lote que se remite ahora mismo
     * @throws EnvioYaResueltoException si no está pendiente
     */
    void apartar(UUID obligadoId, UUID envioId);

    /**
     * @throws EnvioDesconocidoException si el envío no existe o es de otro obligado
     * @throws EnvioEnCursoException si su fila está bloqueada
     * @throws EnvioYaResueltoException si no está apartado
     */
    void reanudar(UUID obligadoId, UUID envioId);

    /** Aparta los pendientes del obligado que no están en un lote en vuelo; devuelve cuántos. */
    int apartarPendientesDe(UUID obligadoId);

    /** Devuelve al despacho todos los apartados del obligado; devuelve cuántos. */
    int reanudarApartadosDe(UUID obligadoId);

    /**
     * Da por atendido un rechazado o aceptado con errores, que deja de listarse entre los errores.
     *
     * @throws EnvioDesconocidoException si el envío no existe o es de otro obligado
     * @throws EnvioYaResueltoException si no tiene un error de la AEAT o ya estaba atendido
     */
    void marcarAtendido(UUID obligadoId, UUID envioId, String operador);
}
