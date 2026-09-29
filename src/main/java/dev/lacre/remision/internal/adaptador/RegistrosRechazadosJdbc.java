package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EstadoEnvio;
import dev.lacre.verifactu.emision.RegistrosRechazados;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Registros rechazados por la AEAT, según el desenlace de su envío en el outbox. */
@Component
class RegistrosRechazadosJdbc implements RegistrosRechazados {

    private final JdbcClient jdbc;

    RegistrosRechazadosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> entre(Collection<UUID> registroIds) {
        if (registroIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                        select registro_id from envio_registro
                        where estado = :rechazado and registro_id in (:ids)
                        """)
                .param("rechazado", EstadoEnvio.RECHAZADO.name())
                .param("ids", registroIds)
                .query(UUID.class)
                .list());
    }
}
