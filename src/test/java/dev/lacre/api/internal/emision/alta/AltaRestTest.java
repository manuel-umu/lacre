package dev.lacre.api.internal.emision.alta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.ApiDePrueba;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.Envios;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

/**
 * El alta por HTTP, de extremo a extremo y contra Postgres: contrato, mapeo al modelo fiscal,
 * idempotencia y forma de los errores.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AltaRestTest {

    /** Válido, pero no dado de alta como obligado. */
    private static final String NIF_NO_CENSADO = "89890001K";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Obligados obligados;

    @Autowired
    private Envios envios;

    @Autowired
    private JdbcClient jdbc;

    private UUID obligadoId;
    private String nifDelObligado;

    @BeforeEach
    void darDeAltaUnObligado() {
        obligadoId = ObligadosDePrueba.nuevo(obligados);
        nifDelObligado = obligados.findById(obligadoId).orElseThrow().nif().valor();
    }

    /** Un 201 significa registro en la cadena y envío en el outbox. */
    @Test
    void elAltaDevuelveLaHuellaYDejaSuEnvioPendiente() throws Exception {
        MvcResult respuesta = mvc.perform(alta("clave-1", cuerpo("FA/1", "123.45")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(1))
                .andExpect(jsonPath("$.huella").value(org.hamcrest.Matchers.matchesPattern("[0-9A-F]{64}")))
                .andExpect(jsonPath("$.avisos").isEmpty())
                .andExpect(jsonPath("$.urlQr")
                        .value("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=" + nifDelObligado
                                + "&numserie=FA%2F1&fecha=15-01-2026&importe=123.45"))
                .andReturn();

        assertThat(envios.findByRegistroId(registroId(respuesta)))
                .hasValueSatisfying(envio -> assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE));
    }

    @Test
    void elReintentoConLaMismaClaveNoAnadeUnSegundoEslabon() throws Exception {
        String cuerpo = cuerpo("FA/1", "123.45");

        MvcResult primera = mvc.perform(alta("clave-repetida", cuerpo))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult segunda = mvc.perform(alta("clave-repetida", cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(1))
                .andExpect(jsonPath("$.urlQr").value(org.hamcrest.Matchers.containsString("numserie=FA%2F1")))
                .andReturn();

        assertThat(registroId(segunda)).isEqualTo(registroId(primera));
    }

    // --- Comprobación previa del art. 7.i de la OM HAC/1177/2024 ---

    /**
     * La cadena se rompe insertando un eslabón con huella anterior falsa: la tabla no admite
     * {@code UPDATE}.
     */
    @Test
    void unaCadenaRotaSeAvisaEnLaRespuestaYNoImpideRegistrar() throws Exception {
        mvc.perform(alta("clave-rota-1", cuerpo("FA/1", "123.45"))).andExpect(status().isCreated());
        insertarEslabonSuelto();

        mvc.perform(alta("clave-rota-2", cuerpo("FA/3", "123.45")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(3))
                .andExpect(jsonPath("$.avisos[0].codigo").value("HUELLA_ANTERIOR_NO_CUADRA"))
                .andExpect(jsonPath("$.avisos[0].mensaje")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }

    /** El eslabón enlaza por huella, pero su XML dice venir de otra factura. */
    @Test
    void unAnteriorQueDeclaraOtraFacturaSeAvisa() throws Exception {
        mvc.perform(alta("clave-otra-1", cuerpo("FA/1", "123.45"))).andExpect(status().isCreated());
        String huellaDelPrimero =
                jdbc.sql("""
                        select huella from registro_facturacion
                        where obligado_id = :obligado and posicion = 1
                        """).param("obligado", obligadoId).query(String.class).single();
        insertarEslabon("FA/OTRA", huellaDelPrimero);

        mvc.perform(alta("clave-otra-2", cuerpo("FA/3", "123.45")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0].codigo").value("IDENTIFICACION_ANTERIOR_NO_CUADRA"));
    }

    /** Sin anotarlos, el reintento diría que la cadena está sana. */
    @Test
    void elReintentoIdempotenteRepiteLosAvisos() throws Exception {
        mvc.perform(alta("clave-rota-3", cuerpo("FA/1", "123.45"))).andExpect(status().isCreated());
        insertarEslabonSuelto();
        String cuerpo = cuerpo("FA/3", "123.45");

        mvc.perform(alta("clave-rota-4", cuerpo)).andExpect(status().isCreated());

        mvc.perform(alta("clave-rota-4", cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos[0].codigo").value("HUELLA_ANTERIOR_NO_CUADRA"));
    }

    // --- Idempotencia y errores ---

    @Test
    void laMismaClaveConOtraFacturaEsConflicto() throws Exception {
        mvc.perform(alta("clave-2", cuerpo("FA/1", "123.45"))).andExpect(status().isCreated());

        mvc.perform(alta("clave-2", cuerpo("FA/2", "123.45")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("clave-idempotencia-reutilizada"));
    }

    @Test
    void sinClaveDeIdempotenciaNoSeEmite() throws Exception {
        mvc.perform(post("/v1/registros/alta")
                        .with(ApiDePrueba.autenticada())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("FA/9", "123.45")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaClaveDeIdempotenciaMasLargaQueLaColumnaEsPeticionInvalida() throws Exception {
        mvc.perform(alta("k".repeat(129), cuerpo("FA/9", "123.45")))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail").value("La Idempotency-Key admite como máximo 128 caracteres y tiene 129"));

        mvc.perform(alta("k".repeat(128), cuerpo("FA/9", "123.45"))).andExpect(status().isCreated());
    }

    @Test
    void unCaracterDeControlNoLlegaAlXmlDelRegistro() throws Exception {
        String conControl = cuerpo("FA/8", "123.45").replace("Servicios de consultoría", "Servicios\\u0001raros");

        mvc.perform(alta("clave-control", conControl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("U+0001")));

        assertThat(jdbc.sql("""
                        select count(*) from registro_facturacion where obligado_id = :obligado
                        """).param("obligado", obligadoId).query(Long.class).single())
                .isZero();
    }

    /** El error nombra el campo que falta. */
    @Test
    void unCampoObligatorioQueFaltaSeDicePorSuNombre() throws Exception {
        String sinDescripcion =
                cuerpo("FA/3", "123.45").replace("\"descripcionOperacion\": \"Servicios de consultoría\",", "");

        mvc.perform(alta("clave-3", sinDescripcion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.errores[*].campo").value(org.hamcrest.Matchers.hasItem("descripcionOperacion")));
    }

    /** El obligado sale del emisor de la factura, así que un emisor no censado se ve aquí. */
    @Test
    void unEmisorQueNoEstaDadoDeAltaLoDiceClaramente() throws Exception {
        String deOtroEmisor = cuerpo("FA/4", "123.45").replace(nifDelObligado, NIF_NO_CENSADO);

        mvc.perform(alta("clave-4", deOtroEmisor))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("obligado-desconocido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(NIF_NO_CENSADO)));
    }

    /** Un alta que la AEAT rechazaría no entra en la cadena, y el error trae su código. */
    @Test
    void unaCuotaQueLaAeatRechazariaSeDevuelveConSuCodigoYNoSeRegistra() throws Exception {
        String alVeintiuno = cuerpo("FA/6", "123.45").replace("\"tipoImpositivo\": 10", "\"tipoImpositivo\": 21");

        mvc.perform(alta("clave-6", alVeintiuno))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.codigoAeat").value("1142"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("12.35")));

        assertThat(jdbc.sql("""
                        select count(*) from registro_facturacion where obligado_id = :obligado
                        """).param("obligado", obligadoId).query(Long.class).single())
                .isZero();
    }

    /** Fecha de expedición futura, código 1112 del catálogo de errores de la AEAT. */
    @Test
    void unaFechaDeExpedicionFuturaSeDevuelveConSuCodigoYNoSeRegistra() throws Exception {
        String futura = cuerpo("FA/8", "123.45").replace("2026-01-15", "2099-01-01");

        mvc.perform(alta("clave-8", futura))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoAeat").value("1112"));

        assertThat(jdbc.sql("""
                        select count(*) from registro_facturacion where obligado_id = :obligado
                        """).param("obligado", obligadoId).query(Long.class).single())
                .isZero();
    }

    /** Fecha de expedición anterior a la entrada en vigor de la Orden HAC/1177/2024. */
    @Test
    void unaFechaDeExpedicionAnteriorALaEntradaEnVigorSeDevuelveConSuCodigo() throws Exception {
        String anterior = cuerpo("FA/9", "123.45").replace("2026-01-15", "2024-01-01");

        mvc.perform(alta("clave-9", anterior))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoAeat").value("1152"));
    }

    @Test
    void unaSegundaAltaDeLaMismaFacturaSeRechazaComoDuplicada() throws Exception {
        mvc.perform(alta("dup-1", cuerpo("FA/10", "123.45"))).andExpect(status().isCreated());

        mvc.perform(alta("dup-2", cuerpo("FA/10", "123.45")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoAeat").value("3000"));

        assertThat(jdbc.sql("""
                        select count(*) from registro_facturacion where obligado_id = :obligado
                        """).param("obligado", obligadoId).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void unaSubsanacionDeUnaFacturaQueYaExisteSeAdmite() throws Exception {
        mvc.perform(alta("subs-1", cuerpo("FA/11", "123.45"))).andExpect(status().isCreated());

        mvc.perform(alta("subs-2", subsanacion(cuerpo("FA/11", "123.45"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(2));
    }

    /** Un alta rechazada no existe en la AEAT, así que puede enviarse otra vez. */
    @Test
    void siLaAeatRechazoElAltaSePuedeVolverAEnviar() throws Exception {
        UUID rechazada = registroId(mvc.perform(alta("rech-1", cuerpo("FA/12", "123.45")))
                .andExpect(status().isCreated())
                .andReturn());
        envios.save(envios.findByRegistroId(rechazada)
                .orElseThrow()
                .rechazado(OffsetDateTime.now(), 1142, "Cuota incorrecta"));

        mvc.perform(alta("rech-2", cuerpo("FA/12", "123.45")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posicion").value(2));
    }

    /** Sin validación de la AEAT de por medio, el error no trae código de la AEAT. */
    @Test
    void unErrorDeContratoNoTraeCodigoAeat() throws Exception {
        String sinDescripcion =
                cuerpo("FA/7", "123.45").replace("\"descripcionOperacion\": \"Servicios de consultoría\",", "");

        mvc.perform(alta("clave-7", sinDescripcion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigoAeat").doesNotExist());
    }

    /** Un código de catálogo inventado se responde con la lista de los admitidos. */
    @Test
    void unCodigoDeCatalogoDesconocidoDiceCualesValen() throws Exception {
        String conImpuestoInventado = cuerpo("FA/5", "123.45").replace("\"impuesto\": \"01\"", "\"impuesto\": \"99\"");

        mvc.perform(alta("clave-5", conImpuestoInventado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Admitidos")));
    }

    /** Segundo eslabón de la cadena, válido salvo por su huella anterior. */
    private void insertarEslabonSuelto() {
        insertarEslabon("FA/1", "0".repeat(64));
    }

    /** Segundo eslabón, cuyo XML declara como anterior la factura y la huella indicadas. */
    private void insertarEslabon(String numSerieDeclarado, String huellaAnterior) {
        String xml = EscritorRegistro.escribir(new RegistroEncadenado(
                Registros.emitible().idFactura(idFactura("MANIPULADA")).build(),
                Optional.of(new RegistroAnterior(idFactura(numSerieDeclarado), new Huella(huellaAnterior))),
                OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
                new Huella("F".repeat(64))));
        jdbc.sql("""
                insert into registro_facturacion
                    (id, obligado_id, posicion, tipo, emisor, num_serie_factura,
                     fecha_expedicion_factura, huella, huella_anterior,
                     fecha_hora_huso_gen_registro, huso_offset_segundos, xml)
                values (:id, :obligado, 2, 'ALTA', :emisor, 'MANIPULADA', date '2026-01-15',
                        :huella, :huellaAnterior, :fechaHora, 0, :xml)
                """)
                .param("id", UUID.randomUUID())
                .param("obligado", obligadoId)
                .param("emisor", nifDelObligado)
                .param("huella", "F".repeat(64))
                .param("huellaAnterior", huellaAnterior)
                .param("fechaHora", OffsetDateTime.now())
                .param("xml", xml)
                .update();
    }

    private IdFactura idFactura(String numSerie) {
        return new IdFactura(new Nif(nifDelObligado), numSerie, LocalDate.of(2026, 1, 15));
    }

    private org.springframework.test.web.servlet.RequestBuilder alta(String clave, String cuerpo) {
        return post("/v1/registros/alta")
                .with(ApiDePrueba.autenticada())
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static UUID registroId(MvcResult respuesta) throws Exception {
        return UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(respuesta.getResponse().getContentAsString(), "$.registroId"));
    }

    private static String subsanacion(String cuerpo) {
        return cuerpo.replace("\"tipoFactura\": \"F1\",", "\"tipoFactura\": \"F1\", \"subsanacion\": true,");
    }

    /** Los importes del ejemplo oficial de la huella, 111,10 + 12,35 = 123,45, al 10 %. */
    private String cuerpo(String numSerie, String importeTotal) {
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
                  "importeTotal": %s
                }
                """.formatted(nifDelObligado, numSerie, importeTotal);
    }
}
