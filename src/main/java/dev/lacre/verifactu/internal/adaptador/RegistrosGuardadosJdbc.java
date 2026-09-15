package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import dev.lacre.verifactu.consulta.VerificacionDeCadena;
import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.internal.xml.LectorRegistro;
import dev.lacre.verifactu.internal.xml.RegistroIlegibleException;
import dev.lacre.verifactu.internal.xml.RegistroLeido;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.lacre.verifactu.consulta.VerificacionDeCadena.Rotura;
import static dev.lacre.verifactu.consulta.VerificacionDeCadena.Rotura.Motivo;

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

    @Override
    public Optional<RegistroGuardado> porId(UUID registroId) {
        return jdbc.sql("""
                select id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                       fecha_expedicion_factura, huella, huella_anterior,
                       fecha_hora_huso_gen_registro, huso_offset_segundos
                from registro_facturacion
                where id = :id
                """)
                .param("id", registroId)
                .query((rs, fila) -> new RegistroGuardado(
                        rs.getObject("id", UUID.class),
                        rs.getObject("obligado_id", UUID.class),
                        rs.getLong("posicion"),
                        TipoRegistro.valueOf(rs.getString("tipo")),
                        new IdFactura(new Nif(rs.getString("emisor")),
                                rs.getString("num_serie_factura"),
                                rs.getObject("fecha_expedicion_factura", LocalDate.class)),
                        new Huella(rs.getString("huella")),
                        huellaOpcional(rs.getString("huella_anterior")),
                        rs.getObject("fecha_hora_huso_gen_registro", OffsetDateTime.class)
                                .withOffsetSameInstant(ZoneOffset.ofTotalSeconds(
                                        rs.getInt("huso_offset_segundos")))))
                .optional();
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

        return new VerificacionDeCadena(obligadoId, recorrido.registros, recorrido.roturas,
                VerificacionDeCadena.Alcance.HUELLAS);
    }

    /** Estado de un recorrido: las roturas encontradas y el último eslabón visto. */
    private final class Recorrido {

        private final List<Rotura> roturas = new ArrayList<>();
        private long registros;
        private Eslabon anterior;

        void comprobar(Eslabon eslabon) {
            registros++;
            enlace(eslabon);
            huella(eslabon);
            anterior = eslabon;
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

        /** La huella guardada tiene que ser la que declara el XML y la que sale de su contenido. */
        private void huella(Eslabon eslabon) {
            try {
                RegistroLeido leido = LectorRegistro.leer(eslabon.xml());
                if (!leido.huella().equals(eslabon.huella())
                        || !leido.huellaRecalculada(canonicalizador).equals(eslabon.huella())) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.HUELLA_NO_CUADRA));
                }
            } catch (RegistroIlegibleException e) {
                roturas.add(new Rotura(eslabon.posicion(), Motivo.XML_ILEGIBLE));
            }
        }
    }

    private static Huella huellaOpcional(String valor) {
        return valor == null ? null : new Huella(valor);
    }

    private record Eslabon(long posicion, Huella huella, Huella huellaAnterior, String xml) {
    }
}
