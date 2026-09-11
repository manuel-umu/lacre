package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.Envios;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Lado de lectura del outbox: qué toca remitir y de quién. */
@Component
class ColaDeEnvios {

    private final JdbcClient jdbc;
    private final Envios envios;

    ColaDeEnvios(JdbcClient jdbc, Envios envios) {
        this.jdbc = jdbc;
        this.envios = envios;
    }

    /** Envíos pendientes de un obligado, para la condición de lote lleno del control de flujo. */
    long pendientesDe(UUID obligadoId) {
        return jdbc.sql("""
                select count(*) from envio_registro
                where obligado_id = :obligado and estado = 'PENDIENTE'
                """)
                .param("obligado", obligadoId)
                .query(Long.class)
                .single();
    }

    /**
     * Los envíos pendientes más antiguos de un obligado, hasta el tope del lote, con
     * {@code for update skip locked} para que varias instancias no se pisen.
     */
    List<UUID> reservarLoteDe(UUID obligadoId, int maximo) {
        return jdbc.sql("""
                select id from envio_registro
                where obligado_id = :obligado and estado = 'PENDIENTE'
                order by creado_en, id
                limit :maximo
                for update skip locked
                """)
                .param("obligado", obligadoId)
                .param("maximo", maximo)
                .query(UUID.class)
                .list();
    }

    List<EnvioRegistro> cargar(List<UUID> ids) {
        List<EnvioRegistro> cargados = new java.util.ArrayList<>();
        envios.findAllById(ids).forEach(cargados::add);
        return cargados;
    }

    /**
     * Suma un intento a los envíos que siguen pendientes. Va en la transacción de quien llama:
     * una transacción nueva se bloquearía con las filas ya tomadas con {@code for update}.
     */
    public void sumarIntento(List<EnvioRegistro> lote) {
        envios.saveAll(lote.stream()
                .filter(envio -> envio.estado() == EstadoEnvio.PENDIENTE)
                .map(EnvioRegistro::otroIntentoFallido)
                .toList());
    }
}
