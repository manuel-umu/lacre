package dev.lacre.api.internal;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.api.internal.consulta.EstadoDelRegistro;
import dev.lacre.api.internal.consulta.RespuestaVerificacion;
import dev.lacre.api.internal.emision.IdFacturaDto;
import dev.lacre.api.internal.emision.PersonaDto;
import dev.lacre.api.internal.emision.RespuestaRegistro;
import dev.lacre.api.internal.emision.alta.PeticionAlta;
import dev.lacre.api.internal.emision.anulacion.PeticionAnulacion;
import dev.lacre.api.internal.obligados.PeticionObligado;
import dev.lacre.api.internal.obligados.RespuestaObligado;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contrato publicado en {@code openapi.yaml} contra el código que lo sirve. Una
 * documentación de integración que se desincroniza es peor que no tenerla: dice cómo era la API
 * el día que se escribió.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ContratoOpenApiTest {

    private static final Set<String> VERBOS = Set.of("get", "post", "put", "patch", "delete");

    private static Map<String, Object> contrato;

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeos;

    @BeforeAll
    static void leerElContrato() {
        try (InputStream fichero = ContratoOpenApiTest.class
                .getResourceAsStream("/static/openapi.yaml")) {
            contrato = new Yaml().load(fichero);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer /static/openapi.yaml", e);
        }
    }

    /** Ni un endpoint documentado que no exista, ni uno expuesto que nadie documentó. */
    @Test
    void lasOperacionesDocumentadasSonExactamenteLasQueLaApiExpone() {
        assertThat(operacionesDocumentadas())
                .containsExactlyInAnyOrderElementsOf(operacionesMapeadas());
    }

    /**
     * Los esquemas y los records tienen los mismos campos, en los dos sentidos: un campo nuevo
     * sin documentar pone rojo este test, y uno documentado que ya no existe también.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("esquemasYSusRecords")
    void cadaEsquemaTieneLosCamposDeSuRecord(String esquema, Class<?> record) {
        assertThat(propiedadesDe(esquema))
                .containsExactlyInAnyOrderElementsOf(componentesDe(record));
    }

    /** Como la sonda de salud: el contrato se lee antes de tener credencial. */
    @Test
    void elContratoSeSirveSinCredencial() throws Exception {
        mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
    }

    @Test
    void elContratoDeclaraLaClaveDeApiComoSeguridadPorDefecto() {
        assertThat(contrato).extractingByKey("security")
                .isEqualTo(List.of(Map.of("claveDeApi", List.of())));
    }

    static Stream<Arguments> esquemasYSusRecords() {
        return Stream.of(
                Arguments.of("IdFacturaDto", IdFacturaDto.class),
                Arguments.of("PersonaDto", PersonaDto.class),
                Arguments.of("IdOtroDto", PersonaDto.IdOtroDto.class),
                Arguments.of("PeticionAlta", PeticionAlta.class),
                Arguments.of("Detalle", PeticionAlta.Detalle.class),
                Arguments.of("Rectificacion", PeticionAlta.Rectificacion.class),
                Arguments.of("PeticionAnulacion", PeticionAnulacion.class),
                Arguments.of("RespuestaRegistro", RespuestaRegistro.class),
                Arguments.of("Aviso", RespuestaRegistro.Aviso.class),
                Arguments.of("EstadoDelRegistro", EstadoDelRegistro.class),
                Arguments.of("Remision", EstadoDelRegistro.Remision.class),
                Arguments.of("RespuestaVerificacion", RespuestaVerificacion.class),
                Arguments.of("Rotura", RespuestaVerificacion.Rotura.class),
                Arguments.of("PeticionObligado", PeticionObligado.class),
                Arguments.of("RespuestaObligado", RespuestaObligado.class));
    }

    @SuppressWarnings("unchecked")
    private Set<String> operacionesDocumentadas() {
        Map<String, Map<String, Object>> rutas =
                (Map<String, Map<String, Object>>) contrato.get("paths");

        return rutas.entrySet().stream()
                .flatMap(ruta -> ruta.getValue().keySet().stream()
                        .filter(VERBOS::contains)
                        .map(verbo -> verbo.toUpperCase() + " " + ruta.getKey()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Solo las de la API versionada: {@code /actuator} y {@code /error} no son el contrato. */
    private Set<String> operacionesMapeadas() {
        return mapeos.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream()
                        .flatMap(patron -> info.getMethodsCondition().getMethods().stream()
                                .map(metodo -> metodo.name() + " " + patron)))
                .filter(operacion -> operacion.contains(" /v1/"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private Set<String> propiedadesDe(String esquema) {
        Map<String, Object> esquemas = (Map<String, Object>)
                ((Map<String, Object>) contrato.get("components")).get("schemas");
        Map<String, Object> definicion = (Map<String, Object>) esquemas.get(esquema);

        assertThat(definicion).as("el esquema %s no está en el contrato", esquema).isNotNull();
        return ((Map<String, Object>) definicion.get("properties")).keySet();
    }

    private static Set<String> componentesDe(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
