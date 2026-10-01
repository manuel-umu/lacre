package dev.lacre.consola.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "lacre.consola.clave=")
@AutoConfigureMockMvc
class ConsolaSinClaveTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laConsolaNoExiste() throws Exception {
        mvc.perform(get("/consola")).andExpect(status().isNotFound());
        mvc.perform(get("/consola/entrar")).andExpect(status().isNotFound());
        mvc.perform(formLogin("/consola/entrar").user("lacre").password("")).andExpect(status().isNotFound());
    }

    @Test
    void laApiArrancaIgual() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
