package dev.lacre.api.internal.obligados;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import dev.lacre.identidad.ObligadosDePrueba;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * El alta de obligados por HTTP, contra Postgres: que un obligado recién dado de alta ya puede
 * facturar, que el alta es idempotente por NIF y que cambiar la zona no reescribe el pasado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ObligadosRestTest {

    private static final int HILOS = 16;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AltasDeObligados altas;

    @Autowired
    private JdbcClient jdbc;

    /** El motivo de que exista esta operación: sin ella, facturar exigía un INSERT a mano. */
    @Test
    void unObligadoRecienDadoDeAltaYaPuedeFacturar() throws Exception {
        String nif = ObligadosDePrueba.siguienteNif().valor();

        mvc.perform(alta(nif, "Europe/Madrid"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nif").value(nif))
                .andExpect(jsonPath("$.nombreRazon").value("Obligado de prueba SL"))
                .andExpect(jsonPath("$.zonaHoraria").value("Europe/Madrid"));

        mvc.perform(factura(nif, "FA/1", "alta-de-obligado-1")).andExpect(status().isCreated());
    }

    @Test
    void repetirElAltaNoDuplicaAlObligado() throws Exception {
        String nif = ObligadosDePrueba.siguienteNif().valor();

        mvc.perform(alta(nif, "Europe/Madrid")).andExpect(status().isCreated());
        mvc.perform(alta(nif, "Europe/Madrid")).andExpect(status().isOk());

        assertThat(obligadosConNif(nif)).isEqualTo(1);
    }

    /**
     * La zona nueva vale para los registros que vengan después, y los anteriores conservan el
     * huso con el que se calculó su huella. Madrid y Canarias cambian de hora a la vez, así que
     * siempre están a una hora.
     */
    @Test
    void cambiarLaZonaNoReescribeElHusoDeLosRegistrosAnteriores() throws Exception {
        String nif = ObligadosDePrueba.siguienteNif().valor();
        mvc.perform(alta(nif, "Europe/Madrid")).andExpect(status().isCreated());
        UUID anterior =
                registroId(mvc.perform(factura(nif, "FA/1", "cambio-de-zona-1")).andReturn());
        OffsetDateTime husoAnterior = husoDe(anterior);

        mvc.perform(alta(nif, "Atlantic/Canary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("Atlantic/Canary"));
        UUID posterior =
                registroId(mvc.perform(factura(nif, "FA/2", "cambio-de-zona-2")).andReturn());

        assertThat(husoDe(anterior)).isEqualTo(husoAnterior);
        assertThat(husoAnterior.getOffset().getTotalSeconds()
                        - husoDe(posterior).getOffset().getTotalSeconds())
                .isEqualTo(3600);
    }

    @Test
    void unNifInvalidoSeRechaza() throws Exception {
        mvc.perform(alta("12345678A", "Europe/Madrid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"));
    }

    /** Un desfase fijo no tiene horario de verano: el huso sería falso media parte del año. */
    @ParameterizedTest
    @ValueSource(strings = {"Europe/Villaconejos", "+01:00", "UTC", "GMT+1", "Etc/GMT-1"})
    void unaZonaDesconocidaOSinHorarioDeVeranoSeRechaza(String zona) throws Exception {
        mvc.perform(alta(ObligadosDePrueba.siguienteNif().valor(), zona))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"));
    }

    @Test
    void unCampoQueFaltaSeDicePorSuNombre() throws Exception {
        mvc.perform(put("/v1/obligados/{nif}", ObligadosDePrueba.siguienteNif().valor())
                        .with(ApiDePrueba.autenticada())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "zonaHoraria": "Europe/Madrid" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo").value(org.hamcrest.Matchers.hasItem("nombreRazon")));
    }

    /**
     * Sin el cerrojo, dos altas del mismo NIF nuevo pasarían las dos la comprobación y la segunda
     * se estrellaría contra la restricción de unicidad.
     */
    @Test
    void dieciseisAltasSimultaneasDelMismoNifDejanUnSoloObligado() throws Exception {
        String nif = ObligadosDePrueba.siguienteNif().valor();
        PeticionObligado peticion = new PeticionObligado("Obligado de prueba SL", "Europe/Madrid");

        CyclicBarrier salida = new CyclicBarrier(HILOS);
        List<Callable<AltasDeObligados.Alta>> tareas = IntStream.range(0, HILOS)
                .<Callable<AltasDeObligados.Alta>>mapToObj(i -> () -> {
                    salida.await();
                    return altas.darDeAlta(nif, peticion);
                })
                .toList();

        List<Future<AltasDeObligados.Alta>> resultados;
        try (ExecutorService hilos = Executors.newFixedThreadPool(HILOS)) {
            resultados = hilos.invokeAll(tareas);
        }
        long creados = 0;
        for (Future<AltasDeObligados.Alta> resultado : resultados) {
            if (resultado.get().creado()) {
                creados++;
            }
        }

        assertThat(creados).isEqualTo(1);
        assertThat(obligadosConNif(nif)).isEqualTo(1);
    }

    private RequestBuilder alta(String nif, String zona) {
        return put("/v1/obligados/{nif}", nif)
                .with(ApiDePrueba.autenticada())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "nombreRazon": "Obligado de prueba SL", "zonaHoraria": "%s" }
                        """.formatted(zona));
    }

    private int obligadosConNif(String nif) {
        return jdbc.sql("select count(*) from obligado where nif = :nif")
                .param("nif", nif)
                .query(Integer.class)
                .single();
    }

    private OffsetDateTime husoDe(UUID registro) throws Exception {
        MvcResult respuesta = mvc.perform(get("/v1/registros/{id}", registro).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andReturn();
        return OffsetDateTime.parse(com.jayway.jsonpath.JsonPath.read(
                respuesta.getResponse().getContentAsString(), "$.fechaHoraHusoGenRegistro"));
    }

    private static UUID registroId(MvcResult respuesta) throws Exception {
        assertThat(respuesta.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(respuesta.getResponse().getContentAsString(), "$.registroId"));
    }

    private static RequestBuilder factura(String nif, String numSerie, String clave) {
        return post("/v1/registros/alta")
                .with(ApiDePrueba.autenticada())
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
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
                        """.formatted(nif, numSerie));
    }
}
