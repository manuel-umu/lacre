package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Máquina de estados del outbox: de {@code PENDIENTE} se sale una vez y no se vuelve. */
class EnvioRegistroTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID REGISTRO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OBLIGADO = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final OffsetDateTime CREADO =
            OffsetDateTime.of(2024, 1, 1, 19, 20, 30, 0, ZoneOffset.ofHours(1));
    private static final OffsetDateTime RESPUESTA = CREADO.plusMinutes(2);

    private static EnvioRegistro pendiente() {
        return EnvioRegistro.pendiente(ID, REGISTRO, OBLIGADO, CREADO);
    }

    @Test
    void naceePendienteYSinRespuesta() {
        EnvioRegistro envio = pendiente();

        assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE);
        assertThat(envio.estado().esTerminal()).isFalse();
        assertThat(envio.enviadoEn()).isNull();
        assertThat(envio.codigoError()).isNull();
    }

    // --- Las cuatro salidas legales ---

    @Test
    void aceptadoNoGuardaError() {
        EnvioRegistro envio = pendiente().aceptado(RESPUESTA);

        assertThat(envio.estado()).isEqualTo(EstadoEnvio.ACEPTADO);
        assertThat(envio.enviadoEn()).isEqualTo(RESPUESTA);
        assertThat(envio.codigoError()).isNull();
    }

    @Test
    void aceptadoConErroresGuardaQueHaySubsanar() {
        EnvioRegistro envio = pendiente().aceptadoConErrores(RESPUESTA, 2000, "Huella incorrecta");

        assertThat(envio.estado()).isEqualTo(EstadoEnvio.ACEPTADO_CON_ERRORES);
        assertThat(envio.codigoError()).isEqualTo(2000);
        assertThat(envio.descripcionError()).isEqualTo("Huella incorrecta");
    }

    @Test
    void rechazadoGuardaElMotivo() {
        EnvioRegistro envio = pendiente().rechazado(RESPUESTA, 1130, "Caracteres no permitidos");

        assertThat(envio.estado()).isEqualTo(EstadoEnvio.RECHAZADO);
        assertThat(envio.codigoError()).isEqualTo(1130);
    }

    /**
     * {@code TextMax1500Type} en {@code RespuestaSuministro.xsd}. El número va escrito, y no la
     * constante, para que el test no se mida a sí mismo.
     */
    @Test
    void unaDescripcionDeLaAeatCabeEnteraHastaSuMaximo() {
        String larga = "x".repeat(1500);

        assertThat(pendiente().rechazado(RESPUESTA, 1100, larga).descripcionError())
                .isEqualTo(larga);
        assertThatThrownBy(() -> pendiente().rechazado(RESPUESTA, 1100, larga + "x"))
                .isInstanceOf(ValorInvalidoException.class);
    }

    /** El motivo de un intento fallido sale de una excepción, sin límite: se recorta, no lanza. */
    @Test
    void elMotivoDeUnIntentoFallidoSeRecortaAlMaximo() {
        String enorme = "x".repeat(EnvioRegistro.MAXIMO_LONGITUD_DESCRIPCION_ERROR + 500);

        EnvioRegistro envio = pendiente().otroIntentoFallido(null, enorme);

        assertThat(envio.intentos()).isEqualTo(1);
        assertThat(envio.descripcionError())
                .hasSize(EnvioRegistro.MAXIMO_LONGITUD_DESCRIPCION_ERROR);
    }

    /** El 3000 no es un fallo: significa que el registro ya estaba presentado. */
    @Test
    void duplicadoLlevaSiempreElCodigoDeLaAeat() {
        EnvioRegistro envio = pendiente().duplicado(RESPUESTA, "Registro duplicado");

        assertThat(envio.estado()).isEqualTo(EstadoEnvio.DUPLICADO);
        assertThat(envio.codigoError()).isEqualTo(3000);
    }

    // --- Lo que no se puede hacer ---

    static Stream<UnaryOperator<EnvioRegistro>> desenlaces() {
        return Stream.of(
                envio -> envio.aceptado(RESPUESTA),
                envio -> envio.aceptadoConErrores(RESPUESTA, 2000, "x"),
                envio -> envio.rechazado(RESPUESTA, 1130, "x"),
                envio -> envio.duplicado(RESPUESTA, "x"));
    }

    /** Cada desenlace sobre cada estado terminal: dieciséis combinaciones prohibidas. */
    @ParameterizedTest
    @MethodSource("desenlaces")
    void ningunDesenlaceSaleDeOtroDesenlace(UnaryOperator<EnvioRegistro> desenlace) {
        desenlaces().forEach(previo -> {
            EnvioRegistro resuelto = previo.apply(pendiente());

            assertThatThrownBy(() -> desenlace.apply(resuelto))
                    .isInstanceOf(EnvioYaResueltoException.class)
                    .hasMessageContaining(resuelto.estado().name());
        });
    }

    @Test
    void laAeatNoPuedeResponderAntesDeQueElEnvioExista() {
        assertThatThrownBy(() -> pendiente().aceptado(CREADO.minusSeconds(1)))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void unEnvioTerminadoSinFechaDeRespuestaNoSePuedeConstruir() {
        assertThatThrownBy(() -> new EnvioRegistro(
                ID, REGISTRO, OBLIGADO, EstadoEnvio.ACEPTADO, CREADO, null, null, null, 0, 0))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void unEnvioPendienteConFechaDeRespuestaTampoco() {
        assertThatThrownBy(() -> new EnvioRegistro(
                ID, REGISTRO, OBLIGADO, EstadoEnvio.PENDIENTE, CREADO, RESPUESTA, null, null, 0, 0))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEnvio.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    void todoDesenlaceEsTerminal(EstadoEnvio estado) {
        assertThat(estado.esTerminal()).isTrue();
    }
}
