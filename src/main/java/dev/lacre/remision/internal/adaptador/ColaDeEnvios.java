package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.Envios;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * El lado de lectura del outbox: qué toca remitir y de quién.
 * <p>
 * Va con {@code JdbcClient} y SQL explícito, según la regla de CQRS deliberado de
 * {@code CLAUDE.md}. El repositorio se queda para cargar y guardar el agregado.
 */
@Component
class ColaDeEnvios {

    private final JdbcClient jdbc;
    private final Envios envios;

    ColaDeEnvios(JdbcClient jdbc, Envios envios) {
        this.jdbc = jdbc;
        this.envios = envios;
    }

    /**
     * Cuántos envíos pendientes tiene cada obligado. Sirve para la condición de volumen del
     * control de flujo: con el lote lleno se puede remitir sin esperar el turno.
     */
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
     * Los envíos pendientes más antiguos de un obligado, hasta el tope del lote.
     * <p>
     * {@code for update skip locked} porque habrá más de una instancia: la que no se lleve las
     * filas pasa a lo siguiente en vez de esperar. Se ordenan por fecha de creación para remitir
     * en el mismo orden en que se generaron los registros.
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
     * Suma un intento a envíos que siguen pendientes.
     * <p>
     * <strong>En la transacción de quien llama, y nunca en una nueva.</strong> Se probó con
     * {@code REQUIRES_NEW} —para que la constancia quedara aunque lo de fuera se deshiciera— y
     * <em>cuelga el proceso</em>: el despachador tiene estas mismas filas bloqueadas con
     * {@code for update}, así que una segunda transacción en otra conexión espera a que las
     * suelte, mientras la primera espera a que esta vuelva. No es un interbloqueo que Postgres
     * pueda abortar, porque una de las dos partes espera en el hilo y no en la base de datos:
     * se queda parado indefinidamente.
     * <p>
     * Tampoco hace falta: en el camino de fallo la excepción se captura, así que la transacción
     * de fuera confirma con normalidad.
     */
    public void sumarIntento(List<EnvioRegistro> lote) {
        envios.saveAll(lote.stream()
                .filter(envio -> envio.estado() == EstadoEnvio.PENDIENTE)
                .map(EnvioRegistro::otroIntentoFallido)
                .toList());
    }
}
