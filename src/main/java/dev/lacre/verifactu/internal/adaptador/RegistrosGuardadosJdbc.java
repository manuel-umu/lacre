package dev.lacre.verifactu.internal.adaptador;

import static dev.lacre.verifactu.consulta.VerificacionDeCadena.Rotura;
import static dev.lacre.verifactu.consulta.VerificacionDeCadena.Rotura.Motivo;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.consulta.RegistroDeFactura;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import dev.lacre.verifactu.consulta.VerificacionDeCadena;
import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.internal.xml.LectorRegistro;
import dev.lacre.verifactu.internal.xml.RegistroIlegibleException;
import dev.lacre.verifactu.internal.xml.RegistroLeido;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lado de lectura de los registros guardados, con SQL explícito. */
@Component
class RegistrosGuardadosJdbc implements RegistrosGuardados {

    /** Filas que PostgreSQL entrega de cada vez al recorrer una cadena, en vez de todas. */
    private static final int FILAS_POR_TANDA = 500;

    private final JdbcClient jdbc;
    private final Canonicalizador canonicalizador;

    RegistrosGuardadosJdbc(JdbcClient jdbc, Canonicalizador canonicalizador) {
        this.jdbc = jdbc;
        this.canonicalizador = canonicalizador;
    }

    private static final String SELECT_REGISTRO = """
            select id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                   fecha_expedicion_factura, huella, huella_anterior,
                   fecha_hora_huso_gen_registro, huso_offset_segundos
            from registro_facturacion
            """;

    @Override
    public Optional<RegistroGuardado> porId(UUID registroId) {
        return jdbc.sql(SELECT_REGISTRO + "where id = :id")
                .param("id", registroId)
                .query(RegistrosGuardadosJdbc::registroGuardado)
                .optional();
    }

    @Override
    public List<RegistroGuardado> porIds(Collection<UUID> registroIds) {
        if (registroIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT_REGISTRO + "where id in (:ids)")
                .param("ids", registroIds)
                .query(RegistrosGuardadosJdbc::registroGuardado)
                .list();
    }

    private static RegistroGuardado registroGuardado(ResultSet rs, int fila) throws SQLException {
        return new RegistroGuardado(
                rs.getObject("id", UUID.class),
                rs.getObject("obligado_id", UUID.class),
                rs.getLong("posicion"),
                TipoRegistro.valueOf(rs.getString("tipo")),
                new IdFactura(
                        new Nif(rs.getString("emisor")),
                        rs.getString("num_serie_factura"),
                        rs.getObject("fecha_expedicion_factura", LocalDate.class)),
                new Huella(rs.getString("huella")),
                huellaOpcional(rs.getString("huella_anterior")),
                rs.getObject("fecha_hora_huso_gen_registro", OffsetDateTime.class)
                        .withOffsetSameInstant(ZoneOffset.ofTotalSeconds(rs.getInt("huso_offset_segundos"))));
    }

    @Override
    public List<RegistroDeFactura> deFacturasExpedidasEn(UUID obligadoId, YearMonth mes) {
        return jdbc.sql(SELECT_PARA_COTEJAR + """
                        where obligado_id = :obligado
                          and fecha_expedicion_factura between :desde and :hasta
                        """)
                .param("obligado", obligadoId)
                .param("desde", mes.atDay(1))
                .param("hasta", mes.atEndOfMonth())
                .query(RegistrosGuardadosJdbc::registroDeFactura)
                .list();
    }

    @Override
    public List<RegistroDeFactura> deFacturas(UUID obligadoId, Collection<IdFactura> facturas) {
        if (facturas.isEmpty()) {
            return List.of();
        }
        Set<IdFactura> buscadas = Set.copyOf(facturas);
        return jdbc
                .sql(SELECT_PARA_COTEJAR + """
                        where obligado_id = :obligado and num_serie_factura in (:series)
                        """)
                .param("obligado", obligadoId)
                .param(
                        "series",
                        buscadas.stream()
                                .map(IdFactura::numSerieFactura)
                                .distinct()
                                .toList())
                .query(RegistrosGuardadosJdbc::registroDeFactura)
                .list()
                .stream()
                .filter(registro -> buscadas.contains(registro.idFactura()))
                .toList();
    }

    private static final String SELECT_PARA_COTEJAR = """
            select id, posicion, tipo, emisor, num_serie_factura, fecha_expedicion_factura, huella, xml
            from registro_facturacion
            """;

    /** Un XML ilegible no impide cotejar: sin fecha de operación se imputa por la de expedición. */
    private static RegistroDeFactura registroDeFactura(ResultSet rs, int fila) throws SQLException {
        TipoRegistro tipo = TipoRegistro.valueOf(rs.getString("tipo"));
        LocalDate fechaOperacion = null;
        if (tipo == TipoRegistro.ALTA) {
            try {
                fechaOperacion = LectorRegistro.leer(rs.getString("xml"))
                        .fechaOperacion()
                        .orElse(null);
            } catch (RegistroIlegibleException e) {
                fechaOperacion = null;
            }
        }
        return new RegistroDeFactura(
                rs.getObject("id", UUID.class),
                rs.getLong("posicion"),
                tipo,
                new IdFactura(
                        new Nif(rs.getString("emisor")),
                        rs.getString("num_serie_factura"),
                        rs.getObject("fecha_expedicion_factura", LocalDate.class)),
                new Huella(rs.getString("huella")),
                fechaOperacion);
    }

