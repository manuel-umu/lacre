package dev.lacre.remision;

import java.util.UUID;

/** Acciones del operador sobre los envíos pendientes de un obligado: apartarlos del despacho y reanudarlos. */
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
}
