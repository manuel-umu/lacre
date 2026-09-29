package dev.lacre.api.internal.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.internal.adaptador.DespachadorLotes;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Consulta del estado de un registro y verificación de la cadena de un obligado. Cada test usa
 * su propio obligado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultaRestTest {

    private static final String TRIGGER = "registro_facturacion_append_only";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Obligados obligados;

    @Autowired
    private PostgreSQLContainer postgres;

    /** Como propietario: estos tests se saltan a propósito las defensas del registro. */
    private JdbcClient jdbc;

    @Autowired
    private DespachadorLotes despachador;

    @Autowired
    private EncadenadorRegistros encadenador;

    private UUID obligadoId;
    private String nifDelObligado;

    @BeforeEach
    void darDeAltaUnObligado() {
        jdbc = TestcontainersConfiguration.comoPropietario(postgres);
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
                .andExpect(jsonPath("$.remision.descripcionError")
                        .value(org.hamcrest.Matchers.startsWith("Certificado no disponible (" + nifDelObligado + ")")));
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

    /** Recalcula la huella de cada registro desde el XML guardado, y lo declara en el alcance. */
    @Test
    void unaCadenaBienFormadaSeVerificaYDiceHastaDondeLlega() throws Exception {
        emitir("FA/1", "cadena-1");
        emitir("FA/2", "cadena-2");
        emitir("FA/3", "cadena-3");

        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros").value(3))
                .andExpect(jsonPath("$.intacta").value(true))
                .andExpect(jsonPath("$.alcance").value("HUELLAS"))
                .andExpect(jsonPath("$.roturas").isEmpty());
    }

    /**
     * La cadena se rompe insertando un eslabón con huella anterior falsa: la tabla no admite
     * {@code UPDATE}. Su XML es válido y coherente con su huella, así que solo falla el enlace.
     */
    @Test
    void unEslabonQueNoEnlazaSeDenuncia() throws Exception {
        emitir("FA/1", "rota-1");
        RegistroEncadenado suelto = registroQueApuntaA(new Huella("0".repeat(64)));
        insertarEslabon(2, suelto, EscritorRegistro.escribir(suelto));

        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros").value(2))
                .andExpect(jsonPath("$.intacta").value(false))
                .andExpect(jsonPath("$.roturas.length()").value(1))
                .andExpect(jsonPath("$.roturas[0].posicion").value(2))
                .andExpect(jsonPath("$.roturas[0].motivo").value("HUELLA_ANTERIOR_NO_CUADRA"));
    }

    /**
     * Lo que la verificación de enlaces no veía: un registro cuyo contenido se ha cambiado
     * después de guardarlo, sin tocar sus huellas. Los enlaces siguen cuadrando.
     */
    @Test
    void unRegistroManipuladoSeDenunciaAunqueSusEnlacesCuadren() throws Exception {
        emitir("FA/1", "manipulada-1");
        emitir("FA/2", "manipulada-2");

        int cambiadas = manipularSaltandoseElTrigger("""
                update registro_facturacion
                set xml = replace(xml, 'CuotaTotal>12.35<', 'CuotaTotal>99.99<')
                where obligado_id = :obligado and posicion = 2
                """);

        assertThat(cambiadas).isEqualTo(1);
        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intacta").value(false))
                .andExpect(jsonPath("$.roturas.length()").value(1))
                .andExpect(jsonPath("$.roturas[0].posicion").value(2))
                .andExpect(jsonPath("$.roturas[0].motivo").value("HUELLA_NO_CUADRA"));
    }

    /** Una fila cuyo XML no se puede leer se denuncia; no tumba la verificación. */
    @Test
    void unRegistroConElXmlIlegibleSeDenuncia() throws Exception {
        emitir("FA/1", "ilegible-1");
        Huella delPrimero = new Huella(
                jdbc.sql("""
                select huella from registro_facturacion
                where obligado_id = :obligado and posicion = 1
                """).param("obligado", obligadoId).query(String.class).single());
        insertarEslabon(2, registroQueApuntaA(delPrimero), "<x/>");

        mvc.perform(get("/v1/obligados/{nif}/cadena", nifDelObligado).with(ApiDePrueba.autenticada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intacta").value(false))
                .andExpect(jsonPath("$.roturas.length()").value(1))
                .andExpect(jsonPath("$.roturas[0].posicion").value(2))
                .andExpect(jsonPath("$.roturas[0].motivo").value("XML_ILEGIBLE"));
    }

    @Test
    void laCadenaDeUnObligadoQueNoExisteEs422() throws Exception {
        mvc.perform(get("/v1/obligados/{nif}/cadena", "89890001K").with(ApiDePrueba.autenticada()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("obligado-desconocido"));
    }

    /** Un alta del obligado que declara como anterior la huella indicada. */
    private RegistroEncadenado registroQueApuntaA(Huella anterior) {
        IdFactura factura = new IdFactura(new Nif(nifDelObligado), "SUELTA", LocalDate.of(2026, 1, 15));
        return encadenador.encadenar(
                Registros.alta().idFactura(factura).build(),
                Optional.of(new RegistroAnterior(Registros.idFactura("FA/0"), anterior)),
                ZoneId.of("Europe/Madrid"));
    }

    private void insertarEslabon(long posicion, RegistroEncadenado registro, String xml) {
        jdbc.sql("""
                insert into registro_facturacion
                    (id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                     fecha_expedicion_factura, huella, huella_anterior,
                     fecha_hora_huso_gen_registro, huso_offset_segundos, xml)
                values (:id, :obligado, :posicion, 'ALTA', :emisor, :numSerie, :fecha,
                        :huella, :huellaAnterior, :fechaHora, :huso, :xml)
                """)
                .param("id", UUID.randomUUID())
                .param("obligado", obligadoId)
                .param("posicion", posicion)
                .param("emisor", nifDelObligado)
                .param("numSerie", registro.datos().idFactura().numSerieFactura())
                .param("fecha", registro.datos().idFactura().fechaExpedicion())
                .param("huella", registro.huella().valor())
                .param(
                        "huellaAnterior",
                        registro.registroAnterior().orElseThrow().huella().valor())
                .param("fechaHora", registro.fechaHoraHusoGenRegistro())
                .param("huso", registro.fechaHoraHusoGenRegistro().getOffset().getTotalSeconds())
                .param("xml", xml)
                .update();
    }

    /**
     * El trigger impide cualquier modificación; solo quien tiene permisos sobre la tabla puede
     * desactivarlo, que es lo que se simula.
     */
    private int manipularSaltandoseElTrigger(String sentencia) {
        jdbc.sql("alter table registro_facturacion disable trigger " + TRIGGER).update();
        try {
            return jdbc.sql(sentencia).param("obligado", obligadoId).update();
        } finally {
            jdbc.sql("alter table registro_facturacion enable trigger " + TRIGGER)
                    .update();
        }
    }

    private UUID emitir(String numSerie, String clave) throws Exception {
        MvcResult respuesta = mvc.perform(post("/v1/registros/alta")
                        .with(ApiDePrueba.autenticada())
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(numSerie)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(respuesta.getResponse().getContentAsString(), "$.registroId"));
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
