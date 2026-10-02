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
