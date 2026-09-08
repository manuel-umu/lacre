package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.evento.RegistroCreado;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Añade registros a la cadena de un obligado, en orden y sin bifurcaciones.
 * <p>
 * Es el caso de uso, y vive en el adaptador y no en el núcleo a propósito: la librería que se
 * publica ofrece el motor de huella y la serialización, mientras que decidir la posición y
 * escribir en una base de datos es asunto de la aplicación. Ver ADR 0002.
 * <p>
 * <strong>Toda la operación va en una transacción</strong> y empieza tomando un cerrojo
 * consultivo por obligado. Sin él, dos hilos podrían leer el mismo último registro y calcular
 * la misma posición: uno de los dos fallaría por la restricción {@code UNIQUE}, que es la
 * tercera capa de defensa, pero perdiendo la factura. El cerrojo evita llegar a eso.
 * <p>
 * Es el adaptador, no el núcleo, quien conoce a {@code identidad}: la zona horaria con la que se
 * fecha el registro <strong>entra en la huella</strong> y es dato del obligado, así que se
 * resuelve aquí en vez de confiar en que quien llame acierte con el parámetro. El núcleo
 * publicable sigue sin conocer a ningún módulo, y {@code ArquitecturaTest} lo vigila.
 */
@Service
public class CadenaDeRegistros {

    private static final Logger log = LoggerFactory.getLogger(CadenaDeRegistros.class);

    private final JdbcClient jdbc;
    private final RegistroRepository repositorio;
    private final Obligados obligados;
    private final EncadenadorRegistros encadenador;
    private final Supplier<UUID> generadorDeIdentificadores;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    CadenaDeRegistros(JdbcClient jdbc, RegistroRepository repositorio, Obligados obligados,
                      EncadenadorRegistros encadenador, Supplier<UUID> generadorDeIdentificadores,
                      ApplicationEventPublisher eventos, Clock reloj) {
        this.jdbc = jdbc;
        this.repositorio = repositorio;
        this.obligados = obligados;
        this.encadenador = encadenador;
        this.generadorDeIdentificadores = generadorDeIdentificadores;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public RegistroFacturacion anadir(UUID obligadoId, DatosRegistro datos) {
        ObligadoTributario obligado = obligados.findById(obligadoId)
                .orElseThrow(() -> new ObligadoDesconocidoException(obligadoId));

        serializarLaCadenaDe(obligadoId);

        List<Enlace> cola = losDosUltimosDe(obligadoId);
        Optional<Enlace> ultimo = cola.stream().findFirst();
        comprobacionPreviaDelArticulo7i(obligadoId, cola);

        RegistroEncadenado encadenado = encadenador.encadenar(
                datos, ultimo.map(Enlace::comoAnterior), obligado.zonaHoraria());
        long posicion = ultimo.map(enlace -> enlace.posicion() + 1).orElse(1L);

        RegistroFacturacion guardado = repositorio.save(RegistroFacturacion.de(
                generadorDeIdentificadores.get(), obligadoId, posicion, encadenado,
                EscritorRegistro.escribir(encadenado)));

        // Publicado aquí y no con @DomainEvents en el agregado: RegistroFacturacion es un record
        // inmutable, y ese mecanismo pide además un método que limpie los eventos ya publicados.
        // Los oyentes son síncronos, así que corren antes de que esta transacción confirme.
        eventos.publishEvent(new RegistroCreado(guardado.id(), obligadoId));
        return guardado;
    }

    /**
     * Art. 7.i de la OM HAC/1177/2024. <strong>Avisa, no impide emitir</strong>: la FAQ 15 dice
     * que «será preciso generar el siguiente RF, ya que la facturación por este motivo NUNCA
     * debe interrumpirse». Lanzar aquí sería incumplir la norma, no ser más estricto.
     * <p>
     * El primer registro de la cadena está exento, y con un solo registro guardado no hay
     * eslabón que comprobar: el suyo se comprobó cuando se generó.
     *
     * @implNote TODO Cuando exista la API (Fase 7.3), estas anomalías deben viajar también en la
     * respuesta al integrador, no solo al log.
     */
    private void comprobacionPreviaDelArticulo7i(UUID obligadoId, List<Enlace> cola) {
        if (cola.isEmpty()) {
            return;
        }
        Enlace ultimo = cola.getFirst();
        Enlace penultimo = cola.size() > 1 ? cola.get(1) : null;

        Set<ComprobacionPrevia.Anomalia> anomalias =
                ComprobacionPrevia.comprobar(ultimo, penultimo, OffsetDateTime.now(reloj));

        if (!anomalias.isEmpty()) {
            log.error("Comprobación previa del art. 7.i: la cadena del obligado {} presenta {} "
                            + "en su registro de posición {}. Se emite igualmente, porque la "
                            + "facturación no debe interrumpirse.",
                    obligadoId, anomalias, ultimo.posicion());
        }
    }

    /**
     * Cerrojo consultivo transaccional: se libera solo al terminar la transacción, así que no
     * hay forma de olvidarse de soltarlo. Nada de Redis ni de {@code synchronized}: habrá más
     * de una instancia de la aplicación y un cerrojo en memoria no serviría de nada.
     */
    private void serializarLaCadenaDe(UUID obligadoId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtext(:clave))")
                .param("clave", "cadena:" + obligadoId)
                .query()
                .singleRow();
    }

    /**
     * Lado de lectura: SQL explícito, sin pasar por el agregado.
     * <p>
     * Dos filas y no una: la primera es con la que se encadena el registro nuevo, y la segunda
     * hace falta para la comprobación previa del art. 7.i, que verifica que la primera enlaza
     * bien con ella.
     */
    private List<Enlace> losDosUltimosDe(UUID obligadoId) {
        return jdbc.sql("""
                select posicion, emisor, num_serie_factura, fecha_expedicion_factura,
                       huella, huella_anterior,
                       fecha_hora_huso_gen_registro, huso_offset_segundos
                from registro_facturacion
                where obligado_id = :obligado
                order by posicion desc
                limit 2
                """)
                .param("obligado", obligadoId)
                .query((rs, fila) -> new Enlace(
                        rs.getLong("posicion"),
                        new Nif(rs.getString("emisor")),
                        rs.getString("num_serie_factura"),
                        rs.getObject("fecha_expedicion_factura", LocalDate.class),
                        new Huella(rs.getString("huella")),
                        huellaOpcional(rs.getString("huella_anterior")),
                        rs.getObject("fecha_hora_huso_gen_registro", OffsetDateTime.class)
                                .withOffsetSameInstant(
                                        ZoneOffset.ofTotalSeconds(rs.getInt("huso_offset_segundos")))))
                .list();
    }

    private static Huella huellaOpcional(String valor) {
        return valor == null ? null : new Huella(valor);
    }

    /**
     * Un registro guardado, visto desde la cadena: con qué enlaza hacia atrás y cuándo se generó.
     *
     * @param huellaAnterior nula solo en el primer registro de la cadena
     * @param fechaHora reconstruida con el huso con el que se calculó la huella, no con el de la
     *                  sesión de base de datos
     */
    record Enlace(long posicion, Nif emisor, String numSerieFactura, LocalDate fechaExpedicion,
                  Huella huella, Huella huellaAnterior, OffsetDateTime fechaHora) {

        RegistroAnterior comoAnterior() {
            return new RegistroAnterior(new IdFactura(emisor, numSerieFactura, fechaExpedicion), huella);
        }
    }
}
