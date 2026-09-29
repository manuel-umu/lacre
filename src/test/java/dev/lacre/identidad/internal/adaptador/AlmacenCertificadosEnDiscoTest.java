package dev.lacre.identidad.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.CertificadoNoDisponibleException;
import dev.lacre.shared.Nif;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * El almacén contra ficheros PKCS#12 reales, sin Spring ni base de datos. Los certificados de
 * {@code src/test/resources/certificados} son autofirmados; el del NIF {@code 00000001R} nació
 * caducado.
 */
@ExtendWith(OutputCaptureExtension.class)
class AlmacenCertificadosEnDiscoTest {

    private static final String DIRECTORIO = "src/test/resources/certificados";
    private static final Nif VIGENTE = new Nif("89890001K");
    private static final Nif CADUCADO = new Nif("00000001R");
    private static final Nif CONTRASENA_MALA = new Nif("00000002W");

    private static final Instant AHORA = Instant.parse("2026-09-08T10:00:00Z");

    private final AlmacenCertificadosEnDisco almacen = almacenA(AHORA);

    private static AlmacenCertificadosEnDisco almacenA(Instant momento) {
        return new AlmacenCertificadosEnDisco(
                new PropiedadesCertificados(
                        DIRECTORIO,
                        Map.of(
                                "89890001K", "cambiar",
                                "00000001R", "cambiar",
                                "00000002W", "n0-es-la-buena")),
                Clock.fixed(momento, ZoneOffset.UTC));
    }

    @Test
    void cargaElCertificadoDelObligadoPorSuNif() {
        CertificadoDeObligado certificado = almacen.de(VIGENTE);

        assertThat(certificado.titular()).contains("Obligado de prueba SL");
        assertThat(certificado.caducadoA(AHORA)).isFalse();
    }

    @Test
    void entregaLosGestoresDeClaveParaElTlsMutuo() {
        assertThat(almacen.de(VIGENTE).gestoresDeClave()).isNotEmpty();
    }

    /** Ningún método expone la contraseña ni un {@code char[]}. */
    @Test
    void noHayFormaDeLeerLaContrasena() {
        assertThat(CertificadoDeObligado.class.getMethods())
                .noneMatch(metodo -> metodo.getName().toLowerCase().contains("contrasena")
                        || metodo.getReturnType() == char[].class);
    }

    // --- Caducidad ---

    @Test
    void unCertificadoCaducadoSeCargaPeroSeDenuncia(CapturedOutput salida) {
        CertificadoDeObligado certificado = almacen.de(CADUCADO);

        assertThat(certificado.caducadoA(AHORA)).isTrue();
        assertThat(salida).contains("CADUCÓ").contains("00000001R");
    }

    /** Un certificado caducado se carga; quien decide qué hacer es el despachador. */
    @Test
    void cargarUnCertificadoCaducadoNoLanza() {
        assertThat(almacen.de(CADUCADO)).isNotNull();
    }

    /** El aviso se prueba adelantando el reloj. */
    @Test
    void avisaCuandoQuedanMenosDeTreintaDias(CapturedOutput salida) {
        CertificadoDeObligado certificado = almacen.de(VIGENTE);
        Instant vispera = certificado.caducaEn().minusSeconds(10 * 24 * 3600);

        almacenA(vispera).de(VIGENTE);

        assertThat(salida).contains("caduca el").contains("89890001K");
    }

    @Test
    void conMargenDeSobraNoAvisaDeNada(CapturedOutput salida) {
        almacen.de(VIGENTE);

        assertThat(salida).doesNotContain("caduca el").doesNotContain("CADUCÓ");
    }

    // --- Los cuatro motivos por los que no hay certificado ---

    @Test
    void sinFicheroLoDiceConLaRutaQueBusco() {
        assertThatThrownBy(() -> almacen.de(new Nif("00000003A")))
                .isInstanceOf(CertificadoNoDisponibleException.class)
                .hasMessageContaining("00000003A")
                .hasMessageContaining("no hay fichero legible");
    }

    /** Con un obligado que sí tiene fichero: la ruta se comprueba antes que la contraseña. */
    @Test
    void sinContrasenaConfiguradaLoDiceSinInventarsela() {
        AlmacenCertificadosEnDisco sinContrasenas = new AlmacenCertificadosEnDisco(
                new PropiedadesCertificados(DIRECTORIO, Map.of()), Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> sinContrasenas.de(VIGENTE))
                .isInstanceOf(CertificadoNoDisponibleException.class)
                .hasMessageContaining("89890001K")
                .hasMessageContaining("no hay contraseña configurada");
    }

    @Test
    void sinDirectorioConfiguradoLoDiceEnVezDeBuscarEnLaRaiz() {
        AlmacenCertificadosEnDisco sinConfigurar = new AlmacenCertificadosEnDisco(
                new PropiedadesCertificados("  ", Map.of()), Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> sinConfigurar.de(VIGENTE))
                .isInstanceOf(CertificadoNoDisponibleException.class)
                .hasMessageContaining("lacre.certificados.directorio");
    }

    /**
     * Un almacén con el certificado y sin su clave privada, como el que queda al exportar un
     * {@code .cer}. Se fija el mensaje de error.
     */
    @Test
    void unCertificadoSinClavePrivadaNoSirveParaAutenticarse() throws Exception {
        KeyStore conClave = KeyStore.getInstance("PKCS12");
        try (InputStream entrada = Files.newInputStream(Path.of(DIRECTORIO, "89890001K.p12"))) {
            conClave.load(entrada, "cambiar".toCharArray());
        }
        KeyStore soloPublico = KeyStore.getInstance("PKCS12");
        soloPublico.load(null, null);
        soloPublico.setCertificateEntry("elCertificado", conClave.getCertificate("obligado"));

        assertThatThrownBy(() -> CertificadoDeObligado.desde(soloPublico, "da igual".toCharArray(), VIGENTE.valor()))
                .isInstanceOf(CertificadoNoDisponibleException.class)
                .hasMessageContaining("no contiene ninguna clave privada");
    }

    /**
     * {@code 00000002W.p12} es el certificado bueno con otra contraseña configurada. El mensaje
     * no debe llevar ninguna de las dos contraseñas.
     */
    @Test
    void conLaContrasenaEquivocadaFallaSinFiltrarla() {
        assertThatThrownBy(() -> almacen.de(CONTRASENA_MALA))
                .isInstanceOf(CertificadoNoDisponibleException.class)
                .hasMessageContaining("00000002W")
                .hasMessageNotContaining("cambiar")
                .hasMessageNotContaining("n0-es-la-buena");
    }
}
