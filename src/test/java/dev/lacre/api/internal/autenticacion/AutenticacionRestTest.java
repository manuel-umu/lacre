package dev.lacre.api.internal.autenticacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Autenticación de {@code /v1/**}: qué se rechaza sin credencial y qué sigue abierto. Se prueba
 * sobre un {@code GET} de un registro inexistente: con la clave válida la respuesta es 404.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionRestTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sinCabeceraNoSePasa() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }

    /** Misma respuesta que sin cabecera, para no dar pistas a quien pruebe claves. */
    @Test
    void conUnaClaveQueNoEsLaSuyaTampoco() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer clave-inventada-que-no-es-la-buena"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }

    /** La clave correcta con otro esquema no vale. */
    @Test
    void laClaveBuenaConOtroEsquemaNoVale() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + ApiDePrueba.CLAVE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unPrefijoDeLaClaveNoVale() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ApiDePrueba.CLAVE.substring(0, 20)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conLaClaveBuenaSePasaYLaPeticionLlegaAlControlador() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID()).with(ApiDePrueba.autenticada()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("registro-desconocido"));
    }

    /** La sonda del contenedor no lleva credencial. */
    @Test
    void laSaludSigueAbiertaParaLaSondaDelContenedor() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
