package dev.lacre.remision.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnvioDesconocidoException;
import dev.lacre.remision.EnvioEnCursoException;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EnvioYaResueltoException;
import dev.lacre.remision.Envios;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OperacionDeEnviosJdbcTest {

    @Autowired
    private OperacionDeEnviosJdbc operacion;

    @Autowired
    private ColaDeEnvios cola;

    @Autowired
    private Envios envios;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private PostgreSQLContainer postgres;

    private UUID obligado;

    @BeforeEach
    void preparar() {
        obligado = ObligadosDePrueba.nuevo(obligados);
    }

    @Test
    void unApartadoNoEntraEnElLoteDelDespachadorHastaQueSeReanuda() {
        EnvioRegistro primero = emitir("FA/1");
        EnvioRegistro segundo = emitir("FA/2");

        operacion.apartar(obligado, primero.id());

        assertThat(reservar()).containsExactly(segundo.id());
        assertThat(estadoDe(primero)).isEqualTo(EstadoEnvio.APARTADO);

        operacion.reanudar(obligado, primero.id());

        assertThat(reservar()).containsExactly(primero.id(), segundo.id());
    }

    @Test
    void apartarDosVecesNoSePuede() {
        EnvioRegistro envio = emitir("FA/1");
        operacion.apartar(obligado, envio.id());

        assertThatThrownBy(() -> operacion.apartar(obligado, envio.id())).isInstanceOf(EnvioYaResueltoException.class);
        assertThatThrownBy(() -> operacion.reanudar(obligado, emitir("FA/2").id()))
                .isInstanceOf(EnvioYaResueltoException.class);
    }

    @Test
    void unEnvioDeOtroObligadoEsDesconocido() {
        EnvioRegistro envio = emitir("FA/1");
        UUID otro = ObligadosDePrueba.nuevo(obligados);

        assertThatThrownBy(() -> operacion.apartar(otro, envio.id())).isInstanceOf(EnvioDesconocidoException.class);
        assertThatThrownBy(() -> operacion.apartar(obligado, UUID.randomUUID()))
                .isInstanceOf(EnvioDesconocidoException.class);
        assertThat(estadoDe(envio)).isEqualTo(EstadoEnvio.PENDIENTE);
    }

    @Test
    void seApartanYSeReanudanTodosLosDelObligadoSinTocarLosDeOtro() {
        emitir("FA/1");
        emitir("FA/2");
        UUID otro = obligado;
        obligado = ObligadosDePrueba.nuevo(obligados);
        EnvioRegistro ajeno = emitir("FA/1");
        obligado = otro;

        assertThat(operacion.apartarPendientesDe(obligado)).isEqualTo(2);
        assertThat(reservar()).isEmpty();
        assertThat(estadoDe(ajeno)).isEqualTo(EstadoEnvio.PENDIENTE);

        assertThat(operacion.reanudarApartadosDe(obligado)).isEqualTo(2);
        assertThat(reservar()).hasSize(2);
    }

    @Test
    void unEnvioEnUnLoteEnVueloNoSeEsperaNiSePisa() throws Exception {
        EnvioRegistro envio = emitir("FA/1");
        CountDownLatch tomado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        CompletableFuture<Void> lote = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            cola.reservarLoteDe(obligado, 10);
            tomado.countDown();
            esperar(soltar);
        }));
        try {
            assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> operacion.apartar(obligado, envio.id())).isInstanceOf(EnvioEnCursoException.class);
            assertThat(operacion.apartarPendientesDe(obligado)).isZero();
        } finally {
            soltar.countDown();
            lote.get(10, TimeUnit.SECONDS);
        }
        assertThat(estadoDe(envio)).isEqualTo(EstadoEnvio.PENDIENTE);
    }

    @Test
    void unRechazadoYUnAceptadoConErroresSeDanPorAtendidosConQuienYCuando() {
        EnvioRegistro rechazado = resolver(emitir("FA/1"), "RECHAZADO");
        EnvioRegistro conErrores = resolver(emitir("FA/2"), "ACEPTADO_CON_ERRORES");

        operacion.marcarAtendido(obligado, rechazado.id(), "operador");
        operacion.marcarAtendido(obligado, conErrores.id(), "operador");

        var atencion = TestcontainersConfiguration.comoPropietario(postgres)
                .sql("select atendido_por, atendido_en from envio_registro where id = :id")
                .param("id", rechazado.id())
                .query((rs, fila) -> rs.getString("atendido_por") + "@" + (rs.getObject("atendido_en") != null))
                .single();
        assertThat(atencion).isEqualTo("operador@true");
        assertThat(estadoDe(rechazado)).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    @Test
    void soloSeAtiendeUnErrorDeLaAeatYUnaSolaVez() {
        EnvioRegistro rechazado = resolver(emitir("FA/1"), "RECHAZADO");
        EnvioRegistro aceptado = resolver(emitir("FA/2"), "ACEPTADO");
        EnvioRegistro pendiente = emitir("FA/3");
        operacion.marcarAtendido(obligado, rechazado.id(), "operador");

        assertThatThrownBy(() -> operacion.marcarAtendido(obligado, rechazado.id(), "operador"))
                .isInstanceOf(EnvioYaResueltoException.class);
        assertThatThrownBy(() -> operacion.marcarAtendido(obligado, aceptado.id(), "operador"))
                .isInstanceOf(EnvioYaResueltoException.class);
        assertThatThrownBy(() -> operacion.marcarAtendido(obligado, pendiente.id(), "operador"))
                .isInstanceOf(EnvioYaResueltoException.class);
    }

    @Test
    void elErrorDeOtroObligadoEsDesconocido() {
        EnvioRegistro rechazado = resolver(emitir("FA/1"), "RECHAZADO");
        UUID otro = ObligadosDePrueba.nuevo(obligados);

        assertThatThrownBy(() -> operacion.marcarAtendido(otro, rechazado.id(), "operador"))
                .isInstanceOf(EnvioDesconocidoException.class);
        assertThatThrownBy(() -> operacion.marcarAtendido(obligado, UUID.randomUUID(), "operador"))
                .isInstanceOf(EnvioDesconocidoException.class);
    }

    private EnvioRegistro resolver(EnvioRegistro envio, String estado) {
        TestcontainersConfiguration.comoPropietario(postgres)
                .sql("update envio_registro set estado = :estado, enviado_en = now() where id = :id")
                .param("estado", estado)
                .param("id", envio.id())
                .update();
        return envios.findById(envio.id()).orElseThrow();
    }

    private java.util.List<UUID> reservar() {
        return transaccion.execute(estado -> cola.reservarLoteDe(obligado, 10));
    }

    private EstadoEnvio estadoDe(EnvioRegistro envio) {
        return envios.findById(envio.id()).orElseThrow().estado();
    }

    private EnvioRegistro emitir(String numSerie) {
        UUID registro = cadena.anadir(
                        obligado,
                        Registros.emitible()
                                .idFactura(Registros.idFacturaEmitible(numSerie))
                                .build())
                .id();
        return envios.findByRegistroId(registro).orElseThrow();
    }

    private static void esperar(CountDownLatch cerrojo) {
        try {
            cerrojo.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
