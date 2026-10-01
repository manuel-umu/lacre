package dev.lacre.remision.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnviosDeObligado;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResumenDeEnviosJdbcTest {

    @Autowired
    private ResumenDeEnviosJdbc resumen;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private PostgreSQLContainer postgres;

    private JdbcClient jdbc;

    private UUID obligado;

    @BeforeEach
    void preparar() {
        jdbc = TestcontainersConfiguration.comoPropietario(postgres);
        obligado = ObligadosDePrueba.nuevo(obligados);
    }

    @Test
    void cuentaCadaEstadoYDaElPendienteMasAntiguo() {
        OffsetDateTime antiguo = OffsetDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        UUID primerPendiente = emitir("FA/1");
        emitir("FA/2");
        marcar(emitir("FA/3"), EstadoEnvio.ACEPTADO);
        marcar(emitir("FA/4"), EstadoEnvio.ACEPTADO);
        marcar(emitir("FA/5"), EstadoEnvio.ACEPTADO_CON_ERRORES);
        marcar(emitir("FA/6"), EstadoEnvio.RECHAZADO);
        marcar(emitir("FA/7"), EstadoEnvio.DUPLICADO);
        jdbc.sql("update envio_registro set creado_en = :antiguo where registro_id = :id")
                .param("antiguo", antiguo)
                .param("id", primerPendiente)
                .update();

        EnviosDeObligado envios = resumen.porObligado().get(obligado);

        assertThat(envios).isEqualTo(new EnviosDeObligado(obligado, 2, 2, 1, 1, 1, envios.pendienteMasAntiguo()));
        assertThat(envios.pendienteMasAntiguo()).isEqualTo(antiguo);
    }

    @Test
    void sinPendientesNoHayMasAntiguo() {
        marcar(emitir("FA/1"), EstadoEnvio.ACEPTADO);

        assertThat(resumen.porObligado().get(obligado).pendienteMasAntiguo()).isNull();
    }

    @Test
    void unObligadoSinEnviosNoAparece() {
        assertThat(resumen.porObligado()).doesNotContainKey(obligado);
    }

    private UUID emitir(String numSerie) {
        return cadena.anadir(
                        obligado,
                        Registros.emitible()
                                .idFactura(Registros.idFacturaEmitible(numSerie))
                                .build())
                .id();
    }

    private void marcar(UUID registroId, EstadoEnvio estado) {
        jdbc.sql("update envio_registro set estado = :estado where registro_id = :id")
                .param("estado", estado.name())
                .param("id", registroId)
                .update();
    }
}
