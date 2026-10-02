package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnvioDesconocidoException;
import dev.lacre.remision.EnvioEnCursoException;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.Envios;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.OperacionDeEnvios;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apartar y reanudar envíos. Cada acción toma la fila con {@code for update nowait} o la salta
 * con {@code skip locked}, para no esperar ni pisar al despachador mientras remite un lote.
 */
@Component
@Transactional
class OperacionDeEnviosJdbc implements OperacionDeEnvios {

    /** SQLState de PostgreSQL cuando {@code for update nowait} encuentra la fila bloqueada. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final JdbcClient jdbc;
    private final Envios envios;

    OperacionDeEnviosJdbc(JdbcClient jdbc, Envios envios) {
        this.jdbc = jdbc;
        this.envios = envios;
    }

    @Override
    public void apartar(UUID obligadoId, UUID envioId) {
        cambiar(obligadoId, envioId, EnvioRegistro::apartado);
    }

    @Override
    public void reanudar(UUID obligadoId, UUID envioId) {
        cambiar(obligadoId, envioId, EnvioRegistro::reanudado);
    }

    @Override
    public int apartarPendientesDe(UUID obligadoId) {
        return cambiarTodos(obligadoId, EstadoEnvio.PENDIENTE, EstadoEnvio.APARTADO);
    }

    @Override
    public int reanudarApartadosDe(UUID obligadoId) {
        return cambiarTodos(obligadoId, EstadoEnvio.APARTADO, EstadoEnvio.PENDIENTE);
    }

    private void cambiar(UUID obligadoId, UUID envioId, UnaryOperator<EnvioRegistro> transicion) {
        boolean existe;
        try {
            existe = jdbc.sql("""
                            select id from envio_registro
                            where id = :id and obligado_id = :obligado
                            for update nowait
                            """)
                    .param("id", envioId)
                    .param("obligado", obligadoId)
                    .query(UUID.class)
                    .optional()
                    .isPresent();
        } catch (DataAccessException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                throw new EnvioEnCursoException(envioId);
            }
            throw e;
        }
        if (!existe) {
            throw new EnvioDesconocidoException(envioId);
        }
        envios.save(transicion.apply(envios.findById(envioId).orElseThrow()));
    }

    private int cambiarTodos(UUID obligadoId, EstadoEnvio desde, EstadoEnvio hacia) {
        return jdbc.sql("""
                        update envio_registro set estado = :hacia, version = version + 1
                        where id in (
                            select id from envio_registro
                            where obligado_id = :obligado and estado = :desde
                            for update skip locked)
                        """)
                .param("obligado", obligadoId)
                .param("desde", desde.name())
                .param("hacia", hacia.name())
                .update();
    }
}
