package dev.lacre.identidad.internal.adaptador;

import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.CertificadoNoDisponibleException;
import dev.lacre.shared.Nif;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El almacén contra ficheros PKCS#12 de verdad, y <strong>sin Spring ni base de datos</strong>:
 * esto lee ficheros, y levantar un Postgres para probarlo sería desperdicio.
 * <p>
 * Los certificados de {@code src/test/resources/certificados} son autofirmados, generados con
 * {@code keytool} solo para esto, y su contraseña está a la vista a propósito: no protegen nada.
 * El del NIF {@code 00000001R} nació caducado —{@code -startdate -800d -validity 1}—, porque el
 * aviso de caducidad hay que probarlo con uno caducado y no esperando dos años.
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
                new PropiedadesCertificados(DIRECTORIO, Map.of(
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

    /** Es lo único que la capa de transporte necesita, y lo único que se expone. */
    @Test
    void entregaLosGestoresDeClaveParaElTlsMutuo() {
        assertThat(almacen.de(VIGENTE).gestoresDeClave()).isNotEmpty();
    }

    /**
     * La contraseña no sale del certificado. Se comprueba contra el diseño —ningún método la
     * devuelve ni devuelve un {@code char[]}— y no contra una implementación concreta.
     */
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

    /**
     * Cargar no es remitir. Un certificado caducado no puede impedir que el sistema arranque ni
     * que se sigan generando registros: quien decide qué hacer con él es el despachador.
     */
    @Test
    void cargarUnCertificadoCaducadoNoLanza() {
        assertThat(almacen.de(CADUCADO)).isNotNull();
    }

    /** El aviso previo se prueba adelantando el reloj, no esperando a que caduque el fichero. */
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

    /**
     * Con un obligado que <strong>sí</strong> tiene fichero: si se usara uno que tampoco lo
     * tiene, el fallo por fichero ausente taparía el que se quiere probar, porque la ruta se
     * comprueba antes. Es lo que pasó la primera vez que se escribió este test.
     */
    @Test
    void sinContrasenaConfiguradaLoDiceSinInventarsela() {
        AlmacenCertificadosEnDisco sinContrasenas = new AlmacenCertificadosEnDisco(
                new PropiedadesCertificados(DIRECTORIO, Map.of()),
                Clock.fixed(AHORA, ZoneOffset.UTC));

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
     * El error que de verdad va a cometer quien despliegue esto: dejar un certificado donde iba
     * un almacén de claves. Un {@code .cer} o un {@code .pem} son solo la parte pública, y el TLS
     * mutuo necesita firmar con la privada.
     * <p>
     * El almacén se fabrica sacando el certificado de nuestro propio PKCS#12 y metiéndolo en uno
     * nuevo <em>sin</em> su clave, que es exactamente lo que queda al exportar un {@code .cer}.
     * No se apoya en ningún fichero de fuera del repositorio: un test que dependiera de la
     * carpeta de certificados de quien programa no valdría para nadie más.
     * <p>
     * Lo que se fija es el <strong>mensaje</strong>: quien se equivoque tiene que leer qué le
     * falta, no un {@code NullPointerException} tres capas más abajo.
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

        assertThatThrownBy(() ->
                CertificadoDeObligado.desde(soloPublico, "da igual".toCharArray(), VIGENTE.valor()))
                .isInstanceOf(CertificadoNoDisponibleException.class)
                .hasMessageContaining("no contiene ninguna clave privada");
    }

    /**
     * {@code 00000002W.p12} es una copia del certificado bueno con otra contraseña configurada.
     * Lo que se comprueba no es solo que falle: es que el mensaje <strong>no lleve la
     * contraseña</strong>, ni la buena ni la intentada. Ese texto acaba en el log de quien
     * integra, y de ahí no se recupera.
     * <p>
     * La contraseña de prueba es deliberadamente impronunciable: la primera versión usaba
     * «equivocada», que aparece dentro del propio mensaje de error —«¿ruta o contraseña
     * equivocadas?»— y hacía fallar la comprobación por una coincidencia de texto.
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
