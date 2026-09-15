package dev.lacre.api.internal.emision.anulacion;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.Envios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La anulación por HTTP: anular añade un eslabón a la cadena, y el tipo de petición entra en la
 * huella de idempotencia.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AnulacionRestTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Obligados obligados;

    @Autowired
    private Envios envios;

    private String nifDelObligado;

    @BeforeEach
    void darDeAltaUnObligado() {
        UUID id = ObligadosDePrueba.nuevo(obligados);
        nifDelObligado = obligados.findById(id).orElseThrow().nif().valor();
    }

    @Test
    void anularAnadeUnEslabonEnVezDeQuitarlo() throws Exception {
        mvc.perform(peticion("/alta", "alta-1", alta("FA/1"))).andExpect(status().isCreated());

        MvcResult respuesta = mvc.perform(peticion("/anulacion", "anul-1", anulacion("FA/1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(2))
                .andExpect(jsonPath("$.urlQr").doesNotExist())
                .andReturn();

        assertThat(envios.findByRegistroId(registroId(respuesta)))
                .hasValueSatisfying(envio -> assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE));
    }

    @Test
    void elReintentoConLaMismaClaveNoAnulaDosVeces() throws Exception {
        mvc.perform(peticion("/alta", "alta-2", alta("FA/2"))).andExpect(status().isCreated());

        MvcResult primera = mvc.perform(peticion("/anulacion", "anul-2", anulacion("FA/2")))
                .andExpect(status().isCreated()).andReturn();
        MvcResult segunda = mvc.perform(peticion("/anulacion", "anul-2", anulacion("FA/2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(2))
                .andReturn();

        assertThat(registroId(segunda)).isEqualTo(registroId(primera));
    }

    @Test
    void laClaveDelAltaNoSirveParaLaAnulacionDeLaMismaFactura() throws Exception {
        mvc.perform(peticion("/alta", "compartida", alta("FA/3"))).andExpect(status().isCreated());

        mvc.perform(peticion("/anulacion", "compartida", anulacion("FA/3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("clave-idempotencia-reutilizada"));
    }

    @Test
    void sinLaFacturaAnuladaNoHayNadaQueAnular() throws Exception {
        mvc.perform(peticion("/anulacion", "anul-4", """
                { "generadoPor": "E" }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo")
                        .value(org.hamcrest.Matchers.hasItem("idFacturaAnulada")));
    }

    private RequestBuilder peticion(String ruta, String clave, String cuerpo) {
        return post("/v1/registros" + ruta)
                .with(ApiDePrueba.autenticada())
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static UUID registroId(MvcResult respuesta) throws Exception {
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                respuesta.getResponse().getContentAsString(), "$.registroId"));
    }

    private String anulacion(String numSerie) {
        return """
                {
                  "idFacturaAnulada": {
                    "idEmisorFactura": "%s",
                    "numSerieFactura": "%s",
                    "fechaExpedicionFactura": "2026-01-15"
                  },
                  "generadoPor": "E"
                }
                """.formatted(nifDelObligado, numSerie);
    }

    private String alta(String numSerie) {
        return """
                {
                  "idFactura": {
                    "idEmisorFactura": "%s",
                    "numSerieFactura": "%s",
                    "fechaExpedicionFactura": "2026-01-15"
                  },
                  "nombreRazonEmisor": "Obligado de prueba SL",
                  "tipoFactura": "F1",
                  "descripcionOperacion": "Servicios de consultoría",
                  "destinatarios": [ { "nombreRazon": "Cliente SL", "nif": "A28015865" } ],
                  "desglose": [
                    {
                      "impuesto": "01",
                      "claveRegimen": "01",
                      "calificacion": "S1",
                      "tipoImpositivo": 10,
                      "baseImponibleOimporteNoSujeto": 111.10,
                      "cuotaRepercutida": 12.35
                    }
                  ],
                  "cuotaTotal": 12.35,
                  "importeTotal": 123.45
                }
                """.formatted(nifDelObligado, numSerie);
    }
}
