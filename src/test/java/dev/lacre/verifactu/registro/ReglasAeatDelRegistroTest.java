package dev.lacre.verifactu.registro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Validaciones de la AEAT que dependen del registro entero, no de una línea del desglose. */
class ReglasAeatDelRegistroTest {

    private static final PersonaFisicaJuridica ASESORIA =
            new PersonaFisicaJuridica("Asesoría SL", new Nif("B12345674"));
    private static final PersonaFisicaJuridica AYUNTAMIENTO =
            new PersonaFisicaJuridica("Ayuntamiento", new Nif("P2800000H"));
    private static final PersonaFisicaJuridica CON_PASAPORTE = extranjero("FR", TipoIdentificacion.PASAPORTE);
    private static final PersonaFisicaJuridica DE_ESPANA_SIN_PASAPORTE =
            extranjero("ES", TipoIdentificacion.OTRO_DOCUMENTO);
    private static final PersonaFisicaJuridica CON_NIF_IVA =
            new PersonaFisicaJuridica("Client Ltd", new IdOtro("IE", TipoIdentificacion.NIF_IVA, "IE6388047V"));
    private static final ImporteRectificacion RECTIFICADO =
            new ImporteRectificacion(Importe.de("100.00"), Importe.de("21.00"), null);

    static Stream<Arguments> altasQueLaAeatRechazaria() {
        return Stream.of(
                alta(
                        "1118",
                        "rectificativa por sustitución sin importe de rectificación",
                        b -> rectificativa(b, TipoFactura.R1, ClaveTipoRectificativa.S)
                                .importeRectificacion(null)),
                alta(
                        "1119",
                        "rectificativa por diferencias con importe de rectificación",
                        b -> rectificativa(b, TipoFactura.R1, ClaveTipoRectificativa.I)
                                .importeRectificacion(RECTIFICADO)),
                alta(
                        "1183",
                        "F2 con la marca de los artículos 7.2 y 7.3",
                        b -> simplificada(b).facturaSimplificadaArt7273(true)),
                alta("1185", "F1 con la marca del artículo 6.1.d)", b -> b.facturaSinIdentifDestinatarioArt61d(true)),
                alta("1157", "F1 con cupón", b -> b.cupon(true)),
                alta("1139", "cien millones sin marca de macrodato", b -> b.importeTotal(Importe.de("-100000000.00"))),
                alta("1138", "marca de macrodato con un importe pequeño", b -> b.macrodato(true)),
                alta(
                        "1158",
                        "emitida por el destinatario sin destinatarios",
                        b -> simplificada(b).emitidaPorTerceroODestinatario(EmitidaPor.D)),
                alta("1189", "F1 sin destinatarios", b -> b.destinatarios(List.of())),
                alta("1190", "F2 con destinatarios", b -> b.tipoFactura(TipoFactura.F2)),
                alta(
                        "1126",
                        "destinatario de España con otro documento",
                        b -> b.destinatarios(List.of(DE_ESPANA_SIN_PASAPORTE))),
                alta(
                        "1191",
                        "R3 con destinatario con pasaporte",
                        b -> rectificativa(b, TipoFactura.R3, ClaveTipoRectificativa.I)
                                .destinatarios(List.of(CON_PASAPORTE))),
                alta(
                        "1192",
                        "R2 con destinatario con pasaporte",
                        b -> rectificativa(b, TipoFactura.R2, ClaveTipoRectificativa.I)
                                .destinatarios(List.of(CON_PASAPORTE))),
                alta("1155", "tercero sin decir quién emite", b -> b.tercero(ASESORIA)),
                alta(
                        "1159",
                        "tercero en una factura emitida por el destinatario",
                        b -> b.emitidaPorTerceroODestinatario(EmitidaPor.D).tercero(ASESORIA)),
                alta("1186", "emitida por un tercero sin tercero", b -> b.emitidaPorTerceroODestinatario(EmitidaPor.T)),
                alta(
                        "1188",
                        "el tercero es el obligado",
                        b -> b.emitidaPorTerceroODestinatario(EmitidaPor.T)
                                .tercero(new PersonaFisicaJuridica("El mismo", Registros.EMISOR))),
                alta(
                        "1211",
                        "tercero no censado",
                        b -> b.emitidaPorTerceroODestinatario(EmitidaPor.T)
                                .tercero(extranjero("ES", TipoIdentificacion.NO_CENSADO))),
                alta(
                        "1232",
                        "tercero de España con otro documento",
                        b -> b.emitidaPorTerceroODestinatario(EmitidaPor.T)
                                .tercero(extranjero("ES", TipoIdentificacion.OTRO_DOCUMENTO))),
                alta("1150", "F2 de más de 3.010 euros", b -> simplificada(b).desglose(alDiez("3000.00", "300.00"))),
                alta(
                        "1197",
                        "F2 con inversión del sujeto pasivo",
                        b -> simplificada(b)
                                .desglose(
                                        Desglose.de(linea("01", CalificacionOperacion.S2, "0", "100.00", null, "0")))),
                alta(
                        "1202",
                        "F3 con clave 06",
                        b -> b.tipoFactura(TipoFactura.F3)
                                .desglose(Desglose.de(
                                        linea("06", CalificacionOperacion.S1, "10", "100.00", "100.00", "10.00")))),
                alta(
                        "1205",
                        "clave 10 en una F3",
                        b -> b.tipoFactura(TipoFactura.F3).desglose(clave10())),
                alta(
                        "1205",
                        "clave 10 con un destinatario sin NIF",
                        b -> b.desglose(clave10()).destinatarios(List.of(CON_PASAPORTE))),
                alta("1148", "clave 14 en una F3", b -> clave14(b).tipoFactura(TipoFactura.F3)),
                alta("1147", "clave 14 sin fecha de operación", b -> clave14(b).fechaOperacion(null)),
                alta(
                        "1149",
                        "clave 14 con un destinatario que no es Administración",
                        b -> clave14(b).destinatarios(Registros.alta().build().destinatarios())),
                alta(
                        "1146",
                        "expedida antes de la operación sin clave 14 ni 15",
                        b -> b.fechaOperacion(Registros.FECHA_EXPEDICION.plusDays(1))),
                alta(
                        "1194",
                        "tipo del 5 % fuera de su vigencia",
                        b -> b.idFactura(expedidaEl(LocalDate.of(2025, 2, 1)))
                                .desglose(Desglose.de(
                                        linea("01", CalificacionOperacion.S1, "5", "100.00", null, "5.00")))),
                alta(
                        "1194",
                        "tipo del 5 % fuera de su vigencia sin impuesto, que cuenta como IVA",
                        b -> b.idFactura(expedidaEl(LocalDate.of(2025, 2, 1)))
                                .desglose(Desglose.de(sinImpuesto(
                                        linea("01", CalificacionOperacion.S1, "5", "100.00", null, "5.00"))))),
                alta(
                        "1205",
                        "clave 10 en una F3 con IGIC",
                        b -> b.tipoFactura(TipoFactura.F3)
                                .desglose(Desglose.de(conImpuesto(
                                        Impuesto.IGIC, clave10().detalles().getFirst())))),
                alta(
                        "1205",
                        "clave 10 en una F3 sin impuesto, que cuenta como IVA",
                        b -> b.tipoFactura(TipoFactura.F3)
                                .desglose(Desglose.de(
                                        sinImpuesto(clave10().detalles().getFirst())))));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("altasQueLaAeatRechazaria")
    void unAltaQueLaAeatRechazariaNoSeConstruye(
            String codigo, String caso, UnaryOperator<DatosRegistroAlta.Builder> cambio) {
        assertThatThrownBy(() -> cambio.apply(Registros.alta()).build())
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo(codigo));
    }

