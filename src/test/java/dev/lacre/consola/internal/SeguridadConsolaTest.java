package dev.lacre.consola.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Acceso a la consola con la credencial de {@code src/test/resources/application.properties}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadConsolaTest {

    private static final String USUARIO = "operador";
    private static final String CLAVE = "clave-de-pruebas-de-la-consola";

    @Autowired
    private MockMvc mvc;

    @Test
    void sinSesionSeRedirigeAlInicioDeSesion() throws Exception {
        mvc.perform(get("/consola")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/consola/entrar"));
    }

    @Test
    void elDetalleDeUnObligadoTambienExigeSesion() throws Exception {
        mvc.perform(get("/consola/obligados/{nif}", "89890001K")).andExpect(redirectedUrl("/consola/entrar"));
        mvc.perform(get("/consola/obligados/{nif}/cadena", "89890001K")).andExpect(redirectedUrl("/consola/entrar"));
    }

    @Test
    void laPaginaDeEntradaYSusEstilosSonPublicos() throws Exception {
        mvc.perform(get("/consola/entrar")).andExpect(status().isOk());
        mvc.perform(get("/consola/consola.css")).andExpect(status().isOk());
        mvc.perform(get("/consola/htmx.min.js")).andExpect(status().isOk());
        mvc.perform(get("/consola/logo.svg")).andExpect(status().isOk());
    }

    @Test
    void conLaCredencialBuenaSeEntra() throws Exception {
        mvc.perform(formLogin("/consola/entrar").user(USUARIO).password(CLAVE))
                .andExpect(redirectedUrl("/consola"))
                .andExpect(authenticated().withUsername(USUARIO));
    }

    @Test
    void conOtraClaveSeVuelveAlInicioConError() throws Exception {
        mvc.perform(formLogin("/consola/entrar").user(USUARIO).password("clave-que-no-es-la-buena"))
                .andExpect(redirectedUrl("/consola/entrar?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void laClaveDeLaApiNoAbreLaConsola() throws Exception {
        mvc.perform(formLogin("/consola/entrar").user(USUARIO).password("clave-de-pruebas-de-lacre-no-es-un-secreto"))
                .andExpect(unauthenticated());
    }

    @Test
    void unPostSinTokenCsrfSeRechaza() throws Exception {
        mvc.perform(post("/consola/salir").with(user(USUARIO))).andExpect(status().isForbidden());
        mvc.perform(post("/consola/entrar").param("username", USUARIO).param("password", CLAVE))
                .andExpect(status().isForbidden());
    }

    @Test
    void salirCierraLaSesion() throws Exception {
        mvc.perform(post("/consola/salir").with(user(USUARIO)).with(csrf()))
                .andExpect(redirectedUrl("/consola/entrar?salida"))
                .andExpect(unauthenticated());
    }

    @Test
    void laApiSigueProtegidaPorSuClaveYNoPorLaSesion() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID()).with(user(USUARIO)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }

    @Test
    void laSaludYElContratoSiguenAbiertos() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
    }
}
