package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.emision.AnomaliaPrevia;
import dev.lacre.verifactu.emision.EmisorDeRegistros;
import dev.lacre.verifactu.emision.RegistroEmitido;
import dev.lacre.verifactu.emision.RegistrosRechazados;
import dev.lacre.verifactu.evento.RegistroCreado;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.ClavesDeRegimenIpsi;
import dev.lacre.verifactu.internal.FechasAeat;
import dev.lacre.verifactu.internal.OperativaAeat;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de emisión: añade registros a la cadena de un obligado, en orden y sin
 * bifurcaciones. Toda la operación va en una transacción bajo un cerrojo consultivo por
 * obligado, y resuelve la zona horaria del obligado, que entra en la huella.
 */
@Service
public class CadenaDeRegistros implements EmisorDeRegistros {

    private static final Logger log = LoggerFactory.getLogger(CadenaDeRegistros.class);

    /** Zona horaria de la que depende la fecha de hoy para las validaciones de la AEAT. */
    private static final ZoneId ZONA_AEAT = ZoneId.of("Europe/Madrid");

    private final JdbcClient jdbc;
    private final RegistroRepository repositorio;
    private final Obligados obligados;
    private final EncadenadorRegistros encadenador;
    private final Supplier<UUID> generadorDeIdentificadores;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final RegistrosRechazados rechazados;

    CadenaDeRegistros(
            JdbcClient jdbc,
            RegistroRepository repositorio,
            Obligados obligados,
            EncadenadorRegistros encadenador,
            Supplier<UUID> generadorDeIdentificadores,
            ApplicationEventPublisher eventos,
            Clock reloj,
            RegistrosRechazados rechazados) {
        this.jdbc = jdbc;
        this.repositorio = repositorio;
        this.obligados = obligados;
        this.encadenador = encadenador;
        this.generadorDeIdentificadores = generadorDeIdentificadores;
        this.eventos = eventos;
        this.reloj = reloj;
        this.rechazados = rechazados;
    }

    /** Puerto publicado; añade el registro y devuelve lo que vio la comprobación previa. */
    @Override
    @Transactional
    public RegistroEmitido emitir(UUID obligadoId, DatosRegistro datos) {
        Asiento asiento = escribir(obligadoId, datos);
        RegistroFacturacion guardado = asiento.registro();
        return new RegistroEmitido(guardado.id(), guardado.posicion(), guardado.huella(), asiento.avisos());
    }

    @Transactional
    public RegistroFacturacion anadir(UUID obligadoId, DatosRegistro datos) {
        return escribir(obligadoId, datos).registro();
    }

