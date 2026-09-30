package dev.lacre.api.internal.correlacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CorrelacionRestTest {

    private static final String CABECERA = FiltroDeCorrelacion.CABECERA;

    private static final UUID GENERADO = UUID.fromString("00000000-0000-0000-0000-00000000c0e1");

    private final FiltroDeCorrelacion filtro = new FiltroDeCorrelacion(() -> GENERADO);

    @Autowired
    private MockMvc mvc;

    @Test
    void sinCabeceraSeGeneraUnUuid() throws Exception {
        String devuelto = mvc.perform(
                        get("/v1/registros/{id}", UUID.randomUUID()).with(ApiDePrueba.autenticada()))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getHeader(CABECERA);

        assertThat(UUID.fromString(devuelto)).isNotNull();
    }

    @Test
    void elDelClienteSeRespeta() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID())
                        .with(ApiDePrueba.autenticada())
                        .header(CABECERA, "pedido-42.reintento_1"))
                .andExpect(header().string(CABECERA, "pedido-42.reintento_1"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "falso\n2026-01-01 INFO linea inventada",
                "con espacios",
                "",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    void unoQueNoEsSeguroDeEscribirSeSustituye(String cabecera) {
        assertThat(filtro.identificadorDe(cabecera)).isEqualTo(GENERADO.toString());
    }

    @Test
    void sesentaYCuatroCaracteresSeAdmiten() {
        String cabecera = "a".repeat(64);

        assertThat(filtro.identificadorDe(cabecera)).isEqualTo(cabecera);
    }

    @Test
    void elAvisoDeUnaPeticionSinCredencialLlevaElIdentificador(CapturedOutput salida) throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID()).header(CABECERA, "traza-sin-credencial"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CABECERA, "traza-sin-credencial"));

        assertThat(salida.getOut()).containsPattern("\\[traza-sin-credencial\\] .*Petición sin credencial válida");
    }
}
