package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InicioConsolaTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private PostgreSQLContainer postgres;

    private String conCadena;
    private String sinCadena;

    /** Uno con tres registros y un pendiente de hace dos horas, y otro recién dado de alta. */
    @BeforeEach
    void preparar() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);
        for (String numSerie : new String[] {"FA/1", "FA/2", "FA/3"}) {
            cadena.anadir(
                    obligado,
                    Registros.emitible()
                            .idFactura(Registros.idFacturaEmitible(numSerie))
                            .build());
        }
        TestcontainersConfiguration.comoPropietario(postgres)
                .sql("update envio_registro set creado_en = now() - interval '2 hours' where obligado_id = :obligado")
                .param("obligado", obligado)
                .update();
        conCadena = nifDe(obligado);
        sinCadena = nifDe(ObligadosDePrueba.nuevo(obligados));
    }

    @Test
    void laVistaGeneralMuestraCadaObligadoConSuCadenaYSusEnvios() throws Exception {
        MvcResult resultado = mvc.perform(get("/consola").with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"atascado\"")))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<FilaObligado> filas =
                (List<FilaObligado>) resultado.getModelAndView().getModel().get("filas");
        FilaObligado conRegistros = filaDe(filas, conCadena);
        FilaObligado sinRegistros = filaDe(filas, sinCadena);

        assertThat(conRegistros.ultimaPosicion()).isEqualTo(3L);
        assertThat(conRegistros.envios().pendientes()).isEqualTo(3);
        assertThat(conRegistros.atascado()).isTrue();
        assertThat(sinRegistros.ultimaPosicion()).isNull();
        assertThat(sinRegistros.envios().pendientes()).isZero();
        assertThat(sinRegistros.atascado()).isFalse();
    }

    @Test
    void elFragmentoQueRefrescaHtmxEsSoloLaTabla() throws Exception {
        mvc.perform(get("/consola/obligados").with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(conCadena)))
                .andExpect(content().string(not(containsString("<html"))));
    }

    private String nifDe(UUID obligado) {
        return obligados.findById(obligado).orElseThrow().nif().valor();
    }

    private static FilaObligado filaDe(List<FilaObligado> filas, String nif) {
        return filas.stream().filter(fila -> fila.nif().equals(nif)).findFirst().orElseThrow();
    }
}