    private Asiento escribir(UUID obligadoId, DatosRegistro datos) {
        ObligadoTributario obligado =
                obligados.findById(obligadoId).orElseThrow(() -> new ObligadoDesconocidoException(obligadoId));

        serializarLaCadenaDe(obligadoId);

        if (datos instanceof DatosRegistroAlta alta) {
            LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_AEAT));
            FechasAeat.exigir(alta, hoy);
            ClavesDeRegimenIpsi.exigir(alta, hoy);
        }
        OperativaAeat.exigir(datos, laFacturaYaTieneRegistro(obligadoId, datos.idFactura()));

        List<Enlace> cola = losDosUltimosDe(obligadoId);
        Optional<Enlace> ultimo = cola.stream().findFirst();
        Set<AnomaliaPrevia> avisos = comprobacionPreviaDelArticulo7i(obligadoId, cola);

        RegistroEncadenado encadenado =
                encadenador.encadenar(datos, ultimo.map(Enlace::comoAnterior), obligado.zonaHoraria());
        long posicion = ultimo.map(enlace -> enlace.posicion() + 1).orElse(1L);

        RegistroFacturacion guardado = repositorio.save(RegistroFacturacion.de(
                generadorDeIdentificadores.get(),
                obligadoId,
                posicion,
                encadenado,
                EscritorRegistro.escribir(encadenado)));

        // Oyentes síncronos: corren antes de que confirme esta transacción.
        eventos.publishEvent(new RegistroCreado(guardado.id(), obligadoId));
        return new Asiento(guardado, avisos);
    }

    /**
     * Comprobación previa del art. 7.i de la OM HAC/1177/2024. Avisa y no impide emitir; el
     * primer registro de la cadena está exento.
     */
    private Set<AnomaliaPrevia> comprobacionPreviaDelArticulo7i(UUID obligadoId, List<Enlace> cola) {
        if (cola.isEmpty()) {
            return Set.of();
        }
        Enlace ultimo = cola.getFirst();
        Enlace penultimo = cola.size() > 1 ? cola.get(1) : null;

        Set<AnomaliaPrevia> anomalias = ComprobacionPrevia.comprobar(ultimo, penultimo, OffsetDateTime.now(reloj));

        if (!anomalias.isEmpty()) {
            log.error(
                    "Comprobación previa del art. 7.i: la cadena del obligado {} presenta {} "
                            + "en su registro de posición {}. Se emite igualmente, porque la "
                            + "facturación no debe interrumpirse.",
                    obligadoId,
                    anomalias,
                    ultimo.posicion());
        }
        return anomalias;
    }

    /** Si la factura tiene en la cadena algún registro que la AEAT no rechazó. */
    private boolean laFacturaYaTieneRegistro(UUID obligadoId, IdFactura factura) {
        List<UUID> registros = jdbc.sql("""
                select id from registro_facturacion
                where obligado_id = :obligado and emisor = :emisor
                  and num_serie_factura = :numSerie and fecha_expedicion_factura = :fecha
                """)
                .param("obligado", obligadoId)
                .param("emisor", factura.emisor().valor())
                .param("numSerie", factura.numSerieFactura())
                .param("fecha", factura.fechaExpedicion())
                .query(UUID.class)
                .list();
        return !rechazados.entre(registros).containsAll(registros);
    }

    /** Cerrojo consultivo por obligado; se libera al terminar la transacción. */
    private void serializarLaCadenaDe(UUID obligadoId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtext(:clave))")
                .param("clave", "cadena:" + obligadoId)
                .query()
                .singleRow();
    }

    /**
     * Los dos últimos registros de la cadena: el primero para encadenar el nuevo, el segundo
     * para la comprobación previa.
     */
    private List<Enlace> losDosUltimosDe(UUID obligadoId) {
        return jdbc.sql("""
                select posicion, emisor, num_serie_factura, fecha_expedicion_factura,
                       huella, huella_anterior,
                       fecha_hora_huso_gen_registro, huso_offset_segundos, xml
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
                                .withOffsetSameInstant(ZoneOffset.ofTotalSeconds(rs.getInt("huso_offset_segundos"))),
                        rs.getString("xml")))
                .list();
    }

    private static Huella huellaOpcional(String valor) {
        return valor == null ? null : new Huella(valor);
    }

    /** Registro recién escrito y las anomalías que la comprobación previa encontró antes. */
    private record Asiento(RegistroFacturacion registro, Set<AnomaliaPrevia> avisos) {}

    /**
     * Registro guardado visto desde la cadena.
     *
     * @param huellaAnterior nula en el primer registro de la cadena
     * @param fechaHora con el huso original con el que se calculó la huella
     * @param xml el registro tal y como se guardó
     */
    record Enlace(
            long posicion,
            Nif emisor,
            String numSerieFactura,
            LocalDate fechaExpedicion,
            Huella huella,
            Huella huellaAnterior,
            OffsetDateTime fechaHora,
            String xml) {

        IdFactura idFactura() {
            return new IdFactura(emisor, numSerieFactura, fechaExpedicion);
        }

        RegistroAnterior comoAnterior() {
            return new RegistroAnterior(idFactura(), huella);
        }
    }
}
