package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.consulta.RegistrosGuardados;
import dev.lacre.verifactu.consulta.VerificacionDeCadena;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

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

    private final JdbcClient jdbc;

    RegistrosGuardadosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
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

    /** Carga la cadena entera en memoria y comprueba los enlaces. */
    @Override
    public VerificacionDeCadena verificarCadenaDe(UUID obligadoId) {
        List<Eslabon> cadena = jdbc.sql("""
                select posicion, huella, huella_anterior
                from registro_facturacion
                where obligado_id = :obligado
                order by posicion
                """)
                .param("obligado", obligadoId)
                .query((rs, fila) -> new Eslabon(
                        rs.getLong("posicion"),
                        new Huella(rs.getString("huella")),
                        huellaOpcional(rs.getString("huella_anterior"))))
                .list();

        return new VerificacionDeCadena(obligadoId, cadena.size(), roturasDe(cadena),
                VerificacionDeCadena.Alcance.ENLACES);
    }

    /**
     * El primero no lleva huella anterior; cualquier otro lleva la del que le precede y ocupa la
     * posición siguiente.
     */
    private static List<Rotura> roturasDe(List<Eslabon> cadena) {
        List<Rotura> roturas = new ArrayList<>();
        Eslabon anterior = null;

        for (Eslabon eslabon : cadena) {
            if (anterior == null) {
                if (eslabon.huellaAnterior() != null) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.PRIMERO_CON_HUELLA_ANTERIOR));
                }
            } else {
                if (eslabon.posicion() != anterior.posicion() + 1) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.POSICION_SALTADA));
                }
                if (eslabon.huellaAnterior() == null) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.SIN_HUELLA_ANTERIOR));
                } else if (!eslabon.huellaAnterior().equals(anterior.huella())) {
                    roturas.add(new Rotura(eslabon.posicion(), Motivo.HUELLA_ANTERIOR_NO_CUADRA));
                }
            }
            anterior = eslabon;
        }
        return roturas;
    }

    private static Huella huellaOpcional(String valor) {
        return valor == null ? null : new Huella(valor);
    }

    private record Eslabon(long posicion, Huella huella, Huella huellaAnterior) {
    }
}
