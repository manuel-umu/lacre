package dev.lacre.verifactu.internal.xml;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.shared.Huella;
import dev.lacre.shared.IdOtro;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.desglose.OperacionExenta;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.internal.EsquemasAeat;
import dev.lacre.verifactu.registro.ClaveTipoRectificativa;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.EmitidaPor;
import dev.lacre.verifactu.registro.ImporteRectificacion;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.RechazoPrevio;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import dev.lacre.verifactu.registro.SistemaInformatico;
import dev.lacre.verifactu.registro.TipoFactura;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

/**
 * Serialización validada contra el XSD oficial: {@code RegistroFacturacionAltaType} es una
 * {@code sequence} y un elemento fuera de orden invalida el documento.
 */
class EscritorRegistroTest {

    private static final Instant MOMENTO = Instant.parse("2024-01-01T18:20:30Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final EncadenadorRegistros ENCADENADOR =
            new EncadenadorRegistros(Clock.fixed(MOMENTO, ZoneOffset.UTC), new CanonicalizadorAeat());

    private static final Huella HUELLA_ANTERIOR =
            new Huella("3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60");

    @Test
    void elPrimerRegistroDeLaCadenaValidaContraElEsquemaOficial() {
        String xml = EscritorRegistro.escribir(encadenar(Optional.empty()));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml).contains("<sf:PrimerRegistro>S</sf:PrimerRegistro>");
    }

    @Test
    void unRegistroEnlazadoValidaYLlevaLaIdentificacionCompletaDelAnterior() {
        String xml = EscritorRegistro.escribir(encadenar(Optional.of(Registros.anterior(HUELLA_ANTERIOR))));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .contains("<sf:RegistroAnterior>")
                .contains("<sf:IDEmisorFactura>89890001K</sf:IDEmisorFactura>")
                .contains("<sf:NumSerieFactura>12345678/G32</sf:NumSerieFactura>")
                .contains("<sf:FechaExpedicionFactura>01-01-2024</sf:FechaExpedicionFactura>")
                .contains("<sf:Huella>" + HUELLA_ANTERIOR.valor() + "</sf:Huella>")
                .doesNotContain("PrimerRegistro");
    }

    @Test
    void laHuellaDelRegistroViajaEnElXmlJuntoConSuAlgoritmo() {
        RegistroEncadenado registro = encadenar(Optional.empty());

        assertThat(EscritorRegistro.escribir(registro))
                .contains("<sf:TipoHuella>01</sf:TipoHuella>")
                .contains("<sf:Huella>" + registro.huella().valor() + "</sf:Huella>");
    }

