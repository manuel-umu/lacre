package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.AlmacenCertificados;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.identidad.CertificadoDeObligado;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.internal.xml.EscritorRegistro;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.SistemaInformatico;
import dev.lacre.verifactu.registro.TipoFactura;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remisión real contra el entorno de pruebas de la AEAT. Solo se ejecuta con
 * {@code LACRE_PORTAL_PRUEBAS=si}, porque registra de verdad en los sistemas de la AEAT. No usa
 * base de datos: construye el registro en memoria y lo remite.
 *
 * <h2>Cómo se lanza</h2>
 * <pre>{@code
 * LACRE_PORTAL_PRUEBAS=si \
 * LACRE_CERT_P12=ruta/al/certificado.p12 \
 * LACRE_CERT_PASS=... \
 * LACRE_OBLIGADO_NIF=99999999R  * LACRE_OBLIGADO_NOMBRE="APELLIDOS NOMBRE" \
 * ./mvnw test -Dtest=PortalDePruebasTest
 * }</pre>
 * <p>
 * El NIF tiene que ser el del titular del certificado. Con un certificado de sello el endpoint
 * es {@code prewww10} en vez de {@code prewww1}; se cambia con {@code LACRE_AEAT_ENDPOINT}.
 * <p>
 * Los envíos quedan registrados: el número de serie lleva marca de tiempo, y a partir del
 * segundo envío es normal recibir el error admisible 2007, «no debe informarse como primer
 * registro».
 */
@EnabledIfEnvironmentVariable(named = "LACRE_PORTAL_PRUEBAS", matches = "si")
class PortalDePruebasTest {

    private static final String ENDPOINT_PRUEBAS =
            "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP";

    private static final Nif OBLIGADO_NIF = new Nif(env("LACRE_OBLIGADO_NIF"));
    /** Debe coincidir con el censo de la AEAT; para una persona física, «APELLIDOS NOMBRE». */
    private static final String NOMBRE_OBLIGADO = env("LACRE_OBLIGADO_NOMBRE");

