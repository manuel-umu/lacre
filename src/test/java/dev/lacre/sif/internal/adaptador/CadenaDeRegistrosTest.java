package dev.lacre.sif.internal.adaptador;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.shared.Huella;
import dev.lacre.sif.registro.Registros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La cadena bajo concurrencia real, contra Postgres real.
 * <p>
 * Es el segundo test que exige {@code CLAUDE.md}, y el que justifica el cerrojo consultivo: sin
 * él, dos hilos leen el mismo último registro, calculan la misma posición y encadenan los dos
 * contra la misma huella anterior. La cadena se bifurca, que es justo lo que el RD 1007/2023
 * prohíbe.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CadenaDeRegistrosTest {

    private static final int HILOS = 16;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private JdbcClient jdbc;

    /** El contenedor se reutiliza entre tests, y el NIF del obligado es único en la tabla. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private UUID obligado;

    @BeforeEach
    void crearObligado() {
        obligado = nuevoObligado();
    }

    private UUID nuevoObligado() {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into obligado (id, nif, nombre_razon) values (:id, :nif, :nombre)")
                .param("id", id)
                .param("nif", "%08dZ".formatted(SECUENCIA.incrementAndGet()))
                .param("nombre", "Obligado de prueba SL")
                .update();
        return id;
    }

    @Test
    void elPrimerRegistroAbreLaCadenaEnLaPosicionUno() {
        RegistroFacturacion registro = cadena.anadir(obligado, Registros.alta().build());

        assertThat(registro.posicion()).isEqualTo(1);
        assertThat(registro.huellaAnterior()).isNull();
        assertThat(registro.xml()).startsWith("<sf:RegistroAlta");
    }

    @Test
    void elSegundoRegistroEnlazaConLaHuellaDelPrimero() {
        RegistroFacturacion primero = cadena.anadir(obligado, Registros.alta().build());
        RegistroFacturacion segundo = cadena.anadir(obligado,
                Registros.alta().idFactura(Registros.idFactura("FA/2")).build());

        assertThat(segundo.posicion()).isEqualTo(2);
        assertThat(segundo.huellaAnterior()).isEqualTo(primero.huella());
        assertThat(segundo.xml()).contains(primero.huella().valor());
    }

    @Test
    void cadaObligadoTieneSuPropiaCadena() {
        UUID otro = nuevoObligado();

        cadena.anadir(obligado, Registros.alta().build());
        RegistroFacturacion primeroDelOtro = cadena.anadir(otro, Registros.alta().build());

        assertThat(primeroDelOtro.posicion()).isEqualTo(1);
        assertThat(primeroDelOtro.huellaAnterior()).isNull();
    }

    @Test
    void laFechaConservaSuHusoOriginalAlLeerlaDeLaBaseDeDatos() {
        RegistroFacturacion guardado = cadena.anadir(obligado, Registros.alta().build());

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

    // --- El test que justifica el cerrojo ---

    @Test
    void dieciseisHilosConcurrentesNoBifurcanLaCadena() throws Exception {
        CyclicBarrier salida = new CyclicBarrier(HILOS);
        List<Callable<RegistroFacturacion>> tareas = IntStream.range(0, HILOS)
                .<Callable<RegistroFacturacion>>mapToObj(i -> () -> {
                    salida.await();
                    return cadena.anadir(obligado,
                            Registros.alta().idFactura(Registros.idFactura("FA/" + i)).build());
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

    /**
     * Recorre la cadena guardada de principio a fin: cada registro debe apuntar a la huella del
     * que ocupa la posición anterior, y solo el primero puede no tener anterior.
     */
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
