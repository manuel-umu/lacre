package dev.lacre.verifactu.internal.xml;

import dev.lacre.shared.Huella;
import dev.lacre.shared.IdOtro;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.internal.EsquemasAeat;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import dev.lacre.verifactu.registro.TipoFactura;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serialización validada contra el XSD oficial: {@code RegistroFacturacionAltaType} es una
 * {@code sequence} y un elemento fuera de orden invalida el documento.
 */
class EscritorRegistroTest {

    private static final Instant MOMENTO = Instant.parse("2024-01-01T18:20:30Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final EncadenadorRegistros ENCADENADOR = new EncadenadorRegistros(
            Clock.fixed(MOMENTO, ZoneOffset.UTC), new CanonicalizadorAeat());

    private static final Huella HUELLA_ANTERIOR = new Huella(
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60");

    @Test
    void elPrimerRegistroDeLaCadenaValidaContraElEsquemaOficial() {
        String xml = EscritorRegistro.escribir(encadenar(Optional.empty()));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml).contains("<sf:PrimerRegistro>S</sf:PrimerRegistro>");
    }

    @Test
    void unRegistroEnlazadoValidaYLlevaLaIdentificacionCompletaDelAnterior() {
        String xml = EscritorRegistro.escribir(
                encadenar(Optional.of(Registros.anterior(HUELLA_ANTERIOR))));

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
    void unRegistroConTodoInformadoSigueValidando() {
        DatosRegistroAlta datos = Registros.alta()
                .refExterna("PEDIDO-2024-0001")
                .tipoFactura(TipoFactura.R1)
                .tipoRectificativa(dev.lacre.verifactu.registro.ClaveTipoRectificativa.I)
                .facturasRectificadas(List.of(Registros.idFactura("FA/ORIGINAL")))
                .importeRectificacion(new dev.lacre.verifactu.registro.ImporteRectificacion(
                        Importe.de("100.00"), Importe.de("21.00"), Importe.de("5.20")))
                .fechaOperacion(LocalDate.of(2023, 12, 28))
                .subsanacion(true)
                .rechazoPrevio(dev.lacre.verifactu.registro.RechazoPrevio.S)
                .macrodato(true)
                .cupon(true)
                .emitidaPorTerceroODestinatario(dev.lacre.verifactu.registro.EmitidaPor.T)
                .tercero(new PersonaFisicaJuridica("Asesoría SL", new Nif("B12345674")))
                .numRegistroAcuerdoFacturacion("ACU-000001")
                .idAcuerdoSistemaInformatico("SIF-0001")
                .build();

        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(datos, Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        assertThat(validar(xml)).isEmpty();
    }

    @Test
    void unDestinatarioExtranjeroSeSerializaComoIdOtro() {
        DatosRegistroAlta datos = Registros.alta()
                .destinatarios(List.of(new PersonaFisicaJuridica("Jean Dupont",
                        new IdOtro("FR", TipoIdentificacion.PASAPORTE, "12AB34567"))))
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

        assertThat(xml)
                .doesNotContain("Macrodato")
                .doesNotContain("Cupon")
                .doesNotContain("Subsanacion");
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
        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(
                Registros.anulacion(), Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .startsWith("<sf:RegistroAnulacion")
                .contains("<sf:IDEmisorFacturaAnulada>89890001K</sf:IDEmisorFacturaAnulada>")
                .contains("<sf:NumSerieFacturaAnulada>12345679/G34</sf:NumSerieFacturaAnulada>")
                .contains("<sf:FechaExpedicionFacturaAnulada>01-01-2024</sf:FechaExpedicionFacturaAnulada>")
                .contains("<sf:GeneradoPor>E</sf:GeneradoPor>");
    }

    @Test
    void unaAnulacionNoLlevaDesgloseNiImportes() {
        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(Registros.anulacion(), Optional.empty(), MADRID));

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
                        Registros.idFactura("12345679/G34"), "REF-ANU-1", true, true,
                        dev.lacre.verifactu.registro.GeneradoPor.T,
                        new PersonaFisicaJuridica("Asesoría SL", new Nif("B12345674")),
                        Registros.sistemaInformatico());

        String xml = EscritorRegistro.escribir(
                ENCADENADOR.encadenar(datos, Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        assertThat(validar(xml)).isEmpty();
        assertThat(xml)
                .contains("<sf:SinRegistroPrevio>S</sf:SinRegistroPrevio>")
                .contains("<sf:RechazoPrevio>S</sf:RechazoPrevio>")
                .contains("<sf:Generador>");
    }

    @Test
    void coincideConElFicheroGoldenDeAnulacion() throws Exception {
        String xml = EscritorRegistro.escribir(ENCADENADOR.encadenar(
                Registros.anulacion(), Optional.of(Registros.anterior(HUELLA_ANTERIOR)), MADRID));

        Diff diff = DiffBuilder.compare(golden("registro-anulacion.xml"))
                .withTest(xml).ignoreWhitespace().checkForSimilar().build();

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
                .withFailMessage("El XML cambió respecto del golden:%n%s%n%nGenerado:%n%s",
                        diff.toString(), xml)
                .isFalse();
    }

    private static RegistroEncadenado encadenar(Optional<RegistroAnterior> anterior) {
        return ENCADENADOR.encadenar(Registros.alta().build(), anterior, MADRID);
    }

    /** Devuelve el mensaje del error de validación, o vacío si el documento es válido. */
    private static Optional<String> validar(String xml) {
        try {
            Validator validador = EsquemasAeat.compilar("SuministroInformacion.xsd").newValidator();
            validador.validate(new StreamSource(new StringReader(xml)));
            return Optional.empty();
        } catch (SAXException e) {
            return Optional.of(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
