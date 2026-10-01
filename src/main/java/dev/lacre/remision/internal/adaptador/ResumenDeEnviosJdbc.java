package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnviosDeObligado;
import dev.lacre.remision.ResumenDeEnvios;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Recuento de los envíos por obligado y estado, en una sola consulta. */
@Component
class ResumenDeEnviosJdbc implements ResumenDeEnvios {

    private final JdbcClient jdbc;

    ResumenDeEnviosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, EnviosDeObligado> porObligado() {
        Map<UUID, EnviosDeObligado> resumen = new HashMap<>();
        jdbc.sql("""
                        select obligado_id,
                               count(*) filter (where estado = 'PENDIENTE')            as pendientes,
                               count(*) filter (where estado = 'ACEPTADO')             as aceptados,
                               count(*) filter (where estado = 'ACEPTADO_CON_ERRORES') as con_errores,
                               count(*) filter (where estado = 'RECHAZADO')            as rechazados,
                               count(*) filter (where estado = 'DUPLICADO')            as duplicados,
                               min(creado_en) filter (where estado = 'PENDIENTE')      as mas_antiguo
                        from envio_registro
                        group by obligado_id
                        """).query(rs -> {
            UUID obligado = rs.getObject("obligado_id", UUID.class);
            resumen.put(
                    obligado,
                    new EnviosDeObligado(
                            obligado,
                            rs.getLong("pendientes"),
                            rs.getLong("aceptados"),
                            rs.getLong("con_errores"),
                            rs.getLong("rechazados"),
                            rs.getLong("duplicados"),
                            rs.getObject("mas_antiguo", OffsetDateTime.class)));
        });
        return resumen;
    }
}
