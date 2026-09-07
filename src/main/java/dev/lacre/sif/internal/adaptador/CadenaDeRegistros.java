package dev.lacre.sif.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.sif.huella.EncadenadorRegistros;
import dev.lacre.sif.internal.xml.EscritorRegistro;
import dev.lacre.sif.registro.DatosRegistro;
import dev.lacre.sif.registro.IdFactura;
import dev.lacre.sif.registro.RegistroAnterior;
import dev.lacre.sif.registro.RegistroEncadenado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
 */
@Service
public class CadenaDeRegistros {

    private final JdbcClient jdbc;
    private final RegistroRepository repositorio;
    private final EncadenadorRegistros encadenador;
    private final Supplier<UUID> generadorDeIdentificadores;

    CadenaDeRegistros(JdbcClient jdbc, RegistroRepository repositorio,
                      EncadenadorRegistros encadenador, Supplier<UUID> generadorDeIdentificadores) {
        this.jdbc = jdbc;
        this.repositorio = repositorio;
        this.encadenador = encadenador;
        this.generadorDeIdentificadores = generadorDeIdentificadores;
    }

    @Transactional
    public RegistroFacturacion anadir(UUID obligadoId, DatosRegistro datos) {
        serializarLaCadenaDe(obligadoId);

        Optional<Enlace> ultimo = ultimoDe(obligadoId);
        RegistroEncadenado encadenado = encadenador.encadenar(datos, ultimo.map(Enlace::comoAnterior));
        long posicion = ultimo.map(enlace -> enlace.posicion() + 1).orElse(1L);

        return repositorio.save(RegistroFacturacion.de(
                generadorDeIdentificadores.get(), obligadoId, posicion, encadenado,
                EscritorRegistro.escribir(encadenado)));
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

    /** Lado de lectura: SQL explícito, sin pasar por el agregado. */
    private Optional<Enlace> ultimoDe(UUID obligadoId) {
        return jdbc.sql("""
                select posicion, emisor, num_serie_factura, fecha_expedicion_factura, huella
                from registro_facturacion
                where obligado_id = :obligado
                order by posicion desc
                limit 1
                """)
                .param("obligado", obligadoId)
                .query((rs, fila) -> new Enlace(
                        rs.getLong("posicion"),
                        new Nif(rs.getString("emisor")),
                        rs.getString("num_serie_factura"),
                        rs.getObject("fecha_expedicion_factura", LocalDate.class),
                        new Huella(rs.getString("huella"))))
                .optional();
    }

    private record Enlace(long posicion, Nif emisor, String numSerieFactura,
                          LocalDate fechaExpedicion, Huella huella) {

        RegistroAnterior comoAnterior() {
            return new RegistroAnterior(new IdFactura(emisor, numSerieFactura, fechaExpedicion), huella);
        }
    }
}
