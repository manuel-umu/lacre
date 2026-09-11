package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Nif;
import dev.lacre.verifactu.consulta.RegistroRemitible;
import dev.lacre.verifactu.consulta.RegistrosRemitibles;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Lado de lectura de los registros que hay que remitir, con SQL explícito. */
@Component
class RegistrosRemitiblesJdbc implements RegistrosRemitibles {

    private final JdbcClient jdbc;

    RegistrosRemitiblesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RegistroRemitible> de(List<UUID> registroIds) {
        if (registroIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, RegistroRemitible> porId = new HashMap<>();
        jdbc.sql("""
                select id, tipo, emisor, num_serie_factura, fecha_expedicion_factura, xml
                from registro_facturacion
                where id in (:ids)
                """)
                .param("ids", registroIds)
                .query((rs, fila) -> porId.put(rs.getObject("id", UUID.class), new RegistroRemitible(
                        rs.getObject("id", UUID.class),
                        new IdFactura(new Nif(rs.getString("emisor")),
                                rs.getString("num_serie_factura"),
                                rs.getObject("fecha_expedicion_factura", LocalDate.class)),
                        TipoRegistro.valueOf(rs.getString("tipo")),
                        rs.getString("xml"))))
                .list();

        // En el orden pedido: quien llama empareja el resultado con su propia lista.
        return registroIds.stream().map(porId::get).filter(Objects::nonNull).toList();
    }
}
