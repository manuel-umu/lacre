package dev.lacre.remision.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EnviosDeObligado;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

        assertThat(envios).isEqualTo(new EnviosDeObligado(obligado, 2, 2, 1, 1, 1, 0, envios.pendienteMasAntiguo()));
        assertThat(envios.pendienteMasAntiguo()).isEqualTo(antiguo);
    }

    @Test
    void sinPendientesNoHayMasAntiguo() {
        marcar(emitir("FA/1"), EstadoEnvio.ACEPTADO);

        assertThat(resumen.porObligado().get(obligado).pendienteMasAntiguo()).isNull();
    }

    @Test
    void losPendientesVanDelMasAntiguoAlMasRecienteYConTope() {
        OffsetDateTime base = OffsetDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        UUID tercero = emitir("FA/1");
        UUID primero = emitir("FA/2");
        UUID segundo = emitir("FA/3");
        marcar(emitir("FA/4"), EstadoEnvio.RECHAZADO);
        crearEn(primero, base);
        crearEn(segundo, base.plusMinutes(1));
        crearEn(tercero, base.plusMinutes(2));

        assertThat(resumen.pendientesDe(obligado, 2))
                .extracting(EnvioRegistro::registroId)
                .containsExactly(primero, segundo);
    }

    @Test
    void losErroresSonRechazadosYAceptadosConErroresDelMasRecienteAlMasAntiguo() {
        OffsetDateTime base = OffsetDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        UUID antiguo = emitir("FA/1");
        UUID reciente = emitir("FA/2");
        UUID medio = emitir("FA/3");
        emitir("FA/4");
        marcar(antiguo, EstadoEnvio.RECHAZADO, base);
        marcar(reciente, EstadoEnvio.ACEPTADO_CON_ERRORES, base.plusHours(2));
        marcar(medio, EstadoEnvio.RECHAZADO, base.plusHours(1));
        marcar(emitir("FA/5"), EstadoEnvio.ACEPTADO);
        marcar(emitir("FA/6"), EstadoEnvio.DUPLICADO);

        List<EnvioRegistro> errores = resumen.conErroresDe(obligado, 10);

        assertThat(errores).extracting(EnvioRegistro::registroId).containsExactly(reciente, medio, antiguo);
        assertThat(errores.getFirst().codigoError()).isEqualTo(1100);
        assertThat(resumen.conErroresDe(obligado, 1)).hasSize(1);
    }

    @Test
    void cadaObligadoVeSoloLosSuyos() {
        emitir("FA/1");
        UUID otro = obligado;
        obligado = ObligadosDePrueba.nuevo(obligados);

        assertThat(resumen.pendientesDe(obligado, 10)).isEmpty();
        assertThat(resumen.pendientesDe(otro, 10)).hasSize(1);
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
        marcar(registroId, estado, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void marcar(UUID registroId, EstadoEnvio estado, OffsetDateTime respondido) {
        jdbc.sql("""
                        update envio_registro
                        set estado = :estado, enviado_en = :respondido, codigo_error = 1100,
                            descripcion_error = 'Valor o tipo incorrecto del campo'
                        where registro_id = :id
                        """)
                .param("estado", estado.name())
                .param("respondido", respondido)
                .param("id", registroId)
                .update();
    }

    private void crearEn(UUID registroId, OffsetDateTime creado) {
        jdbc.sql("update envio_registro set creado_en = :creado where registro_id = :id")
                .param("creado", creado)
                .param("id", registroId)
                .update();
    }
}
