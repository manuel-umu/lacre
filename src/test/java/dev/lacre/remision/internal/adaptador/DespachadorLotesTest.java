package dev.lacre.remision.internal.adaptador;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.EstadoEnvioAeat;
import dev.lacre.remision.EstadoRegistroAeat;
import dev.lacre.remision.Envios;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.internal.adaptador.RegistroFacturacion;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.Registros;
import dev.lacre.verifactu.registro.TipoRegistro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El despachador contra Postgres real y un cliente de la AEAT de mentira.
 * <p>
 * Lo que se prueba aquí no es el XML —eso ya está cubierto— sino las tres cosas que solo se ven
 * con base de datos: que el control de flujo del art. 16.2 impide remitir antes de tiempo, que
 * dos instancias no despachan el mismo lote, y que cada desenlace aterriza en la fila que le
 * toca.
 */
@Import({TestcontainersConfiguration.class, DespachadorLotesTest.AeatDeMentira.class})
@SpringBootTest
class DespachadorLotesTest {

    @Autowired
    private DespachadorLotes despachador;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Envios envios;

    @Autowired
    private Obligados obligados;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AeatFalsa aeat;

    private UUID obligado;

    /**
     * El outbox se vacía entre tests porque {@code despachar()} recorre <strong>todos</strong> los
     * obligados con pendientes —que es lo que tiene que hacer en producción— y el contenedor se
     * reutiliza: sin esto, cada test contaría también los lotes que dejaron los anteriores.
     * <p>
     * {@code registro_facturacion} no se toca: es de solo inserción y el trigger lo impediría.
     * No hace falta, porque cada test estrena obligado y por tanto estrena cadena.
     */
    @BeforeEach
    void preparar() {
        aeat.reiniciar();
        jdbc.sql("delete from envio_registro").update();
        jdbc.sql("delete from control_flujo_envio").update();
        obligado = ObligadosDePrueba.nuevo(obligados);
    }

    private RegistroFacturacion emitir(String numSerie) {
        return cadena.anadir(obligado,
                Registros.alta().idFactura(Registros.idFactura(numSerie)).build());
    }

    // --- Control de flujo, art. 16.2 ---