    /** Sube el nivel a DEBUG para ver el cuerpo de las respuestas. */
    @BeforeAll
    static void verLaRespuestaEntera() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("dev.lacre"))
                .setLevel(Level.DEBUG);
    }

    private final ClienteAeat aeat = clienteReal();

    // --- 1. Que el transporte funciona de extremo a extremo ---

    /** TLS mutuo con certificado real, sobre SOAP y respuesta leída. */
    @Test
    void unAltaLlegaALaAeatYResponde() {
        RespuestaRemision respuesta = remitir(alta(numeroDeSerie("ALTA"), desgloseQueCuadra(),
                Importe.de("23.33"), Importe.de("134.43")));

        informar("ALTA SIMPLE", respuesta);
        assertThat(respuesta.lineas()).isNotEmpty();
    }

    // --- 2. Cuadre de totales por clave de régimen ---

    /**
     * Descuadre con todas las líneas en régimen general: caso de control, debe devolver el error
     * admisible 2005 o 2006.
     */
    @Test
    void unDescuadreSinClaveExentaDebeSerDenunciado() {
        RespuestaRemision respuesta = remitir(alta(numeroDeSerie("DESCUADRE"), desgloseQueCuadra(),
                Importe.de("999.99"), Importe.de("999.99")));

        informar("DESCUADRE, TODAS LAS LÍNEAS EN RÉGIMEN GENERAL", respuesta);
    }

    /**
     * El mismo descuadre con una sola línea en clave 03. Sin error 2005/2006, la exclusión es por
     * registro, como interpreta {@code seContrastanLosTotales()}; con error, es por línea. Enlaza
     * con un alta previa: la respuesta trae un único error por registro, y un 2007 lo taparía.
     */
    @Test
    void unDescuadreConUnaLineaEnClaveExentaResuelveLaInterpretacion() {
        Desglose mixto = Desglose.de(
                new DetalleDesglose(Impuesto.IVA, new ClaveRegimen("01"), CalificacionOperacion.S1,
                        Porcentaje.de("21"), Importe.de("100.00"), null, Importe.de("21.00"), null, null),
                new DetalleDesglose(Impuesto.IVA, new ClaveRegimen("03"), CalificacionOperacion.S1,
                        Porcentaje.de("21"), Importe.de("100.00"), null, Importe.de("21.00"), null, null));

        RegistroEncadenado previo = encadenar(alta(numeroDeSerie("PREVIO"), desgloseQueCuadra(),
                Importe.de("23.33"), Importe.de("134.43")), Optional.empty());
        informar("ALTA PREVIA", remitir(previo));

        DatosRegistroAlta datos = alta(numeroDeSerie("MIXTO"), mixto,
                Importe.de("999.99"), Importe.de("999.99"));
        RespuestaRemision respuesta = remitir(encadenar(datos, Optional.of(
                new RegistroAnterior(previo.datos().idFactura(), previo.huella()))));

        informar("DESCUADRE CON UNA LÍNEA EN CLAVE 03", respuesta);
    }

    // --- Apoyo ---

    private RespuestaRemision remitir(DatosRegistro datos) {
        return remitir(encadenar(datos, Optional.empty()));
    }

    private RegistroEncadenado encadenar(DatosRegistro datos, Optional<RegistroAnterior> anterior) {
        return new EncadenadorRegistros(Clock.systemUTC(), new CanonicalizadorAeat())
                .encadenar(datos, anterior, ZoneId.of("Europe/Madrid"));
    }

    private RespuestaRemision remitir(RegistroEncadenado encadenado) {
        return aeat.remitir(obligado(), List.of(EscritorRegistro.escribir(encadenado)));
    }

    private DatosRegistroAlta alta(String numSerie, Desglose desglose, Importe cuota, Importe total) {
        return DatosRegistroAlta.builder()
                .idFactura(new IdFactura(OBLIGADO_NIF, numSerie, LocalDate.now()))
                .nombreRazonEmisor(NOMBRE_OBLIGADO)
                .tipoFactura(TipoFactura.F1)
                .descripcionOperacion("Prueba de integración de lacre contra el Portal de Pruebas")
                .destinatarios(List.of(new PersonaFisicaJuridica("Cliente SL", new Nif("A28015865"))))
                .desglose(desglose)
                .cuotaTotal(cuota)
                .importeTotal(total)
                .sistemaInformatico(sistemaInformatico())
                .build();
    }

    private static Desglose desgloseQueCuadra() {
        return Desglose.de(new DetalleDesglose(
                Impuesto.IVA, new ClaveRegimen("01"), CalificacionOperacion.S1,
                Porcentaje.de("21"), Importe.de("111.10"), null, Importe.de("23.33"), null, null));
    }

    /** Identidad declarada como sistema informático, con el nombre del censo del titular. */
    private static SistemaInformatico sistemaInformatico() {
        return new SistemaInformatico(
                new PersonaFisicaJuridica(NOMBRE_OBLIGADO, OBLIGADO_NIF),
                "lacre", "01", "0.0.1", "0001",
                true, true, false);
    }

    private static ObligadoTributario obligado() {
        return ObligadoTributario.nuevo(UUID.randomUUID(), OBLIGADO_NIF, NOMBRE_OBLIGADO,
                ZoneId.of("Europe/Madrid"));
    }

    /** Único por ejecución: repetirlo devolvería el código 3000, «registro duplicado». */
    private static String numeroDeSerie(String prefijo) {
        return prefijo + "-" + System.currentTimeMillis();
    }

    private ClienteAeat clienteReal() {
        String endpoint = soloPreproduccion(
                System.getenv().getOrDefault("LACRE_AEAT_ENDPOINT", ENDPOINT_PRUEBAS));
        return new ClienteAeatSoap(new PropiedadesAeat(endpoint, Duration.ofSeconds(60)),
                almacenDelKit());
    }

    /** Abre el PKCS#12 indicado, sin exigir la convención {@code <NIF>.p12}. */
    private static AlmacenCertificados almacenDelKit() {
        Path fichero = Path.of(env("LACRE_CERT_P12"));
        char[] contrasena = env("LACRE_CERT_PASS").toCharArray();
        return nif -> {
            try (InputStream entrada = Files.newInputStream(fichero)) {
                KeyStore almacen = KeyStore.getInstance("PKCS12");
                almacen.load(entrada, contrasena);
                return CertificadoDeObligado.desde(almacen, contrasena, nif.valor());
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo abrir " + fichero, e);
            }
        };
    }

    /**
     * Este test no puede apuntar a producción. Los endpoints de pruebas están bajo
     * {@code aeat.es} con prefijo {@code prewww}; los de producción, bajo
     * {@code agenciatributaria.gob.es}.
     */
    private static String soloPreproduccion(String endpoint) {
        if (!endpoint.startsWith("https://prewww") || !endpoint.contains(".aeat.es/")) {
            throw new IllegalStateException(
                    "Este test solo se lanza contra el entorno de PRUEBAS de la AEAT, y el "
                            + "endpoint configurado no lo es: " + endpoint + ". Con un "
                            + "certificado real, apuntar a producción presenta registros de "
                            + "facturación de verdad a nombre del titular, y eso no tiene "
                            + "vuelta atrás. Los endpoints de pruebas empiezan por "
                            + "https://prewww y están en aeat.es.");
        }
        return endpoint;
    }

    private static String env(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + nombre
                    + "; ver el Javadoc de esta clase");
        }
        return valor;
    }

    /** Imprime la respuesta: el código de cada línea es lo que interesa leer. */
    private static void informar(String caso, RespuestaRemision respuesta) {
        System.out.println("========== " + caso);
        System.out.println("  EstadoEnvio      : " + respuesta.estado());
        System.out.println("  CSV              : " + respuesta.csv());
        System.out.println("  TiempoEsperaEnvio: " + respuesta.tiempoEspera().toSeconds() + " s");
        respuesta.lineas().forEach(linea -> System.out.println(
                "  " + linea.idFactura().numSerieFactura() + " [" + linea.tipo() + "] "
                        + linea.estado()
                        + (linea.codigoError() == null ? "" : " · " + linea.codigoError()
                        + " · " + linea.descripcionError())));
    }
}