    @Test
    void unRegistroConTodoInformadoValidaYCoincideConSuGolden() throws Exception {
        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(altaCompleta(), Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(diferenciasCon("registro-alta-completo.xml", xml)).isEmpty();
    }

    @Test
    void unaLineaExentaSeSerializaComoOperacionExenta() {
        DatosRegistroAlta datos = Registros.alta()
                .desglose(Desglose.de(new DetalleDesglose(
                        Impuesto.IVA,
                        new ClaveRegimen("01"),
                        OperacionExenta.E1,
                        null,
                        Importe.de("123.45"),
                        null,
                        null,
                        null,
                        null)))
                .build();

        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(datos, Optional.empty(), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml).contains("<sf:OperacionExenta>E1</sf:OperacionExenta>").doesNotContain("CalificacionOperacion");
    }

    @Test
    void unaSustitutivaLlevaLasFacturasQueSustituye() {
        DatosRegistroAlta datos = Registros.alta()
                .tipoFactura(TipoFactura.F3)
                .facturasSustituidas(List.of(Registros.idFactura("F2/SUSTITUIDA")))
                .build();

        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(datos, Optional.empty(), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .contains("<sf:FacturasSustituidas><sf:IDFacturaSustituida>")
                .contains("<sf:NumSerieFactura>F2/SUSTITUIDA</sf:NumSerieFactura>");
    }

    @Test
    void unaSimplificadaSinIdentificarAlDestinatarioLlevaSuMarca() {
        DatosRegistroAlta datos = Registros.alta()
                .tipoFactura(TipoFactura.F2)
                .destinatarios(List.of())
                .facturaSinIdentifDestinatarioArt61d(true)
                .build();

        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(datos, Optional.empty(), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml).contains("<sf:FacturaSinIdentifDestinatarioArt61d>S</sf:FacturaSinIdentifDestinatarioArt61d>");
    }

    @Test
    void unDestinatarioExtranjeroSeSerializaComoIdOtro() {
        DatosRegistroAlta datos = Registros.alta()
                .destinatarios(List.of(new PersonaFisicaJuridica(
                        "Jean Dupont", new IdOtro("FR", TipoIdentificacion.PASAPORTE, "12AB34567"))))
                .build();

        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(datos, Optional.empty(), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .contains("<sf:CodigoPais>FR</sf:CodigoPais>")
                .contains("<sf:IDType>03</sf:IDType>")
                .contains("<sf:ID>12AB34567</sf:ID>");
    }

    @Test
    void losIndicadoresEnFalsoNoSeEmiten() {
        String xml = EscritorRegistro.escribir(encadenar(Optional.empty()));

        assertThat(xml).doesNotContain("Macrodato").doesNotContain("Cupon").doesNotContain("Subsanacion");
    }

    @Test
    void noEmiteDeclaracionXmlParaPoderEmbeberseEnUnLote() {
        assertThat(EscritorRegistro.escribir(encadenar(Optional.empty())))
                .doesNotContain("<?xml")
                .startsWith("<sf:RegistroAlta");
    }

    // --- Registro de anulación ---

    @Test
    void unaAnulacionValidaContraElEsquemaOficial() {
        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(Registros.anulacion(), Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .startsWith("<sf:RegistroAnulacion")
                .contains("<sf:IDEmisorFacturaAnulada>89890001K</sf:IDEmisorFacturaAnulada>")
                .contains("<sf:NumSerieFacturaAnulada>12345679/G34</sf:NumSerieFacturaAnulada>")
                .contains("<sf:FechaExpedicionFacturaAnulada>01-01-2024</sf:FechaExpedicionFacturaAnulada>")
                .doesNotContain("<sf:GeneradoPor>");
    }

    @Test
    void unaAnulacionNoLlevaDesgloseNiImportes() {
        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(Registros.anulacion(), Optional.empty(), MADRID));

        assertThat(xml)
                .doesNotContain("Desglose")
                .doesNotContain("CuotaTotal")
                .doesNotContain("ImporteTotal")
                .doesNotContain("TipoFactura");
    }

    @Test
    void unaAnulacionGeneradaPorUnTerceroValidaConSuGenerador() {
        dev.lacre.verifactu.registro.DatosRegistroAnulacion datos =
                new dev.lacre.verifactu.registro.DatosRegistroAnulacion(
                        Registros.idFactura("12345679/G34"),
                        "REF-ANU-1",
                        true,
                        true,
                        dev.lacre.verifactu.registro.GeneradoPor.T,
                        new PersonaFisicaJuridica("Asesoría SL", new Nif("B12345674")),
                        Registros.sistemaInformatico());

        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(datos, Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .contains("<sf:RefExterna>REF-ANU-1</sf:RefExterna>")
                .contains("<sf:SinRegistroPrevio>S</sf:SinRegistroPrevio>")
                .contains("<sf:RechazoPrevio>S</sf:RechazoPrevio>")
                .contains("<sf:GeneradoPor>T</sf:GeneradoPor>")
                .contains("<sf:Generador>");
    }

    @Test
    void coincideConElFicheroGoldenDeAnulacion() throws Exception {
        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(Registros.anulacion(), Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        Diff diff = DiffBuilder.compare(golden("registro-anulacion.xml"))
                .withTest(xml)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();

        assertThat(diff.hasDifferences())
                .withFailMessage("El XML cambió respecto del golden:%n%s%n%nGenerado:%n%s", diff, xml)
                .isFalse();
    }

    @Test
    void coincideConElFicheroGolden() throws Exception {
        String xml = EscritorRegistro.escribir(encadenar(Optional.empty()));

        Diff diff = DiffBuilder.compare(golden("registro-alta-primero.xml"))
                .withTest(xml)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();

        assertThat(diff.hasDifferences())
                .withFailMessage("El XML cambió respecto del golden:%n%s%n%nGenerado:%n%s", diff.toString(), xml)
                .isFalse();
    }

    /**
     * R1 por sustitución con todos los campos opcionales compatibles entre sí, y cada indicador y
     * cada valor distinto del que tendría si no se informara.
     */
    private static DatosRegistroAlta altaCompleta() {
        return Registros.alta()
                .refExterna("PEDIDO-2024-0001")
                .tipoFactura(TipoFactura.R1)
                .tipoRectificativa(ClaveTipoRectificativa.S)
                .facturasRectificadas(List.of(Registros.idFactura("FA/ORIGINAL")))
                .importeRectificacion(
                        new ImporteRectificacion(Importe.de("100.00"), Importe.de("21.00"), Importe.de("5.20")))
                .fechaOperacion(LocalDate.of(2023, 12, 28))
                .subsanacion(true)
                .rechazoPrevio(RechazoPrevio.S)
                .facturaSimplificadaArt7273(true)
                .macrodato(true)
                .importeTotal(Importe.de("100000000.00"))
                .cupon(true)
                .emitidaPorTerceroODestinatario(EmitidaPor.T)
                .tercero(new PersonaFisicaJuridica("Asesoría SL", new Nif("B12345674")))
                .desglose(Desglose.de(
                        new DetalleDesglose(
                                Impuesto.IVA,
                                new ClaveRegimen("01"),
                                CalificacionOperacion.S1,
                                Porcentaje.de("21"),
                                Importe.de("111.10"),
                                null,
                                Importe.de("23.33"),
                                Porcentaje.de("5.2"),
                                Importe.de("5.78")),
                        new DetalleDesglose(
                                Impuesto.IPSI,
                                new ClaveRegimen("01"),
                                CalificacionOperacion.S1,
                                Porcentaje.de("10"),
                                Importe.de("50.00"),
                                Importe.de("40.00"),
                                Importe.de("4.00"),
                                null,
                                null)))
                .numRegistroAcuerdoFacturacion("ACU-000001")
                .idAcuerdoSistemaInformatico("SIF-0001")
                .sistemaInformatico(new SistemaInformatico(
                        new PersonaFisicaJuridica("lacre", new Nif("12345678Z")),
                        "lacre",
                        "01",
                        "0.0.1",
                        "0001",
                        false,
                        true,
                        true))
                .build();
    }

    private static RegistroEncadenado encadenar(Optional<RegistroAnterior> anterior) {
        return ENCADENADOR.encadenar(Registros.alta().build(), anterior, MADRID);
    }

    /** Devuelve el mensaje del error de validación, o vacío si el documento es válido. */
    private static Optional<String> validar(String xml) {
        try {
            Validator validador =
                    EsquemasAeat.compilar("SuministroInformacion.xsd").newValidator();
            validador.validate(new StreamSource(new StringReader(xml)));
            return Optional.empty();
        } catch (SAXException e) {
            return Optional.of(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Diferencias entre el XML y su fichero golden, o vacío si coinciden. */
    private static String diferenciasCon(String golden, String xml) throws IOException {
        Diff diff = DiffBuilder.compare(golden(golden))
                .withTest(xml)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();
        return diff.hasDifferences() ? diff + "%n%nGenerado:%n".formatted() + xml : "";
    }

    private static String golden(String nombre) throws IOException {
        try (var entrada = EscritorRegistroTest.class.getResourceAsStream("/golden/" + nombre)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el fichero golden " + nombre);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
