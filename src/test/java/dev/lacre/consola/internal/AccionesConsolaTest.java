package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.Envios;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccionesConsolaTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private Envios envios;

    private UUID obligado;
    private String nif;
    private EnvioRegistro envio;

    @BeforeEach
    void preparar() {
        obligado = ObligadosDePrueba.nuevo(obligados);
        nif = obligados.findById(obligado).orElseThrow().nif().valor();
        envio = emitir("FA/1");
    }

    @Test
    void sinTokenCsrfNoSeAparta() throws Exception {
        mvc.perform(post(ruta("apartar")).with(user("operador"))).andExpect(status().isForbidden());

        assertThat(estado()).isEqualTo(EstadoEnvio.PENDIENTE);
    }

    @Test
    void seApartaYSeReanudaYSeVuelveAlDetalleConElResultado() throws Exception {
        mvc.perform(post(ruta("apartar")).with(user("operador")).with(csrf()))
                .andExpect(redirectedUrl("/consola/obligados/" + nif))
                .andExpect(flash().attributeExists("mensaje"));
        assertThat(estado()).isEqualTo(EstadoEnvio.APARTADO);

        mvc.perform(post(ruta("reanudar")).with(user("operador")).with(csrf()))
                .andExpect(redirectedUrl("/consola/obligados/" + nif));
        assertThat(estado()).isEqualTo(EstadoEnvio.PENDIENTE);
    }

    @Test
    void unaTransicionQueYaNoValeSeExplicaSinRomper() throws Exception {
        mvc.perform(post(ruta("reanudar")).with(user("operador")).with(csrf()))
                .andExpect(redirectedUrl("/consola/obligados/" + nif))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void todosLosPendientesDeUnaVez() throws Exception {
        emitir("FA/2");

        mvc.perform(post("/consola/obligados/{nif}/apartar-pendientes", nif)
                        .with(user("operador"))
                        .with(csrf()))
                .andExpect(flash().attribute("mensaje", "2 envíos apartados."));
        mvc.perform(post("/consola/obligados/{nif}/reanudar-apartados", nif)
                        .with(user("operador"))
                        .with(csrf()))
                .andExpect(flash().attribute("mensaje", "2 envíos reanudados."));
    }

    @Test
    void unEnvioDeOtroObligadoEs404() throws Exception {
        String otro = obligados
                .findById(ObligadosDePrueba.nuevo(obligados))
                .orElseThrow()
                .nif()
                .valor();

        mvc.perform(post("/consola/obligados/{nif}/envios/{envio}/apartar", otro, envio.id())
                        .with(user("operador"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(estado()).isEqualTo(EstadoEnvio.PENDIENTE);
    }

    private String ruta(String accion) {
        return "/consola/obligados/" + nif + "/envios/" + envio.id() + "/" + accion;
    }

    private EstadoEnvio estado() {
        return envios.findById(envio.id()).orElseThrow().estado();
    }

    private EnvioRegistro emitir(String numSerie) {
        UUID registro = cadena.anadir(
                        obligado,
                        Registros.emitible()
                                .idFactura(Registros.idFacturaEmitible(numSerie))
                                .build())
                .id();
        return envios.findByRegistroId(registro).orElseThrow();
    }
}
