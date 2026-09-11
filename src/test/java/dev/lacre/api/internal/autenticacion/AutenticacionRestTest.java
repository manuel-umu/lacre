package dev.lacre.api.internal.autenticacion;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La puerta: qué pasa en {@code /v1/**} sin credencial, y qué sigue abierto a propósito.
 * <p>
 * Se prueba sobre un {@code GET} de un registro que no existe, para que el 401 no pueda
 * confundirse con el éxito de nada: si la clave vale, la respuesta es 404, y ese contraste es
 * justo lo que demuestra que el filtro dejó pasar.
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

    /**
     * Misma respuesta que sin cabecera, y es deliberado: si una clave incorrecta se distinguiera
     * de una ausente, la API sería un oráculo para quien esté probando claves.
     */
    @Test
    void conUnaClaveQueNoEsLaSuyaTampoco() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer clave-inventada-que-no-es-la-buena"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }

    /** La clave correcta con el esquema equivocado no vale: el contrato dice {@code Bearer}. */
    @Test
    void laClaveBuenaConOtroEsquemaNoVale() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + ApiDePrueba.CLAVE))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Un prefijo de la clave buena tampoco: lo obvio, pero es lo que se rompería si alguien
     * cambiase la comparación por un {@code startsWith} para «ser tolerante».
     */
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

    /**
     * La sonda del contenedor no lleva credencial y tiene que seguir contestando. Por eso el
     * filtro se registra acotado a {@code /v1/*} y no como {@code @Component}, que Spring Boot
     * aplicaría a todas las rutas.
     */
    @Test
    void laSaludSigueAbiertaParaLaSondaDelContenedor() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