    @Override
    public Map<UUID, Long> ultimaPosicionDeCadaObligado() {
        Map<UUID, Long> posiciones = new HashMap<>();
        jdbc.sql("select obligado_id, max(posicion) as ultima from registro_facturacion group by obligado_id")
                .query(rs -> {
                    posiciones.put(rs.getObject("obligado_id", UUID.class), rs.getLong("ultima"));
                });
        return posiciones;
    }

    /**
     * Recorre la cadena fila a fila, sin cargarla entera: comprueba que cada eslabón enlaza con el
     * anterior y recalcula su huella desde el XML guardado, que es lo que se remitió.
     */
    @Override
    @Transactional(readOnly = true)
    public VerificacionDeCadena verificarCadenaDe(UUID obligadoId) {
        Recorrido recorrido = new Recorrido();
        jdbc.sql("""
                select posicion, huella, huella_anterior, xml
                from registro_facturacion
                where obligado_id = :obligado
                order by posicion
                """)
                .withFetchSize(FILAS_POR_TANDA)
                .param("obligado", obligadoId)
                .query((RowCallbackHandler) fila -> recorrido.comprobar(new Eslabon(
                        fila.getLong("posicion"),
                        new Huella(fila.getString("huella")),
                        huellaOpcional(fila.getString("huella_anterior")),
                        fila.getString("xml"))));

        return new VerificacionDeCadena(
                obligadoId, recorrido.registros, recorrido.roturas, VerificacionDeCadena.Alcance.HUELLAS);
    }

    /** Estado de un recorrido: las roturas encontradas y el último eslabón visto. */
    private final class Recorrido {

        private final List<Rotura> roturas = new ArrayList<>();
        private long registros;
        private Eslabon anterior;
        private RegistroLeido anteriorLeido;

        void comprobar(Eslabon eslabon) {
            registros++;
            enlace(eslabon);
            RegistroLeido leido = huella(eslabon);
            identificacionDelAnterior(eslabon, leido);
            anterior = eslabon;
            anteriorLeido = leido;
        }

        /**
         * El primero no lleva huella anterior; cualquier otro lleva la del que le precede y ocupa
         * la posición siguiente.
         */
        private void enlace(Eslabon eslabon) {
            if (anterior == null) {
                if (eslabon.huellaAnterior() != null) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.PRIMERO_CON_HUELLA_ANTERIOR));
                }
                return;
            }
            if (eslabon.posicion() != anterior.posicion() + 1) {
                roturas.add(new Rotura(eslabon.posicion(), Motivo.POSICION_SALTADA));
            }
            if (eslabon.huellaAnterior() == null) {
                roturas.add(new Rotura(eslabon.posicion(), Motivo.SIN_HUELLA_ANTERIOR));
            } else if (!eslabon.huellaAnterior().equals(anterior.huella())) {
                roturas.add(new Rotura(eslabon.posicion(), Motivo.HUELLA_ANTERIOR_NO_CUADRA));
            }
        }

        /**
         * La huella guardada tiene que ser la que declara el XML y la que sale de su contenido.
         * Devuelve el registro leído, o {@code null} si su XML es ilegible.
         */
        private RegistroLeido huella(Eslabon eslabon) {
            try {
                RegistroLeido leido = LectorRegistro.leer(eslabon.xml());
                if (!leido.huella().equals(eslabon.huella())
                        || !leido.huellaRecalculada(canonicalizador).equals(eslabon.huella())) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.HUELLA_NO_CUADRA));
                }
                return leido;
            } catch (RegistroIlegibleException e) {
                roturas.add(new Rotura(eslabon.posicion(), Motivo.XML_ILEGIBLE));
                return null;
            }
        }

        /**
         * La factura que el XML declara como anterior es la del XML que le precede. Si no declara
         * ninguna, la falta ya la denuncian los enlaces; si algún XML es ilegible, también.
         */
        private void identificacionDelAnterior(Eslabon eslabon, RegistroLeido leido) {
            if (leido == null || anteriorLeido == null) {
                return;
            }
            leido.anterior()
                    .filter(declarado ->
                            !declarado.idFactura().equals(anteriorLeido.campos().idFactura()))
                    .ifPresent(declarado ->
                            roturas.add(new Rotura(eslabon.posicion(), Motivo.IDENTIFICACION_ANTERIOR_NO_CUADRA)));
        }
    }

    private static Huella huellaOpcional(String valor) {
        return valor == null ? null : new Huella(valor);
    }

    private record Eslabon(long posicion, Huella huella, Huella huellaAnterior, String xml) {}
}
