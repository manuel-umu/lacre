package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.CertificadoNoDisponibleException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.ConsultaAeat;
import dev.lacre.remision.RegistroEnAeat;
import dev.lacre.remision.ResultadoConsulta;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.internal.adaptador.RegistroFacturacion;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.Registros;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** El cotejo de la consola contra lo guardado en Postgres y una AEAT que responde lo que diga cada test. */
@Import({TestcontainersConfiguration.class, CotejoConsolaTest.AeatDeMentira.class})
@SpringBootTest
@AutoConfigureMockMvc
class CotejoConsolaTest {

    private static final YearMonth PERIODO = YearMonth.from(Registros.FECHA_EMITIBLE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private AeatFalsa aeat;

    private UUID obligado;
    private String nif;

    @BeforeEach
    void preparar() {
        obligado = ObligadosDePrueba.nuevo(obligados);
        nif = obligados.findById(obligado).orElseThrow().nif().valor();
        aeat.periodosPedidos.clear();
        aeat.responde = (obligado, periodo) -> new ResultadoConsulta(List.of(), true);
    }

    @Test
    void cruzaLoPresentadoConLoAceptadoEnLacre() throws Exception {
        RegistroFacturacion coincide = aceptada("FA/1");
        aceptada("FA/2");
        RegistroFacturacion soloEnAeat = aceptada("FA/3");
        aeat.responde = (obligado, periodo) -> new ResultadoConsulta(
                List.of(
                        enAeat(coincide.numSerieFactura(), coincide),
                        new RegistroEnAeat(
                                Registros.idFacturaEmitible("OTRO-SIF/9"),
                                soloEnAeat.huella(),
                                RegistroEnAeat.Estado.CORRECTO,
                                null,
                                null,
                                null)),
                true);

        Cotejo cotejo = cotejar(PERIODO.toString());

        assertThat(cotejo.coinciden()).extracting(IdFactura::numSerieFactura).containsExactly("FA/1");
        assertThat(cotejo.soloEnAeat())
                .extracting(r -> r.idFactura().numSerieFactura())
                .containsExactly("OTRO-SIF/9");
        assertThat(cotejo.soloEnLacre())
                .extracting(r -> r.idFactura().numSerieFactura())
                .containsExactly("FA/2", "FA/3");
        assertThat(aeat.periodosPedidos).containsExactly(PERIODO);
    }

    @Test
    void sinCertificadoSeExplicaYNoRevienta() throws Exception {
        aeat.responde = (obligado, periodo) -> {
            throw new CertificadoNoDisponibleException(obligado.nif(), "no hay fichero");
        };

        mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)
                        .param("periodo", PERIODO.toString())
                        .with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No hay certificado con el que consultar a la AEAT")));
    }

    @Test
    void unPeriodoMalFormadoNoLlegaALaAeat() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)
                        .param("periodo", "septiembre")
                        .with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AAAA-MM")));
        assertThat(aeat.periodosPedidos).isEmpty();
    }

    @Test
    void elSelectorMandaAnioYMes() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)
                        .param("anio", String.valueOf(PERIODO.getYear()))
                        .param("mes", String.valueOf(PERIODO.getMonthValue()))
                        .with(user("operador")))
                .andExpect(status().isOk());
        assertThat(aeat.periodosPedidos).containsExactly(PERIODO);
    }

    @Test
    void unMesQueNoExisteNoLlegaALaAeat() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)
                        .param("anio", "2026")
                        .param("mes", "13")
                        .with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AAAA-MM")));
        assertThat(aeat.periodosPedidos).isEmpty();
    }

    @Test
    void unMesFuturoNoLlegaALaAeat() throws Exception {
        String siguiente = YearMonth.now().plusMonths(2).toString();

        mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)
                        .param("periodo", siguiente)
                        .with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("posterior al mes en curso")));
        assertThat(aeat.periodosPedidos).isEmpty();
    }

    @Test
    void elCotejoExigeSesion() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)).andExpect(status().is3xxRedirection());
    }

    private Cotejo cotejar(String periodo) throws Exception {
        MvcResult resultado = mvc.perform(get("/consola/obligados/{nif}/cotejo", nif)
                        .param("periodo", periodo)
                        .with(user("operador")))
                .andExpect(status().isOk())
                .andReturn();
        return (Cotejo) resultado.getModelAndView().getModel().get("cotejo");
    }

    private RegistroFacturacion aceptada(String numSerie) {
        RegistroFacturacion registro = cadena.anadir(
                obligado,
                Registros.emitible()
                        .idFactura(Registros.idFacturaEmitible(numSerie))
                        .build());
        TestcontainersConfiguration.comoPropietario(postgres)
                .sql("update envio_registro set estado = 'ACEPTADO', enviado_en = now() where registro_id = :id")
                .param("id", registro.id())
                .update();
        return registro;
    }

    private static RegistroEnAeat enAeat(String numSerie, RegistroFacturacion registro) {
        return new RegistroEnAeat(
                Registros.idFacturaEmitible(numSerie),
                registro.huella(),
                RegistroEnAeat.Estado.CORRECTO,
                null,
                null,
                null);
    }

    /** Responde lo que el test le diga y apunta los periodos que se le piden. */
    static class AeatFalsa implements ConsultaAeat {

        BiFunction<ObligadoTributario, YearMonth, ResultadoConsulta> responde =
                (obligado, periodo) -> new ResultadoConsulta(List.of(), true);
        final List<YearMonth> periodosPedidos = new ArrayList<>();

        @Override
        public ResultadoConsulta consultar(ObligadoTributario obligado, YearMonth periodo) {
            periodosPedidos.add(periodo);
            return responde.apply(obligado, periodo);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AeatDeMentira {

        @Bean
        @Primary
        AeatFalsa aeatFalsa() {
            return new AeatFalsa();
        }
    }
}
