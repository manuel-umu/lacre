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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DetalleConsolaTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private PostgreSQLContainer postgres;

    private String nif;

    /** Tres altas: una pendiente tras dos intentos fallidos, una rechazada y una aceptada. */
    @BeforeEach
    void preparar() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);
        nif = obligados.findById(obligado).orElseThrow().nif().valor();
        UUID pendiente = emitir(obligado, "FA/PENDIENTE");
        UUID rechazada = emitir(obligado, "FA/RECHAZADA");
        UUID aceptada = emitir(obligado, "FA/ACEPTADA");

        JdbcClient jdbc = TestcontainersConfiguration.comoPropietario(postgres);
        jdbc.sql("""
                        update envio_registro
                        set intentos = 2, codigo_error = 4102, descripcion_error = 'Timeout de la AEAT'
                        where registro_id = :id
                        """).param("id", pendiente).update();
        jdbc.sql("""
                        update envio_registro
                        set estado = 'RECHAZADO', enviado_en = now(), codigo_error = 1100,
                            descripcion_error = 'Valor o tipo incorrecto del campo: NumSerieFactura'
                        where registro_id = :id
                        """).param("id", rechazada).update();
        jdbc.sql("update envio_registro set estado = 'ACEPTADO', enviado_en = now() where registro_id = :id")
                .param("id", aceptada)
                .update();
    }

    @Test
    void muestraLosPendientesYLosErroresConSuFactura() throws Exception {
        var resultado = mvc.perform(get("/consola/obligados/{nif}", nif).with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FA/PENDIENTE")))
                .andExpect(content().string(containsString("Timeout de la AEAT")))
                .andExpect(content().string(containsString("FA/RECHAZADA")))
                .andExpect(content().string(containsString("Valor o tipo incorrecto del campo: NumSerieFactura")))
                .andExpect(content().string(not(containsString("FA/ACEPTADA"))))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<FilaEnvio> pendientes =
                (List<FilaEnvio>) resultado.getModelAndView().getModel().get("pendientes");
        assertThat(pendientes).singleElement().satisfies(fila -> {
            assertThat(fila.factura()).isEqualTo("FA/PENDIENTE");
            assertThat(fila.envio().intentos()).isEqualTo(2);
            assertThat(fila.envio().codigoError()).isEqualTo(4102);
            assertThat(fila.registro().posicion()).isEqualTo(1);
            assertThat(fila.tipo()).isEqualTo("Alta");
        });
    }

    @Test
    void laVerificacionDeUnaCadenaBuenaDiceQueEstaIntegra() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}/cadena", nif).with(user("operador")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Íntegra: 3 registros, con las huellas recalculadas")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void unNifQueNoEstaDadoDeAltaNoExiste() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}", "00000000T").with(user("operador")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/consola/obligados/{nif}/cadena", "00000000T").with(user("operador")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unNifInvalidoTampoco() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}", "no-es-un-nif").with(user("operador")))
                .andExpect(status().isNotFound());
    }

    private UUID emitir(UUID obligado, String numSerie) {
        return cadena.anadir(
                        obligado,
                        Registros.emitible()
                                .idFactura(Registros.idFacturaEmitible(numSerie))
                                .build())
                .id();
    }
}
