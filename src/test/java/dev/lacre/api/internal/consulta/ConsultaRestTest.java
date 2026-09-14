package dev.lacre.api.internal.consulta;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.internal.adaptador.DespachadorLotes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta del estado de un registro y verificación de la cadena de un obligado. Cada test usa
 * su propio obligado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultaRestTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Obligados obligados;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DespachadorLotes despachador;

    private UUID obligadoId;
    private String nifDelObligado;

    @BeforeEach
    void darDeAltaUnObligado() {
        obligadoId = ObligadosDePrueba.nuevo(obligados);
        nifDelObligado = obligados.findById(obligadoId).orElseThrow().nif().valor();
    }

    /**
     * Sin directorio de certificados, el despachador real no llega a salir a la red. El motivo se
     * lee en la consulta, que es donde lo busca quien integra, y no en el log del despliegue.
     */
    @Test
    void unEnvioQueNoSalePorFaltaDeCertificadoDiceElMotivo() throws Exception {
        UUID registro = emitir("FA/1", "sin-certificado-1");

        despachador.despachar();

        mvc.perform(get("/v1/registros/{id}", registro).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remision.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.remision.intentos").value(1))
                .andExpect(jsonPath("$.remision.descripcionError").value(org.hamcrest.Matchers
                        .startsWith("Certificado no disponible (" + nifDelObligado + ")")));
    }

    @Test
    void elEstadoDeUnRegistroReciénEmitidoDiceQueSiguePendiente() throws Exception {
        UUID registro = emitir("FA/1", "consulta-1");

        mvc.perform(get("/v1/registros/{id}", registro).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posicion").value(1))
                .andExpect(jsonPath("$.tipo").value("ALTA"))
                .andExpect(jsonPath("$.numSerieFactura").value("FA/1"))
                .andExpect(jsonPath("$.huella").value(matchesPattern("[0-9A-F]{64}")))
                .andExpect(jsonPath("$.huellaAnterior").doesNotExist())
                .andExpect(jsonPath("$.remision.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.remision.intentos").value(0));
    }

    @Test
    void unRegistroQueNoExisteEs404() throws Exception {
        mvc.perform(get("/v1/registros/{id}", UUID.randomUUID()).with(ApiDePrueba.autenticada()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("registro-desconocido"));
    }

    @Test
    void laCadenaDeUnObligadoSinFacturasEstaIntacta() throws Exception {
        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros").value(0))
                .andExpect(jsonPath("$.intacta").value(true));
    }

    /** La respuesta declara el {@code alcance} de la verificación. */
    @Test
    void unaCadenaBienFormadaSeVerificaYDiceHastaDondeLlega() throws Exception {
        emitir("FA/1", "cadena-1");
        emitir("FA/2", "cadena-2");
        emitir("FA/3", "cadena-3");

        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros").value(3))
                .andExpect(jsonPath("$.intacta").value(true))
                .andExpect(jsonPath("$.alcance").value("ENLACES"))
                .andExpect(jsonPath("$.roturas").isEmpty());
    }

    /**
     * La cadena se rompe insertando un eslabón con huella anterior falsa: la tabla no admite
     * {@code UPDATE}.
     */
    @Test
    void unEslabonQueNoEnlazaSeDenuncia() throws Exception {
        emitir("FA/1", "rota-1");
        insertarEslabonSuelto(2, "0".repeat(64));

        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros").value(2))
                .andExpect(jsonPath("$.intacta").value(false))
                .andExpect(jsonPath("$.roturas[0].posicion").value(2))
                .andExpect(jsonPath("$.roturas[0].motivo").value("HUELLA_ANTERIOR_NO_CUADRA"));
    }

    @Test
    void laCadenaDeUnObligadoQueNoExisteEs422() throws Exception {
        mvc.perform(get("/v1/obligados/{nif}/cadena", "89890001K").with(ApiDePrueba.autenticada()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("obligado-desconocido"));
    }

    private void insertarEslabonSuelto(long posicion, String huellaAnteriorFalsa) {
        jdbc.sql("""
                insert into registro_facturacion
                    (id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                     fecha_expedicion_factura, huella, huella_anterior,
                     fecha_hora_huso_gen_registro, huso_offset_segundos, xml)
                values (:id, :obligado, :posicion, 'ALTA', :emisor, 'MANIPULADA',
                        date '2026-01-15', :huella, :huellaAnterior, :fechaHora, 0, '<x/>')
                """)
                .param("id", UUID.randomUUID())
                .param("obligado", obligadoId)
                .param("posicion", posicion)
                .param("emisor", nifDelObligado)
                .param("huella", "F".repeat(64))
                .param("huellaAnterior", huellaAnteriorFalsa)
                .param("fechaHora", OffsetDateTime.now())
                .update();
    }

    private UUID emitir(String numSerie, String clave) throws Exception {
        MvcResult respuesta = mvc.perform(post("/v1/registros/alta")
                        .with(ApiDePrueba.autenticada())
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(numSerie)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                respuesta.getResponse().getContentAsString(), "$.registroId"));
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
                      "tipoImpositivo": 21,
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
