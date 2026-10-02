package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EnviosDeObligado;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.ResumenDeEnvios;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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
                               count(*) filter (where estado = 'APARTADO')             as apartados,
                               count(*) filter (where atendido_en is not null)         as atendidos,
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
                            rs.getLong("apartados"),
                            rs.getLong("atendidos"),
                            rs.getObject("mas_antiguo", OffsetDateTime.class)));
        });
        return resumen;
    }

    @Override
    public List<EnvioRegistro> pendientesDe(UUID obligadoId, int maximo) {
        return enEstado(obligadoId, EstadoEnvio.PENDIENTE, maximo);
    }

    @Override
    public List<EnvioRegistro> apartadosDe(UUID obligadoId, int maximo) {
        return enEstado(obligadoId, EstadoEnvio.APARTADO, maximo);
    }

    private List<EnvioRegistro> enEstado(UUID obligadoId, EstadoEnvio estado, int maximo) {
        return jdbc.sql(SELECT_ENVIO + """
                        where obligado_id = :obligado and estado = :estado
                        order by creado_en, id
                        limit :maximo
                        """)
                .param("obligado", obligadoId)
                .param("estado", estado.name())
                .param("maximo", maximo)
                .query(ResumenDeEnviosJdbc::envio)
                .list();
    }

    @Override
    public List<EnvioRegistro> conErroresDe(UUID obligadoId, int maximo) {
        return jdbc.sql(SELECT_ENVIO + """
                        where obligado_id = :obligado and estado in ('RECHAZADO', 'ACEPTADO_CON_ERRORES')
                          and atendido_en is null
                        order by enviado_en desc, id
                        limit :maximo
                        """)
                .param("obligado", obligadoId)
                .param("maximo", maximo)
                .query(ResumenDeEnviosJdbc::envio)
                .list();
    }

    private static final String SELECT_ENVIO = """
            select id, registro_id, obligado_id, estado, creado_en, enviado_en, codigo_error,
                   descripcion_error, intentos, version
            from envio_registro
            """;

    private static EnvioRegistro envio(ResultSet rs, int fila) throws SQLException {
        return new EnvioRegistro(
                rs.getObject("id", UUID.class),
                rs.getObject("registro_id", UUID.class),
                rs.getObject("obligado_id", UUID.class),
                EstadoEnvio.valueOf(rs.getString("estado")),
                rs.getObject("creado_en", OffsetDateTime.class),
                rs.getObject("enviado_en", OffsetDateTime.class),
                rs.getObject("codigo_error", Integer.class),
                rs.getString("descripcion_error"),
                rs.getInt("intentos"),
                rs.getLong("version"));
    }
}
