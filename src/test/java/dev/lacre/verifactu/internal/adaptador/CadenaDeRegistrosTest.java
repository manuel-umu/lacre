package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.shared.Huella;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La cadena contra Postgres real y bajo concurrencia: sin el cerrojo consultivo, dos hilos
 * encadenarían contra la misma huella anterior y la cadena se bifurcaría.
 */
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class CadenaDeRegistrosTest {

    private static final int HILOS = 16;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Obligados obligados;

    private UUID obligado;

    @BeforeEach
    void crearObligado() {
        obligado = ObligadosDePrueba.nuevo(obligados);
    }

    @Test
    void elPrimerRegistroAbreLaCadenaEnLaPosicionUno() {
        RegistroFacturacion registro = cadena.anadir(obligado, Registros.emitible().build());

        assertThat(registro.posicion()).isEqualTo(1);
        assertThat(registro.huellaAnterior()).isNull();
        assertThat(registro.xml()).startsWith("<sf:RegistroAlta");
    }

    @Test
    void elSegundoRegistroEnlazaConLaHuellaDelPrimero() {
        RegistroFacturacion primero = cadena.anadir(obligado, Registros.emitible().build());
        RegistroFacturacion segundo = cadena.anadir(obligado,
                Registros.emitible().idFactura(Registros.idFacturaEmitible("FA/2")).build());

        assertThat(segundo.posicion()).isEqualTo(2);
        assertThat(segundo.huellaAnterior()).isEqualTo(primero.huella());
        assertThat(segundo.xml()).contains(primero.huella().valor());
    }

    @Test
    void cadaObligadoTieneSuPropiaCadena() {
        UUID otro = ObligadosDePrueba.nuevo(obligados);

        cadena.anadir(obligado, Registros.emitible().build());
        RegistroFacturacion primeroDelOtro = cadena.anadir(otro, Registros.emitible().build());

        assertThat(primeroDelOtro.posicion()).isEqualTo(1);
        assertThat(primeroDelOtro.huellaAnterior()).isNull();
    }

    @Test
    void laFechaConservaSuHusoOriginalAlLeerlaDeLaBaseDeDatos() {
        RegistroFacturacion guardado = cadena.anadir(obligado, Registros.emitible().build());

        RegistroFacturacion leido = jdbc.sql("""
                select fecha_hora_huso_gen_registro, huso_offset_segundos
                from registro_facturacion where id = :id
                """)
                .param("id", guardado.id())
                .query((rs, fila) -> new RegistroFacturacion(guardado.id(), obligado, 1,
                        guardado.tipo(), guardado.emisor(), guardado.numSerieFactura(),
                        guardado.fechaExpedicionFactura(), guardado.huella(), null,
                        rs.getObject("fecha_hora_huso_gen_registro", java.time.OffsetDateTime.class)
                                .withOffsetSameInstant(java.time.ZoneOffset.ofTotalSeconds(
                                        rs.getInt("huso_offset_segundos"))),
                        rs.getInt("huso_offset_segundos"), guardado.xml()))
                .single();

        assertThat(leido.fechaHoraConSuHusoOriginal())
                .isEqualTo(guardado.fechaHoraConSuHusoOriginal());
        assertThat(leido.fechaHoraConSuHusoOriginal().getOffset().getTotalSeconds())
                .isEqualTo(guardado.husoOffsetSegundos());
    }

    /** La zona horaria es dato del obligado y entra en la huella: Canarias va una hora por detrás. */
    @Test
    void cadaObligadoFechaSusRegistrosConSuPropiaZona() {
        UUID canario = ObligadosDePrueba.nuevo(obligados, ObligadosDePrueba.CANARIAS);

        RegistroFacturacion peninsular = cadena.anadir(obligado, Registros.emitible().build());
        RegistroFacturacion enCanarias = cadena.anadir(canario, Registros.emitible().build());

        // Canarias va una hora por detrás todo el año.
        assertThat(peninsular.husoOffsetSegundos() - enCanarias.husoOffsetSegundos())
                .isEqualTo(3600);
    }

    @Test
    void facturarPorUnObligadoQueNoExisteFallaConUnErrorDelDominio() {
        assertThatThrownBy(() -> cadena.anadir(UUID.randomUUID(), Registros.emitible().build()))
                .isInstanceOf(ObligadoDesconocidoException.class);
    }

    // --- Comprobación previa del art. 7.i de la OM HAC/1177/2024 ---

    /**
     * La comprobación previa avisa y no impide emitir. La cadena se rompe con un {@code INSERT}
     * directo: un {@code UPDATE} lo pararía el trigger.
     */
    @Test
    void unaCadenaRotaSeDenunciaPeroNoImpideFacturar(CapturedOutput salida) {
        RegistroFacturacion primero = cadena.anadir(obligado, Registros.emitible().build());
        insertarEslabonRoto(primero);

        RegistroFacturacion tercero = cadena.anadir(obligado,
                Registros.emitible().idFactura(Registros.idFacturaEmitible("FA/3")).build());

        assertThat(tercero.posicion()).isEqualTo(3);
        assertThat(salida).contains("HUELLA_ANTERIOR_NO_CUADRA")
                .contains("art. 7.i");
    }

    /** El eslabón enlaza por huella, pero su XML dice venir de otra factura. */
    @Test
    void unAnteriorQueDeclaraOtraFacturaSeDenuncia(CapturedOutput salida) {
        RegistroFacturacion primero = cadena.anadir(obligado, Registros.emitible().build());
        insertarEslabon(primero, Registros.idFacturaEmitible("FA/OTRA"), primero.huella());

        cadena.anadir(obligado,
                Registros.emitible().idFactura(Registros.idFacturaEmitible("FA/3")).build());

        assertThat(salida).contains("IDENTIFICACION_ANTERIOR_NO_CUADRA")
                .doesNotContain("HUELLA_ANTERIOR_NO_CUADRA");
    }

    @Test
    void unaCadenaSanaNoDenunciaNada(CapturedOutput salida) {
        cadena.anadir(obligado, Registros.emitible().build());
        cadena.anadir(obligado,
                Registros.emitible().idFactura(Registros.idFacturaEmitible("FA/2")).build());

        assertThat(salida).doesNotContain("art. 7.i");
    }

    /** Una fila válida salvo por su huella anterior, que no es la del registro que le precede. */
    private void insertarEslabonRoto(RegistroFacturacion anterior) {
        insertarEslabon(anterior, new IdFactura(anterior.emisor(), anterior.numSerieFactura(),
                anterior.fechaExpedicionFactura()), new Huella("D".repeat(64)));
    }

    /** Segundo eslabón, cuyo XML declara como anterior la factura y la huella indicadas. */
    private void insertarEslabon(RegistroFacturacion anterior, IdFactura declarada,
                                 Huella huellaAnterior) {
        String xml = EscritorRegistro.escribir(new RegistroEncadenado(
                Registros.emitible().idFactura(Registros.idFacturaEmitible("FA/2")).build(),
                Optional.of(new RegistroAnterior(declarada, huellaAnterior)),
                anterior.fechaHoraConSuHusoOriginal(), new Huella("C".repeat(64))));
        jdbc.sql("""
                insert into registro_facturacion (
                    id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                    fecha_expedicion_factura, huella, huella_anterior,
                    fecha_hora_huso_gen_registro, huso_offset_segundos, xml)
                values (:id, :obligado, 2, 'ALTA', :emisor, 'FA/2', :fecha,
                        :huella, :huellaAnterior, :fechaHora, :huso, :xml)
                """)
                .param("id", UUID.randomUUID())
                .param("obligado", obligado)
                .param("emisor", anterior.emisor().valor())
                .param("fecha", anterior.fechaExpedicionFactura())
                .param("huella", "C".repeat(64))
                .param("huellaAnterior", huellaAnterior.valor())
                .param("fechaHora", anterior.fechaHoraConSuHusoOriginal())
                .param("huso", anterior.husoOffsetSegundos())
                .param("xml", xml)
                .update();
    }

    // --- Concurrencia ---

    @Test
    void dieciseisHilosConcurrentesNoBifurcanLaCadena() throws Exception {
        CyclicBarrier salida = new CyclicBarrier(HILOS);
        List<Callable<RegistroFacturacion>> tareas = IntStream.range(0, HILOS)
                .<Callable<RegistroFacturacion>>mapToObj(i -> () -> {
                    salida.await();
                    return cadena.anadir(obligado, Registros.emitible()
                            .idFactura(Registros.idFacturaEmitible("FA/" + i)).build());
                })
                .toList();

        List<Future<RegistroFacturacion>> resultados;
        try (ExecutorService hilos = Executors.newFixedThreadPool(HILOS)) {
            resultados = hilos.invokeAll(tareas);
        }

        List<RegistroFacturacion> registros = new ArrayList<>();
        for (Future<RegistroFacturacion> resultado : resultados) {
            registros.add(resultado.get());
        }

        assertThat(registros).hasSize(HILOS);
        assertThat(registros).extracting(RegistroFacturacion::posicion)
                .containsExactlyInAnyOrderElementsOf(
                        IntStream.rangeClosed(1, HILOS).mapToObj(Long::valueOf).toList());

        cadenaIntacta();
    }

    /** Cada registro apunta a la huella del anterior; solo el primero no tiene anterior. */
    private void cadenaIntacta() {
        List<Map<String, Object>> cadenaGuardada = jdbc.sql("""
                select posicion, huella, huella_anterior
                from registro_facturacion
                where obligado_id = :obligado
                order by posicion
                """)
                .param("obligado", obligado)
                .query()
                .listOfRows();

        assertThat(cadenaGuardada).hasSize(HILOS);
        assertThat(cadenaGuardada.getFirst().get("huella_anterior")).isNull();

        for (int i = 1; i < cadenaGuardada.size(); i++) {
            Huella anterior = new Huella((String) cadenaGuardada.get(i).get("huella_anterior"));
            Huella laDelAnterior = new Huella((String) cadenaGuardada.get(i - 1).get("huella"));

            assertThat(anterior)
                    .withFailMessage("La cadena se bifurcó en la posición %s", i + 1)
                    .isEqualTo(laDelAnterior);
        }
    }
}
