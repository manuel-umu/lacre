package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las tres capas de defensa de {@code registro_facturacion}, contra Postgres real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RegistroFacturacionAppendOnlyTest {

    private static final UUID OBLIGADO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String HUELLA =
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60";

    @Autowired
    private PostgreSQLContainer postgres;

    /** Como propietario: estos tests se saltan a propósito las defensas del registro. */
    private JdbcClient jdbc;

    private UUID registro;

    @BeforeEach
    void insertarUnRegistro() {
        jdbc = TestcontainersConfiguration.comoPropietario(postgres);
        jdbc.sql("""
                insert into obligado (id, nif, nombre_razon, zona_horaria)
                values (:id, :nif, :nombre, :zona)
                on conflict (id) do nothing
                """)
                .param("id", OBLIGADO)
                .param("nif", "89890001K")
                .param("nombre", "Obligado de prueba SL")
                .param("zona", "Europe/Madrid")
                .update();

        registro = insertar(siguientePosicion(), HUELLA);
    }

    // --- Capa 2: el trigger ---

    @Test
    void unUpdateRevienta() {
        assertThatThrownBy(() -> jdbc.sql("update registro_facturacion set xml = 'manipulado' where id = :id")
                .param("id", registro)
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo inserción");
    }

    @Test
    void unUpdateQueNoAfectaANingunaFilaTambienRevienta() {
        assertThatThrownBy(() -> jdbc.sql("update registro_facturacion set xml = 'x' where id = :id")
                .param("id", UUID.randomUUID())
                .update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void unDeleteRevienta() {
        assertThatThrownBy(() -> jdbc.sql("delete from registro_facturacion where id = :id")
                .param("id", registro)
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo inserción");
    }

    /**
     * La clave ajena del outbox rechaza el {@code truncate} a secas antes de que actúe el
     * trigger; truncar a la vez todas las tablas que referencian al registro sí llega al trigger.
     * La lista crece con cada tabla nueva que lo referencie.
     */
    @Test
    void unTruncateRevienta() {
        assertThatThrownBy(() -> jdbc.sql("truncate registro_facturacion").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("foreign key");

        assertThatThrownBy(() -> jdbc.sql("truncate registro_facturacion, envio_registro, peticion_idempotente").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo inserción");
    }

    @Test
    void elRegistroSigueIntactoDespuesDeIntentarlo() {
        try {
            jdbc.sql("update registro_facturacion set huella = 'x' where id = :id")
                    .param("id", registro).update();
        } catch (DataAccessException esperado) {
            // El trigger ha hecho su trabajo.
        }

        String huella = jdbc.sql("select huella from registro_facturacion where id = :id")
                .param("id", registro).query(String.class).single();

        assertThat(huella).isEqualTo(HUELLA);
    }

    // --- Capa 3: la restricción UNIQUE ---

    @Test
    void dosRegistrosNoPuedenOcuparLaMismaPosicionEnLaCadena() {
        long posicion = siguientePosicion();
        insertar(posicion, HUELLA);

        assertThatThrownBy(() -> insertar(posicion, HUELLA))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("registro_facturacion_cadena_unica");
    }

    @Test
    void insertarSiSeSigueDejando() {
        long antes = jdbc.sql("select count(*) from registro_facturacion").query(Long.class).single();

        insertar(siguientePosicion(), HUELLA);

        assertThat(jdbc.sql("select count(*) from registro_facturacion").query(Long.class).single())
                .isEqualTo(antes + 1);
    }

    // --- Apoyo ---

    private long siguientePosicion() {
        return jdbc.sql("select coalesce(max(posicion), 0) + 1 from registro_facturacion where obligado_id = :o")
                .param("o", OBLIGADO)
                .query(Long.class)
                .single();
    }

    private UUID insertar(long posicion, String huella) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into registro_facturacion (
                    id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                    fecha_expedicion_factura, huella, huella_anterior,
                    fecha_hora_huso_gen_registro, huso_offset_segundos, xml)
                values (:id, :obligado, :posicion, 'ALTA', '89890001K', :numSerie,
                    date '2024-01-01', :huella, null, :fechaHora, 3600, '<sf:RegistroAlta/>')
                """)
                .param("id", id)
                .param("obligado", OBLIGADO)
                .param("posicion", posicion)
                .param("numSerie", "FA/" + posicion)
                .param("huella", huella)
                .param("fechaHora", OffsetDateTime.parse("2024-01-01T19:20:30+01:00"))
                .update();
        return id;
    }
}
