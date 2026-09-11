package dev.lacre.remision.internal.adaptador;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Control de flujo del art. 16.2 de la OM HAC/1177/2024, por obligado: el siguiente envío sale
 * cuando pasa el tiempo de espera desde el anterior o cuando se llena el lote, lo que ocurra
 * primero.
 */
@Component
class ControlDeFlujo {

    private final JdbcClient jdbc;

    ControlDeFlujo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Obligados con envíos pendientes. */
    List<UUID> obligadosConPendientes() {
        return jdbc.sql("""
                select distinct obligado_id from envio_registro
                where estado = 'PENDIENTE'
                order by obligado_id
                """)
                .query(UUID.class)
                .list();
    }

    /**
     * Marca el turno como consumido en transacción propia, antes de enviar. Devuelve
     * {@code false} si otra instancia se lo llevó primero.
     *
     * @param loteLleno si el lote está completo, se sale sin esperar
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reservarTurno(UUID obligadoId, OffsetDateTime ahora, boolean loteLleno) {
        int filas = jdbc.sql("""
                insert into control_flujo_envio (obligado_id, ultimo_envio, espera_segundos, version)
                values (:obligado, :ahora, 60, 0)
                on conflict (obligado_id) do update
                    set ultimo_envio = :ahora,
                        version = control_flujo_envio.version + 1
                    where control_flujo_envio.ultimo_envio is null
                       or :loteLleno
                       or :ahora >= control_flujo_envio.ultimo_envio
                            + make_interval(secs => control_flujo_envio.espera_segundos)
                """)
                .param("obligado", obligadoId)
                .param("ahora", ahora)
                .param("loteLleno", loteLleno)
                .update();
        return filas > 0;
    }

    /** El tiempo de espera que devuelve la AEAT sustituye al anterior. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void actualizarEspera(UUID obligadoId, Duration espera) {
        jdbc.sql("""
                update control_flujo_envio
                set espera_segundos = :segundos, version = version + 1
                where obligado_id = :obligado
                """)
                .param("segundos", (int) Math.max(0, espera.toSeconds()))
                .param("obligado", obligadoId)
                .update();
    }
}
