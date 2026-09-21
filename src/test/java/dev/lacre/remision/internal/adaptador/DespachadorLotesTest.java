package dev.lacre.remision.internal.adaptador;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.CertificadoNoDisponibleException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.EnvioRechazadoException;
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
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
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
 * El despachador contra Postgres real y un cliente de la AEAT falso: control de flujo,
 * concurrencia y desenlaces.
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
     * El outbox se vacía entre tests porque el contenedor se reutiliza.
     * {@code registro_facturacion} es de solo inserción y no se toca.
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
                Registros.emitible().idFactura(Registros.idFacturaEmitible(numSerie)).build());
    }

    // --- Control de flujo, art. 16.2 ---

    @Test
    void elPrimerEnvioDeUnObligadoSaleSinEsperar() {
        emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));

        assertThat(despachador.despachar()).isEqualTo(1);
        assertThat(aeat.lotesRemitidos()).isEqualTo(1);
    }

    /** El turno recién consumido vale 60 segundos: la segunda pasada no remite. */
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

    /** El tiempo de espera que devuelve la AEAT sustituye al anterior. */
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
        cadena.anadir(otro, Registros.emitible().build());
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
     * El alta y la anulación de la misma factura comparten {@code IDFactura}; se emparejan por
     * tipo de operación.
     */
    @Test
    void distingueElAltaDeLaAnulacionDeLaMismaFactura() {
        RegistroFacturacion alta = emitir("12345679/G34");
        RegistroFacturacion anulacion = cadena.anadir(obligado, new DatosRegistroAnulacion(
                Registros.idFacturaEmitible("12345679/G34"), null, false, false, null, null,
                Registros.sistemaInformatico()));
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

    /** Un fallo de transporte deja el lote pendiente y suma un intento. */
    @Test
    void siLaRemisionFallaElLoteSiguePendienteYSumaIntento() {
        RegistroFacturacion registro = emitir("FA/1");
        aeat.fallar();

        assertThat(despachador.despachar()).isZero();

        assertThat(estadoDe(registro)).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(intentosDe(registro)).isEqualTo(1);
    }

    /** Un rechazo del envío entero llega con código; sin guardarlo, solo estaría en el log. */
    @Test
    void unRechazoDelEnvioCompletoDejaSuCodigoEnLaFila() {
        RegistroFacturacion registro = emitir("FA/1");
        aeat.rechazarElEnvio(4104);

        assertThat(despachador.despachar()).isZero();

        EnvioRegistro envio = envios.findByRegistroId(registro.id()).orElseThrow();
        assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(envio.codigoError()).isEqualTo(4104);
        assertThat(envio.descripcionError()).contains("4104");
        assertThat(envio.intentos()).isEqualTo(1);
    }

    /**
     * Falla el primer obligado de la pasada, sea cual sea: los obligados se recorren por
     * identificador, así que fijar cuál fallaría haría que el test pasara por casualidad.
     */
    @Test
    void unFalloInesperadoEnUnObligadoNoImpideDespacharALosDemas() {
        RegistroFacturacion delPrimero = emitir("FA/1");
        RegistroFacturacion delSegundo =
                cadena.anadir(ObligadosDePrueba.nuevo(obligados), Registros.emitible().build());
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60),
                linea(delPrimero, EstadoRegistroAeat.CORRECTO, null),
                linea(delSegundo, EstadoRegistroAeat.CORRECTO, null));
        aeat.fallarSoloLaPrimeraLlamadaCon(new IllegalStateException("fallo inesperado"));

        assertThat(despachador.despachar()).isEqualTo(1);

        assertThat(List.of(estadoDe(delPrimero), estadoDe(delSegundo)))
                .containsExactlyInAnyOrder(EstadoEnvio.ACEPTADO, EstadoEnvio.PENDIENTE);
    }

    /** Sin certificado no hay envío, pero el motivo queda en la fila, que es donde se consulta. */
    @Test
    void unObligadoSinCertificadoDejaElMotivoEnLaFila() {
        RegistroFacturacion registro = emitir("FA/1");
        aeat.fallarSoloLaPrimeraLlamadaCon(
                new CertificadoNoDisponibleException("89890001K", "no hay fichero"));

        assertThat(despachador.despachar()).isZero();

        EnvioRegistro envio = envios.findByRegistroId(registro.id()).orElseThrow();
        assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(envio.intentos()).isEqualTo(1);
        assertThat(envio.codigoError()).isNull();
        assertThat(envio.descripcionError())
                .isEqualTo("Certificado no disponible (89890001K): no hay fichero");
    }

    /** El turno se confirma antes de enviar, así que se consume igual: se reintenta al vencer. */
    @Test
    void unObligadoSinCertificadoSeReintentaCuandoVenceSuTurno() {
        RegistroFacturacion registro = emitir("FA/1");
        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60),
                linea(registro, EstadoRegistroAeat.CORRECTO, null));
        aeat.fallarSoloLaPrimeraLlamadaCon(
                new CertificadoNoDisponibleException("89890001K", "no hay fichero"));

        assertThat(despachador.despachar()).isZero();
        assertThat(despachador.despachar()).isZero();

        // Se envejece el turno en vez de dormir 60 segundos.
        jdbc.sql("update control_flujo_envio set ultimo_envio = ultimo_envio - interval '61 seconds'")
                .update();

        assertThat(despachador.despachar()).isEqualTo(1);
        assertThat(estadoDe(registro)).isEqualTo(EstadoEnvio.ACEPTADO);
    }

    /** El esquema de la respuesta admite descripciones de hasta 1500 caracteres. */
    @Test
    void unRechazoConUnaDescripcionLargaDeLaAeatAterrizaEnSuFila() {
        RegistroFacturacion registro = emitir("FA/1");
        String larga = "x".repeat(1500);
        aeat.responder(EstadoEnvioAeat.PARCIALMENTE_CORRECTO, Duration.ofSeconds(60),
                new LineaRespuesta(idFacturaDe(registro), registro.tipo(),
                        EstadoRegistroAeat.INCORRECTO, 1100, larga));

        despachador.despachar();

        EnvioRegistro envio = envios.findByRegistroId(registro.id()).orElseThrow();
        assertThat(envio.estado()).isEqualTo(EstadoEnvio.RECHAZADO);
        assertThat(envio.descripcionError()).isEqualTo(larga);
    }

    /** El turno se consume igual: la norma cuenta envíos, no éxitos. */
    @Test
    void unFalloDeRemisionConsumeElTurno() {
        emitir("FA/1");
        aeat.fallar();
        despachador.despachar();

        aeat.responder(EstadoEnvioAeat.CORRECTO, Duration.ofSeconds(60));
        assertThat(despachador.despachar()).isZero();
    }

    // --- Concurrencia ---

    /** Dos despachadores concurrentes deben dejar salir un solo lote. */
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

    /** Sustituye al cliente real; el transporte lo prueba WireMock. */
    interface AeatFalsa extends ClienteAeat {
        void reiniciar();

        void fallar();

        void rechazarElEnvio(int codigo);

        void fallarSoloLaPrimeraLlamadaCon(RuntimeException fallo);

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
                private RemisionFallidaException fallo;
                private RuntimeException falloDeLaPrimeraLlamada;
                private int lotes;

                @Override
                public synchronized void reiniciar() {
                    lineas.clear();
                    fallo = null;
                    falloDeLaPrimeraLlamada = null;
                    lotes = 0;
                    espera = Duration.ofSeconds(60);
                }

                @Override
                public synchronized void fallar() {
                    fallo = new RemisionFallidaException("la AEAT no contesta en el test");
                }

                @Override
                public synchronized void fallarSoloLaPrimeraLlamadaCon(RuntimeException fallo) {
                    falloDeLaPrimeraLlamada = fallo;
                }

                @Override
                public synchronized void rechazarElEnvio(int codigo) {
                    fallo = new EnvioRechazadoException(codigo, "rechazo del envío en el test");
                }

                @Override
                public synchronized void responder(EstadoEnvioAeat nuevoEstado, Duration nuevaEspera,
                                                   LineaRespuesta... nuevasLineas) {
                    fallo = null;
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
                    if (falloDeLaPrimeraLlamada != null) {
                        RuntimeException unaVez = falloDeLaPrimeraLlamada;
                        falloDeLaPrimeraLlamada = null;
                        throw unaVez;
                    }
                    if (fallo != null) {
                        throw fallo;
                    }
                    lotes++;
                    return new RespuestaRemision(estado, espera, "CSV-" + lotes, List.copyOf(lineas));
                }
            };
        }
    }
}
