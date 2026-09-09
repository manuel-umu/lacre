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
 * Fase 6.5: remisión real contra el Portal de Pruebas Externas de la AEAT.
 * <p>
 * <strong>No se ejecuta salvo que se pida.</strong> Hace falta la variable de entorno
 * {@code LACRE_PORTAL_PRUEBAS=si}, así que {@code mvn test} nunca sale a la red ni manda nada a
 * nadie. Es lo único razonable para un test que <em>registra de verdad</em> en los sistemas de
 * la AEAT.
 * <p>
 * Tampoco necesita base de datos: construye el registro en memoria, lo serializa y lo remite. Lo
 * que se prueba aquí es lo único que WireMock no puede cubrir —el <strong>TLS mutuo con un
 * certificado real</strong> y el criterio de la AEAT sobre nuestro XML—, no la persistencia, que
 * ya está probada contra Postgres.
 *
 * <h2>Cómo se lanza</h2>
 * <pre>{@code
 * LACRE_PORTAL_PRUEBAS=si \
 * LACRE_CERT_P12="docs/certs/AC FNMT Usuarios/Nuevos/Nuevo Perfil no SMIME/ACTIVO_EIDAS_CERTIFICADO_PRUEBAS___99999999R.p12" \
 * LACRE_CERT_PASS=... \
 * LACRE_OBLIGADO_NIF=99999999R \
 * ./mvnw test -Dtest=PortalDePruebasTest
 * }</pre>
 * <p>
 * El NIF <strong>tiene que ser el del titular del certificado</strong>: la AEAT valida que
 * {@code IDEmisorFactura} coincida con el {@code ObligadoEmision} de la cabecera, y que quien
 * presenta esté autorizado. Con un certificado de sello de empresa el endpoint es
 * {@code prewww10} en vez de {@code prewww1}; se cambia con {@code LACRE_AEAT_ENDPOINT}.
 *
 * <h2>Qué contesta y qué no</h2>
 * Estos envíos <strong>quedan registrados</strong> en el entorno de pruebas, así que el número de
 * serie lleva marca de tiempo: repetirlo devolvería el código 3000, «registro duplicado». Y a
 * partir del segundo envío es normal recibir el error <strong>admisible</strong> 2007 —«no debe
 * informarse como primer registro»—, porque ya existe cadena para ese NIF. No es un fallo
 * nuestro.
 * <p>
 * El ámbito del control de flujo <strong>no se puede resolver aquí</strong>: el catálogo no tiene
 * ningún código de error para haber remitido demasiado deprisa, así que no hay violación
 * observable, y con un solo certificado tampoco se pueden comparar dos obligados.
 */
@EnabledIfEnvironmentVariable(named = "LACRE_PORTAL_PRUEBAS", matches = "si")
class PortalDePruebasTest {

    private static final String ENDPOINT_PRUEBAS =
            "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP";

    private static final Nif OBLIGADO_NIF = new Nif(env("LACRE_OBLIGADO_NIF"));
    /**
     * <strong>Tiene que coincidir con el censo de la AEAT</strong>, no ser un nombre cualquiera:
     * el código 4104 —«el NIF del bloque ObligadoEmision no está identificado»— sale tanto si el
     * NIF no existe como si el par NIF/nombre no cuadra, y el Fault devuelve los dos, lo que
     * sugiere que mira el par.
     * <p>
     * Para una persona física el censo usa «APELLIDOS NOMBRE», que en estos certificados de
     * prueba es {@code EIDAS CERTIFICADO PRUEBAS}: el {@code SURNAME} seguido del
     * {@code GIVENNAME} del titular.
     */
    private static final String NOMBRE_OBLIGADO = env("LACRE_OBLIGADO_NOMBRE");

