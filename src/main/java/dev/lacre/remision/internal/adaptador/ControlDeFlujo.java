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
 * El mecanismo de control de flujo del art. 16.2 de la OM HAC/1177/2024.
 * <p>
 * La regla, literal: el tiempo de espera empieza en 60 segundos, la AEAT lo actualiza en cada
 * respuesta, y para el siguiente envío hay que esperar a que pasen esos segundos <em>desde el
 * anterior envío</em> <strong>o</strong> a acumular el máximo de registros por envío, lo que
 * ocurra primero.
 * <p>
 * <strong>El ámbito es por obligado, y es una interpretación.</strong> El artículo habla del
 * «sistema informático», y lacre no lo es: es un componente de facturación, y el SIF es el ERP
 * del cliente más nosotros. Se elige por obligado porque cada uno presenta con su propio
 * certificado —ante la AEAT son autenticaciones distintas— y porque el ámbito por instalación
 * haría inusable el producto justo en el escenario para el que se vende: un ERP con cientos de
 * obligados y un único turno por minuto. Está pendiente de confirmar contra el Portal de Pruebas,
 * y va unido a la otra pregunta abierta: si la AEAT cuenta por lo que se declara en el bloque
 * {@code SistemaInformatico}, el ámbito es eso.
 * <p>
 * Cambiar de ámbito es cambiar la clave de esta tabla, no rehacer nada.
 */
@Component
class ControlDeFlujo {

    private final JdbcClient jdbc;

    ControlDeFlujo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Obligados con algo pendiente de remitir. Quién tiene turno de verdad lo decide
     * {@link #reservarTurno}, en una sola sentencia y sin margen para que dos instancias se
     * cuelen: preguntarlo aquí y comprobarlo allí dejaría una ventana entre las dos.
     */
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
     * Marca el turno como consumido y <strong>confirma antes de enviar</strong>. Devuelve
     * {@code false} si otra instancia se lo llevó primero.
     * <p>
     * {@code REQUIRES_NEW} es deliberado: esta escritura tiene que quedar confirmada aunque el
     * envío posterior falle. Si se deshiciera con él, dos instancias podrían remitir seguidas y
     * saltarse el tiempo de espera, que es justo lo que la norma prohíbe.
     *
     * @param loteLleno si ya se han acumulado los registros que caben en un envío, en cuyo caso
     *                  se sale sin esperar: la letra c) del artículo dice «lo que ocurra primero»
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

    /**
     * El valor que devuelve la AEAT manda sobre el que teníamos. No es una sugerencia:
     * saltárselo es motivo de rechazo.
     */
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