    static Stream<Arguments> altasQueLaAeatAcepta() {
        return Stream.of(
                arguments(
                        "solo IPSI, expedida antes de la operación",
                        cambio(b -> b.fechaOperacion(Registros.FECHA_EXPEDICION.plusDays(1))
                                .desglose(Desglose.de(soloIpsi())))),
                arguments(
                        "rectificativa por sustitución con importe",
                        cambio(b -> rectificativa(b, TipoFactura.R1, ClaveTipoRectificativa.S))),
                arguments(
                        "macrodato de cien millones",
                        cambio(b -> b.macrodato(true).importeTotal(Importe.de("100000000.00")))),
                arguments(
                        "F2 de 3.010 euros justos", cambio(b -> simplificada(b).desglose(alDiez("2736.36", "273.64")))),
                arguments(
                        "F2 grande sin identificación del destinatario",
                        cambio(b -> simplificada(b)
                                .facturaSinIdentifDestinatarioArt61d(true)
                                .desglose(alDiez("5000.00", "500.00")))),
                arguments(
                        "F2 grande con acuerdo de facturación",
                        cambio(b -> simplificada(b)
                                .numRegistroAcuerdoFacturacion("ACU-1")
                                .desglose(alDiez("5000.00", "500.00")))),
                arguments(
                        "emitida por un tercero",
                        cambio(b ->
                                b.emitidaPorTerceroODestinatario(EmitidaPor.T).tercero(ASESORIA))),
                arguments(
                        "R2 con destinatario con NIF-IVA",
                        cambio(b -> rectificativa(b, TipoFactura.R2, ClaveTipoRectificativa.I)
                                .destinatarios(List.of(CON_NIF_IVA)))),
                arguments(
                        "R3 con destinatario no censado",
                        cambio(b -> rectificativa(b, TipoFactura.R3, ClaveTipoRectificativa.I)
                                .destinatarios(List.of(extranjero("ES", TipoIdentificacion.NO_CENSADO))))),
                arguments(
                        "clave 14 con la Administración y operación posterior",
                        cambio(ReglasAeatDelRegistroTest::clave14)),
                arguments(
                        "clave 15 con operación posterior",
                        cambio(b -> b.fechaOperacion(Registros.FECHA_EXPEDICION.plusMonths(1))
                                .desglose(Desglose.de(
                                        linea("15", CalificacionOperacion.S1, "10", "111.10", null, "12.35"))))),
                arguments(
                        "tipo del 5 % con operación de 2023",
                        cambio(b -> b.fechaOperacion(LocalDate.of(2023, 6, 1))
                                .desglose(Desglose.de(
                                        linea("01", CalificacionOperacion.S1, "5", "100.00", null, "5.00"))))),
                arguments(
                        "tipo del 2 % expedida en noviembre de 2024",
                        cambio(b -> b.idFactura(expedidaEl(LocalDate.of(2024, 11, 15)))
                                .desglose(Desglose.de(
                                        linea("01", CalificacionOperacion.S1, "2", "100.00", null, "2.00"))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("altasQueLaAeatAcepta")
    void unAltaQueLaAeatAceptaSeConstruye(String caso, UnaryOperator<DatosRegistroAlta.Builder> cambio) {
        assertThatCode(() -> cambio.apply(Registros.alta()).build()).doesNotThrowAnyException();
    }

    /** Los dos códigos del catálogo para el 2 % y el 7,5 % tienen el mismo texto. */
    @Test
    void elSieteYMedioFueraDeSuVigenciaNoSeConstruyeYNoLlevaCodigo() {
        assertThatThrownBy(() -> Registros.alta()
                        .desglose(Desglose.de(linea("01", CalificacionOperacion.S1, "7.5", "100.00", null, "7.50")))
                        .build())
                .isInstanceOf(ValorInvalidoException.class)
                .isNotInstanceOf(ReglaAeatIncumplidaException.class)
                .hasMessageContaining("01-10-2024");
    }

    // --- Anulación ---

    static Stream<Arguments> anulacionesQueLaAeatRechazaria() {
        return Stream.of(
                arguments("1224", "quién la genera sin generador", GeneradoPor.E, null),
                arguments("1224", "generador sin decir quién la genera", null, ASESORIA),
                arguments(
                        "1227", "el expedidor sin NIF", GeneradoPor.E, extranjero("FR", TipoIdentificacion.PASAPORTE)),
                arguments(
                        "1229",
                        "un tercero no censado",
                        GeneradoPor.T,
                        extranjero("ES", TipoIdentificacion.NO_CENSADO)),
                arguments(
                        "1232",
                        "un tercero de España con otro documento",
                        GeneradoPor.T,
                        extranjero("ES", TipoIdentificacion.OTRO_DOCUMENTO)),
                arguments(
                        "1230",
                        "el destinatario de España con otro documento",
                        GeneradoPor.D,
                        extranjero("ES", TipoIdentificacion.OTRO_DOCUMENTO)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("anulacionesQueLaAeatRechazaria")
    void unaAnulacionQueLaAeatRechazariaNoSeConstruye(
            String codigo, String caso, GeneradoPor generadoPor, PersonaFisicaJuridica generador) {
        assertThatThrownBy(() -> anulacion(generadoPor, generador))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo(codigo));
    }

    @Test
    void unaAnulacionSinGeneradorOConUnGeneradorBienIdentificadoSeConstruye() {
        assertThatCode(() -> anulacion(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> anulacion(GeneradoPor.E, ASESORIA)).doesNotThrowAnyException();
        PersonaFisicaJuridica noCensado = extranjero("ES", TipoIdentificacion.NO_CENSADO);
        assertThatCode(() -> anulacion(GeneradoPor.D, noCensado)).doesNotThrowAnyException();
    }

    // --- Sistema informático ---

    @Test
    void elIdentificadorDelSistemaSonDosMayusculasODigitos() {
        assertThat(sistema("A1", Registros.sistemaInformatico().productor()).idSistemaInformatico())
                .isEqualTo("A1");
        for (String id : List.of("1", "a1", "Ñ1")) {
            assertThatThrownBy(() -> sistema(id, Registros.sistemaInformatico().productor()))
                    .isInstanceOfSatisfying(
                            ReglaAeatIncumplidaException.class,
                            e -> assertThat(e.codigoAeat()).isEqualTo("1177"));
        }
    }

    @Test
    void elProductorNoEsNoCensadoNiDeEspanaSinPasaporte() {
        assertThatThrownBy(() -> sistema("01", extranjero("ES", TipoIdentificacion.NO_CENSADO)))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1221"));
        assertThatThrownBy(() -> sistema("01", extranjero("ES", TipoIdentificacion.OTRO_DOCUMENTO)))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1232"));
    }

    private static Arguments alta(String codigo, String caso, UnaryOperator<DatosRegistroAlta.Builder> cambio) {
        return arguments(codigo, caso, cambio);
    }

    private static UnaryOperator<DatosRegistroAlta.Builder> cambio(UnaryOperator<DatosRegistroAlta.Builder> cambio) {
        return cambio;
    }

    private static DatosRegistroAlta.Builder rectificativa(
            DatosRegistroAlta.Builder builder, TipoFactura tipo, ClaveTipoRectificativa clave) {
        return builder.tipoFactura(tipo)
                .tipoRectificativa(clave)
                .importeRectificacion(clave == ClaveTipoRectificativa.S ? RECTIFICADO : null);
    }

    private static DatosRegistroAlta.Builder simplificada(DatosRegistroAlta.Builder builder) {
        return builder.tipoFactura(TipoFactura.F2).destinatarios(List.of());
    }

    private static DatosRegistroAlta.Builder clave14(DatosRegistroAlta.Builder builder) {
        return builder.fechaOperacion(Registros.FECHA_EXPEDICION.plusMonths(1))
                .destinatarios(List.of(AYUNTAMIENTO))
                .desglose(Desglose.de(linea("14", CalificacionOperacion.S1, "10", "111.10", null, "12.35")));
    }

    private static Desglose clave10() {
        return Desglose.de(linea("10", CalificacionOperacion.N1, null, "111.10", null, null));
    }

    private static Desglose alDiez(String base, String cuota) {
        return Desglose.de(linea("01", CalificacionOperacion.S1, "10", base, null, cuota));
    }

    private static DetalleDesglose linea(
            String clave, CalificacionOperacion calificacion, String tipo, String base, String aCoste, String cuota) {
        return new DetalleDesglose(
                Impuesto.IVA,
                new ClaveRegimen(clave),
                calificacion,
                tipo == null ? null : Porcentaje.de(tipo),
                Importe.de(base),
                aCoste == null ? null : Importe.de(aCoste),
                cuota == null ? null : Importe.de(cuota),
                null,
                null);
    }

    private static DetalleDesglose sinImpuesto(DetalleDesglose linea) {
        return conImpuesto(null, linea);
    }

    private static DetalleDesglose soloIpsi() {
        return new DetalleDesglose(
                Impuesto.IPSI,
                null,
                CalificacionOperacion.S1,
                Porcentaje.de("10"),
                Importe.de("111.10"),
                null,
                Importe.de("12.35"),
                null,
                null);
    }

    private static DetalleDesglose conImpuesto(Impuesto impuesto, DetalleDesglose linea) {
        return new DetalleDesglose(
                impuesto,
                linea.claveRegimen(),
                linea.calificacion(),
                linea.tipoImpositivo(),
                linea.baseImponibleOimporteNoSujeto(),
                linea.baseImponibleACoste(),
                linea.cuotaRepercutida(),
                linea.tipoRecargoEquivalencia(),
                linea.cuotaRecargoEquivalencia());
    }

    private static IdFactura expedidaEl(LocalDate fecha) {
        return new IdFactura(Registros.EMISOR, "FA/1", fecha);
    }

    private static PersonaFisicaJuridica extranjero(String pais, TipoIdentificacion tipo) {
        String id = tipo == TipoIdentificacion.NO_CENSADO ? "12345678Z" : "12AB34567";
        return new PersonaFisicaJuridica("Persona", new IdOtro(pais, tipo, id));
    }

    private static DatosRegistroAnulacion anulacion(GeneradoPor generadoPor, PersonaFisicaJuridica generador) {
        return new DatosRegistroAnulacion(
                Registros.idFactura("FA/1"),
                null,
                false,
                false,
                generadoPor,
                generador,
                Registros.sistemaInformatico());
    }

    private static SistemaInformatico sistema(String id, PersonaFisicaJuridica productor) {
        return new SistemaInformatico(productor, "lacre", id, "0.0.1", "0001", true, false, false);
    }
}