    /**
     * Sube el nivel a DEBUG para ver el cuerpo de lo que conteste la AEAT. En una remisión real
     * ese volcado está apagado —puede traer datos del obligado—, pero aquí es justo lo que se
     * viene a mirar: si algo no se puede interpretar, hay que poder leerlo.
     */
    @BeforeAll
    static void verLaRespuestaEntera() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("dev.lacre"))
                .setLevel(Level.DEBUG);
    }

    private final ClienteAeat aeat = clienteReal();

    // --- 1. Que el transporte funciona de extremo a extremo ---

    /**
     * El primer envío: TLS mutuo con certificado real, sobre SOAP, XML aceptado y respuesta
     * leída. Si esto pasa, lo único que quedaba sin probar del transporte queda probado.
     */
    @Test
    void unAltaLlegaALaAeatYResponde() {
        RespuestaRemision respuesta = remitir(alta(numeroDeSerie("ALTA"), desgloseQueCuadra(),
                Importe.de("12.35"), Importe.de("123.45")));

        informar("ALTA SIMPLE", respuesta);
        assertThat(respuesta.lineas()).isNotEmpty();
    }

    // --- 2. El experimento que cierra la duda del cuadre de totales ---

    /**
     * Un descuadre de totales con <strong>todas</strong> las líneas en régimen general. Debe
     * volver el error admisible 2005 o 2006: es el caso de control, el que demuestra que la AEAT
     * sí contrasta y que el descuadre que enviamos es suficiente para que se note.
     */
    @Test
    void unDescuadreSinClaveExentaDebeSerDenunciado() {
        RespuestaRemision respuesta = remitir(alta(numeroDeSerie("DESCUADRE"), desgloseQueCuadra(),
                Importe.de("999.99"), Importe.de("999.99")));

        informar("DESCUADRE, TODAS LAS LÍNEAS EN RÉGIMEN GENERAL", respuesta);
    }

    /**
     * El mismo descuadre, pero con <strong>una sola</strong> línea en una clave de régimen de las
     * que excluyen la comprobación —la 03— y otra que no.
     * <p>
     * Aquí está la respuesta que buscamos desde la Fase 2:
     * <ul>
     * <li><strong>Sin error 2005/2006</strong> → la exclusión es <em>por registro</em>: basta una
     *     línea exenta para que no se contraste nada. Es lo que hoy interpreta
     *     {@code seContrastanLosTotales()}.</li>
     * <li><strong>Con error</strong> → la exclusión es <em>por línea</em>, y nuestra
     *     interpretación es demasiado laxa: habría que contrastar las líneas no exentas.</li>
     * </ul>
     */
    @Test
    void unDescuadreConUnaLineaEnClaveExentaResuelveLaInterpretacion() {
        Desglose mixto = Desglose.de(
                new DetalleDesglose(Impuesto.IVA, new ClaveRegimen("01"), CalificacionOperacion.S1,
                        Porcentaje.de("21"), Importe.de("100.00"), null, Importe.de("21.00"), null, null),
                new DetalleDesglose(Impuesto.IVA, new ClaveRegimen("03"), CalificacionOperacion.S1,
                        Porcentaje.de("21"), Importe.de("100.00"), null, Importe.de("21.00"), null, null));

        RespuestaRemision respuesta = remitir(alta(numeroDeSerie("MIXTO"), mixto,
                Importe.de("999.99"), Importe.de("999.99")));

        informar("DESCUADRE CON UNA LÍNEA EN CLAVE 03", respuesta);
    }

    // --- Apoyo ---

    private RespuestaRemision remitir(DatosRegistro datos) {
        RegistroEncadenado encadenado = new EncadenadorRegistros(Clock.systemUTC(), new CanonicalizadorAeat())
                .encadenar(datos, Optional.empty(), ZoneId.of("Europe/Madrid"));
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
                Porcentaje.de("21"), Importe.de("111.10"), null, Importe.de("12.35"), null, null));
    }

    /**
     * La identidad que declaramos como sistema informático. <strong>Es una de las preguntas
     * abiertas</strong>: cuando lacre actúa como CF dentro del CPF de otro, no está confirmado si
     * aquí va la identidad del ERP o la nuestra. Que la AEAT acepte esto demuestra que se admite,
     * no que sea lo correcto.
     */
    private static SistemaInformatico sistemaInformatico() {
        return new SistemaInformatico(
                new PersonaFisicaJuridica("lacre", OBLIGADO_NIF),
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

    /**
     * Abre el PKCS#12 que se le indique, sin exigir la convención {@code <NIF>.p12} del almacén
     * de producción: los ficheros del kit de la FNMT vienen con su propio nombre y no se tocan.
     */
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
     * <strong>Este test no puede apuntar a producción, y punto.</strong>
     * <p>
     * Con un certificado real, la diferencia entre {@code prewww1} y {@code www1} es la que
     * hay entre una prueba y presentar registros de facturación de verdad a nombre de su
     * titular. Eso no se deshace, y un despiste de un carácter no puede tener esa
     * consecuencia: los cuatro endpoints del WSDL se parecen entre sí, y el equivocado no
     * avisa, responde.
     * <p>
     * Los de pruebas de la AEAT están bajo {@code aeat.es} con prefijo {@code prewww}; los de
     * producción, bajo {@code agenciatributaria.gob.es}.
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

    /**
     * El valor de esta fase está en <em>leer</em> la respuesta, no en un aserto verde: qué código
     * devuelve cada línea es lo que responde las preguntas que quedan abiertas.
     */
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