    @Test
    void elPrimerEnvioDeUnObligadoSaleSinEsperar() {
        emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));

        assertThat(despachador.despachar()).isEqualTo(1);
        assertThat(aeat.lotesRemitidos()).isEqualTo(1);
    }

    /**
     * La letra c) del artículo: hay que esperar {@code t} segundos <em>desde el anterior envío</em>.
     * El turno recién consumido vale 60 segundos, así que la segunda pasada no debe remitir nada.
     */
    @Test
    void unSegundoLoteNoSaleHastaQuePasaElTiempoDeEspera() {
        emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));
        despachador.despachar();

        emitir("FA/2");
        assertThat(despachador.despachar()).isZero();
        assertThat(aeat.lotesRemitidos()).isEqualTo(1);
    }

    @Test
    void cuandoVenceLaEsperaVuelveASalir() {
        emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));
        despachador.despachar();
        emitir("FA/2");

        // Se envejece el turno en vez de dormir 60 segundos.
        jdbc.sql("update control_flujo_envio set ultimo_envio = ultimo_envio - interval '61 seconds'")
                .update();

        assertThat(despachador.despachar()).isEqualTo(1);
        assertThat(aeat.lotesRemitidos()).isEqualTo(2);
    }

    /** El valor que devuelve la AEAT manda: si dice 0, el siguiente envío sale ya. */
    @Test
    void laEsperaQueDevuelveLaAeatSustituyeALaAnterior() {
        emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ZERO);
        despachador.despachar();

        emitir("FA/2");
        assertThat(despachador.despachar()).isEqualTo(1);
        assertThat(esperaConfigurada()).isZero();
    }

    @Test
    void cadaObligadoTieneSuPropioTurno() {
        UUID otro = ObligadosDePrueba.nuevo(obligados);
        emitir("FA/1");
        cadena.anadir(otro, Registros.alta().build());
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));

        assertThat(despachador.despachar()).isEqualTo(2);
    }

    // --- Desenlaces ---

    @Test
    void cadaDesenlaceAterrizaEnSuFila() {
        RegistroFacturacion aceptado = emitir("FA/1");
        RegistroFacturacion conErrores = emitir("FA/2");
        RegistroFacturacion rechazado = emitir("FA/3");
        aeat.responder(EstadoEnvioAeat.PARCIALMENTE_CORRECTO, Duration.ofSeconds(60),
                linea(aceptado, EstadoRegistroAeat.CORRECTO, null),
                linea(conErrores, EstadoRegistroAeat.ACEPTADO_CON_ERRORES, 2000),
                linea(rechazado, EstadoRegistroAeat.INCORRECTO, 1130));

        despachador.despachar();

        assertThat(estadoDe(aceptado)).isEqualTo(EstadoEnvio.ACEPTADO);
        assertThat(estadoDe(conErrores)).isEqualTo(EstadoEnvio.ACEPTADO_CON_ERRORES);
        assertThat(estadoDe(rechazado)).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    /** Un `Incorrecto` con código 3000 significa que ya estaba presentado. */
    @Test
    void elCodigoDeDuplicadoNoSeGuardaComoRechazo() {
        RegistroFacturacion registro = emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.INCORRECTO, Duration.ofSeconds(60),
                linea(registro, EstadoRegistroAeat.INCORRECTO, 3000));

        despachador.despachar();

        assertThat(estadoDe(registro)).isEqualTo(EstadoEnvio.DUPLICADO);
    }

    /**
     * El caso que obliga a emparejar por tipo de operación: el alta y la anulación de la misma
     * factura comparten {@code IDFactura}, y emparejar solo por ella pegaría el desenlace al
     * registro equivocado.
     */
    @Test
    void distingueElAltaDeLaAnulacionDeLaMismaFactura() {
        // Registros.anulacion() anula justo esta factura, así que las dos filas comparten
        // IDFactura y solo el tipo de operación las separa.
        RegistroFacturacion alta = emitir("12345679/G34");
        RegistroFacturacion anulacion = cadena.anadir(obligado, Registros.anulacion());
        assertThat(idFacturaDe(alta)).isEqualTo(idFacturaDe(anulacion));

        aeat.responder(EstadoEnvioAeat.PARCIALMENTE_CORRECTO, Duration.ofSeconds(60),
                new LineaRespuesta(idFacturaDe(alta), TipoRegistro.ALTA,
                        EstadoRegistroAeat.CORRECTO, null, null),
                new LineaRespuesta(idFacturaDe(anulacion), TipoRegistro.ANULACION,
                        EstadoRegistroAeat.INCORRECTO, 1130, "Rechazada"));

        despachador.despachar();

        assertThat(estadoDe(alta)).isEqualTo(EstadoEnvio.ACEPTADO);
        assertThat(estadoDe(anulacion)).isEqualTo(EstadoEnvio.RECHAZADO);
    }

    @Test
    void unRegistroSinLineaDeRespuestaSiguePendiente() {
        RegistroFacturacion conRespuesta = emitir("FA/1");
        RegistroFacturacion sinRespuesta = emitir("FA/2");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60),
                linea(conRespuesta, EstadoRegistroAeat.CORRECTO, null));

        despachador.despachar();

        assertThat(estadoDe(conRespuesta)).isEqualTo(EstadoEnvio.ACEPTADO);
        assertThat(estadoDe(sinRespuesta)).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(intentosDe(sinRespuesta)).isEqualTo(1);
    }

    /**
     * Un fallo de transporte no es un rechazo: no sabemos si la AEAT lo registró, así que el
     * lote se queda pendiente y suma un intento.
     */
    @Test
    void siLaRemisionFallaElLoteSiguePendienteYSumaIntento() {
        RegistroFacturacion registro = emitir("FA/1");
        aeat.fallar();

        assertThat(despachador.despachar()).isZero();

        assertThat(estadoDe(registro)).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(intentosDe(registro)).isEqualTo(1);
    }

    /** Y el turno se consume igual: la norma cuenta envíos, no envíos con éxito. */
    @Test
    void unFalloDeRemisionConsumeElTurno() {
        emitir("FA/1");
        aeat.fallar();
        despachador.despachar();

        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));
        assertThat(despachador.despachar()).isZero();
    }

    // --- Concurrencia ---

    /**
     * Dos despachadores a la vez, como habrá dos instancias. El {@code for update skip locked} y
     * la reserva del turno en una sola sentencia tienen que dejar salir <strong>un</strong> lote.
     */
    @Test
    void dosDespachadoresConcurrentesNoRemitenElMismoLote() throws Exception {
        emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));

        CyclicBarrier salida = new CyclicBarrier(2);
        List<Callable<Integer>> tareas = List.of(
                () -> { salida.await(); return despachador.despachar(); },
                () -> { salida.await(); return despachador.despachar(); });

        List<Future<Integer>> resultados;
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            resultados = hilos.invokeAll(tareas);
        }
        int lotes = 0;
        for (Future<Integer> resultado : resultados) {
            lotes += resultado.get();
        }

        assertThat(lotes).isEqualTo(1);
        assertThat(aeat.lotesRemitidos()).isEqualTo(1);
    }

    // --- Apoyo ---

    private LineaRespuesta linea(RegistroFacturacion registro, EstadoRegistroAeat estado, Integer codigo) {
        return new LineaRespuesta(idFacturaDe(registro), registro.tipo(), estado, codigo,
                codigo == null ? null : "Error " + codigo);
    }

    private static IdFactura idFacturaDe(RegistroFacturacion registro) {
        return new IdFactura(registro.emisor(), registro.numSerieFactura(),
                registro.fechaExpedicionFactura());
    }

    private EstadoEnvio estadoDe(RegistroFacturacion registro) {
        return envios.findByRegistroId(registro.id()).orElseThrow().estado();
    }

    private int intentosDe(RegistroFacturacion registro) {
        return envios.findByRegistroId(registro.id()).orElseThrow().intentos();
    }

    private int esperaConfigurada() {
        return jdbc.sql("select espera_segundos from control_flujo_envio where obligado_id = :o")
                .param("o", obligado).query(Integer.class).single();
    }

    /** Sustituye al cliente real: aquí no se prueba el transporte, ya lo hace WireMock. */
    interface AeatFalsa extends ClienteAeat {
        void reiniciar();

        void fallar();

        void responder(EstadoEnvioAeat estado, Duration espera, LineaRespuesta... lineas);

        int lotesRemitidos();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AeatDeMentira {

        @Bean
        @Primary
        AeatFalsa aeatFalsa() {
            return new AeatFalsa() {
                private final List<LineaRespuesta> lineas = new ArrayList<>();
                private EstadoEnvioAeat estado = EstadoEnvioAeat.CORRECTO;
                private Duration espera = Duration.ofSeconds(60);
                private boolean falla;
                private int lotes;

                @Override
                public synchronized void reiniciar() {
                    lineas.clear();
                    falla = false;
                    lotes = 0;
                    espera = Duration.ofSeconds(60);
                }

                @Override
                public synchronized void fallar() {
                    falla = true;
                }

                @Override
                public synchronized void responder(EstadoEnvioAeat nuevoEstado, Duration nuevaEspera,
                                                   LineaRespuesta... nuevasLineas) {
                    falla = false;
                    estado = nuevoEstado;
                    espera = nuevaEspera;
                    lineas.clear();
                    lineas.addAll(List.of(nuevasLineas));
                }

                @Override
                public synchronized int lotesRemitidos() {
                    return lotes;
                }

                @Override
                public synchronized RespuestaRemision remitir(ObligadoTributario obligado,
                                                              List<String> registros) {
                    if (falla) {
                        throw new RemisionFallidaException("la AEAT no contesta en el test");
                    }
                    lotes++;
                    return new RespuestaRemision(estado, espera, "CSV-" + lotes, List.copyOf(lineas));
                }
            };
        }
    }
}
