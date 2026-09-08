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

/**
 * La máquina de estados del outbox, que se resume en una frase: de {@code PENDIENTE} se sale una
 * vez y no se vuelve.
 * <p>
 * Importa porque los dos desenlaces con error tampoco se reintentan. Un registro rechazado o
 * aceptado con errores se arregla <strong>subsanando</strong>, que es generar un registro nuevo;
 * reenviar este duplicaría la presentación ante la AEAT.
 */
class EnvioRegistroTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID REGISTRO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final OffsetDateTime CREADO =
            OffsetDateTime.of(2024, 1, 1, 19, 20, 30, 0, ZoneOffset.ofHours(1));
    private static final OffsetDateTime RESPUESTA = CREADO.plusMinutes(2);

    private static EnvioRegistro pendiente() {
        return EnvioRegistro.pendiente(ID, REGISTRO, CREADO);
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

    /**
     * Cada uno de los cuatro desenlaces, aplicado sobre cada uno de los cuatro estados
     * terminales: dieciséis combinaciones, todas prohibidas.
     */
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
                ID, REGISTRO, EstadoEnvio.ACEPTADO, CREADO, null, null, null, 0))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void unEnvioPendienteConFechaDeRespuestaTampoco() {
        assertThatThrownBy(() -> new EnvioRegistro(
                ID, REGISTRO, EstadoEnvio.PENDIENTE, CREADO, RESPUESTA, null, null, 0))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEnvio.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    void todoDesenlaceEsTerminal(EstadoEnvio estado) {
        assertThat(estado.esTerminal()).isTrue();
    }
}
