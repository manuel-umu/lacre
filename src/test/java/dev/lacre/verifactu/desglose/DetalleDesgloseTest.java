package dev.lacre.verifactu.desglose;

import static dev.lacre.verifactu.desglose.CalificacionOperacion.N1;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.N2;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.S1;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.S2;
import static dev.lacre.verifactu.desglose.Impuesto.IGIC;
import static dev.lacre.verifactu.desglose.Impuesto.IPSI;
import static dev.lacre.verifactu.desglose.Impuesto.IVA;
import static dev.lacre.verifactu.desglose.Impuesto.OTROS;
import static dev.lacre.verifactu.desglose.OperacionExenta.E1;
import static dev.lacre.verifactu.desglose.OperacionExenta.E2;
import static dev.lacre.verifactu.desglose.OperacionExenta.E7;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.shared.ValorInvalidoException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DetalleDesgloseTest {

    static Stream<Arguments> lineasQueLaAeatRechazaria() {
        return Stream.of(
                arguments(
                        "1245",
                        "IVA sin clave de régimen",
                        new Linea(IVA, null, S1).tipo("21").cuota("21")),
                arguments(
                        "1245",
                        "IGIC sin clave de régimen",
                        new Linea(IGIC, null, S1).tipo("7").cuota("7")),
                arguments(
                        "1246",
                        "clave 21 con IVA",
                        new Linea(IVA, "21", S1).tipo("21").cuota("21")),
                arguments("1199", "clave 01 exenta por E2", new Linea(IVA, "01", E2)),
                arguments(
                        "1200",
                        "clave 03 calificada S2",
                        new Linea(IVA, "03", S2).tipo("0").cuota("0")),
                arguments(
                        "1201",
                        "clave 04 calificada S1",
                        new Linea(IVA, "04", S1).tipo("21").cuota("21")),
                arguments(
                        "1202",
                        "clave 06 sin base a coste",
                        new Linea(IVA, "06", S1).tipo("21").cuota("21")),
                arguments("1203", "clave 07 calificada N1", new Linea(IVA, "07", N1)),
                arguments(
                        "1252",
                        "clave 08 calificada S1",
                        new Linea(IVA, "08", S1).tipo("21").cuota("21")),
                arguments("1205", "clave 10 calificada N2", new Linea(IVA, "10", N2)),
                arguments("1182", "exención E7 con IVA", new Linea(IVA, "01", E7)),
                arguments(
                        "1238",
                        "exenta con tipo y cuota",
                        new Linea(IVA, "01", E1).tipo("21").cuota("21")),
                arguments("1237", "no sujeta al IVA con tipo", new Linea(IVA, "01", N1).tipo("21")),
                arguments("1207", "cuota distinta de cero fuera de S1", new Linea(IGIC, "01", N1).cuota("7")),
                arguments(
                        "1198",
                        "S2 con tipo distinto de cero",
                        new Linea(IVA, "01", S2).tipo("21").cuota("0")),
                arguments("1198", "S2 sin cuota", new Linea(IVA, "01", S2).tipo("0")),
                arguments("1208", "S1 sin tipo impositivo", new Linea(IVA, "01", S1).cuota("21")),
                arguments(
                        "1209",
                        "S1 con base a coste y sin cuota",
                        new Linea(IPSI, "01", S1).tipo("10").aCoste("100")),
                arguments(
                        "1124",
                        "tipo de IVA inexistente",
                        new Linea(IVA, "01", S1).tipo("3").cuota("3")),
                arguments(
                        "1127",
                        "tipo de recargo inexistente",
                        new Linea(IVA, "01", S1).tipo("21").cuota("21").recargo("3", "3")),
                arguments(
                        "1162",
                        "recargo que no es del 21 %",
                        new Linea(IVA, "01", S1).tipo("21").cuota("21").recargo("1.4", "1.4")),
                arguments(
                        "1163",
                        "recargo que no es del 10 %",
                        new Linea(IVA, "01", S1).tipo("10").cuota("10").recargo("5.2", "5.2")),
                arguments(
                        "1169",
                        "recargo que no es del 7,5 %",
                        new Linea(IVA, "01", S1).tipo("7.5").cuota("7.5").recargo("0.5", "0.5")),
                arguments(
                        "1160",
                        "recargo que no es del 5 %",
                        new Linea(IVA, "01", S1).tipo("5").cuota("5").recargo("1.4", "1.4")),
                arguments(
                        "1164",
                        "recargo que no es del 4 %",
                        new Linea(IVA, "01", S1).tipo("4").cuota("4").recargo("1.4", "1.4")),
                arguments(
                        "1166",
                        "recargo que no es del 2 %",
                        new Linea(IVA, "01", S1).tipo("2").cuota("2").recargo("0.5", "0.5")),
                arguments(
                        "1206",
                        "clave 11 a un tipo distinto del 21 %",
                        new Linea(IVA, "11", S1).tipo("10").cuota("10")));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("lineasQueLaAeatRechazaria")
    void unaLineaQueLaAeatRechazariaNoSeConstruye(String codigo, String caso, Linea linea) {
        assertThatThrownBy(linea::construir)
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo(codigo));
    }

    static Stream<Arguments> cuotasQueNoSalenDeLaBase() {
        return Stream.of(
                arguments(
                        "1142",
                        "cuota fuera del margen",
                        new Linea(IVA, "01", S1).tipo("21").base("111.10").cuota("12.35")),
                arguments(
                        "1142",
                        "diez euros y un céntimo por encima",
                        new Linea(IVA, "01", S1).tipo("21").base("100").cuota("31.01")),
                arguments(
                        "1143",
                        "cuota de signo contrario a la base",
                        new Linea(IVA, "01", S1).tipo("21").base("100").cuota("-5")),
                arguments(
                        "1144",
                        "cuota fuera del margen de la base a coste",
                        new Linea(IPSI, "01", S1)
                                .tipo("10")
                                .base("1000")
                                .aCoste("100")
                                .cuota("50")),
                arguments(
                        "1140",
                        "cuota de signo contrario a la base a coste",
                        new Linea(IPSI, "01", S1)
                                .tipo("10")
                                .base("1000")
                                .aCoste("100")
                                .cuota("-5")));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("cuotasQueNoSalenDeLaBase")
    void unaCuotaQueNoSaleDeLaBaseYElTipoSeDenuncia(String codigo, String caso, Linea linea) {
        DetalleDesglose detalle = linea.construir();

        assertThatThrownBy(detalle::exigirCuotaCoherenteConLaBase)
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo(codigo));
    }

    static Stream<Arguments> lineasValidas() {
        return Stream.of(
                arguments(
                        "cuota exacta",
                        new Linea(IVA, "01", S1).tipo("21").base("111.10").cuota("23.33")),
                arguments(
                        "diez euros por encima",
                        new Linea(IVA, "01", S1).tipo("21").base("100").cuota("31")),
                arguments(
                        "diez euros por debajo",
                        new Linea(IVA, "01", S1).tipo("21").base("100").cuota("11")),
                arguments(
                        "base y cuota negativas",
                        new Linea(IVA, "01", S1).tipo("21").base("-100").cuota("-21")),
                arguments(
                        "tipo cero y cuota cero",
                        new Linea(IVA, "01", S1).tipo("0").cuota("0")),
                arguments(
                        "impuesto sin informar",
                        new Linea(null, "01", S1).tipo("21").cuota("21")),
                arguments("tipo de IGIC", new Linea(IGIC, "01", S1).tipo("7").cuota("7")),
                arguments(
                        "IPSI sin clave de régimen",
                        new Linea(IPSI, null, S1).tipo("4").cuota("4")),
                arguments(
                        "otros impuestos con base a coste",
                        new Linea(OTROS, null, S1).tipo("10").aCoste("100").cuota("10")),
                arguments(
                        "clave 06 con base a coste",
                        new Linea(IVA, "06", S1).tipo("21").aCoste("100").cuota("21")),
                arguments("exenta sin impuestos", new Linea(IVA, "01", E1)),
                arguments("exención E7 con IGIC", new Linea(IGIC, "01", E7)),
                arguments(
                        "inversión del sujeto pasivo a cero",
                        new Linea(IVA, "01", S2).tipo("0").cuota("0")),
                arguments("no sujeta", new Linea(IVA, "08", N2)),
                arguments(
                        "recargo del 21 %",
                        new Linea(IVA, "01", S1).tipo("21").cuota("21").recargo("5.2", "5.2")),
                arguments("clave 03 exenta", new Linea(IVA, "03", E1)),
                arguments(
                        "clave 11 al 21 %", new Linea(IVA, "11", S1).tipo("21").cuota("21")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lineasValidas")
    void unaLineaQueLaAeatAceptaSeConstruyeYSuCuotaCuadra(String caso, Linea linea) {
        assertThatCode(() -> linea.construir().exigirCuotaCoherenteConLaBase()).doesNotThrowAnyException();
    }

    /** Documento y catálogo de errores no coinciden en qué recargo admite el tipo cero. */
    @Test
    void elTipoCeroNoRestringeElRecargo() {
        Linea linea = new Linea(IVA, "01", S1).tipo("0").cuota("0").recargo("0.26", "0.26");

        assertThatCode(linea::construir).doesNotThrowAnyException();
    }

    @Test
    void unaClaveDeRegimenConOtrosImpuestosNoSeConstruye() {
        assertThatThrownBy(new Linea(OTROS, "01", S1).tipo("10").cuota("10")::construir)
                .isInstanceOf(ValorInvalidoException.class)
                .isNotInstanceOf(ReglaAeatIncumplidaException.class)
                .hasMessageContaining("IVA, IPSI o IGIC");
    }

    @Test
    void laBaseACosteFueraDeLaClave06YDeIpsiUOtrosNoSeConstruye() {
        assertThatThrownBy(new Linea(IVA, "01", S1).tipo("21").aCoste("100").cuota("21")::construir)
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("base imponible a coste");
    }

    @Test
    void unaExportacionSoloPuedeSerExenta() {
        assertThatThrownBy(new Linea(IVA, "02", S1).tipo("21").cuota("21")::construir)
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("exenta");
        assertThatCode(new Linea(IVA, "02", E2)::construir).doesNotThrowAnyException();
    }

    @Test
    void conIgicYClave20LaOperacionEsN2() {
        assertThatThrownBy(new Linea(IGIC, "20", S1).tipo("7").cuota("7")::construir)
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("N2");
    }

    @Test
    void elMensajeDeUnaCuotaQueNoCuadraDaLosValores() {
        DetalleDesglose detalle = new Linea(IVA, "01", S1)
                .tipo("21")
                .base("111.10")
                .cuota("12.35")
                .construir();

        assertThatThrownBy(detalle::exigirCuotaCoherenteConLaBase)
                .hasMessageContaining("12.35")
                .hasMessageContaining("111.10")
                .hasMessageContaining("21 %");
    }

    /** Línea de desglose por partes; los importes van en texto y nulo es no informado. */
    record Linea(
            Impuesto impuesto,
            String clave,
            Calificacion calificacion,
            String tipo,
            String base,
            String aCoste,
            String cuota,
            String tipoRecargo,
            String cuotaRecargo) {

        Linea(Impuesto impuesto, String clave, Calificacion calificacion) {
            this(impuesto, clave, calificacion, null, "100", null, null, null, null);
        }

        Linea tipo(String valor) {
            return new Linea(impuesto, clave, calificacion, valor, base, aCoste, cuota, tipoRecargo, cuotaRecargo);
        }

        Linea base(String valor) {
            return new Linea(impuesto, clave, calificacion, tipo, valor, aCoste, cuota, tipoRecargo, cuotaRecargo);
        }

        Linea aCoste(String valor) {
            return new Linea(impuesto, clave, calificacion, tipo, base, valor, cuota, tipoRecargo, cuotaRecargo);
        }

        Linea cuota(String valor) {
            return new Linea(impuesto, clave, calificacion, tipo, base, aCoste, valor, tipoRecargo, cuotaRecargo);
        }

        Linea recargo(String tipoDeRecargo, String cuotaDeRecargo) {
            return new Linea(impuesto, clave, calificacion, tipo, base, aCoste, cuota, tipoDeRecargo, cuotaDeRecargo);
        }

        DetalleDesglose construir() {
            return new DetalleDesglose(
                    impuesto,
                    clave == null ? null : new ClaveRegimen(clave),
                    calificacion,
                    tipo == null ? null : Porcentaje.de(tipo),
                    Importe.de(base),
                    aCoste == null ? null : Importe.de(aCoste),
                    cuota == null ? null : Importe.de(cuota),
                    tipoRecargo == null ? null : Porcentaje.de(tipoRecargo),
                    cuotaRecargo == null ? null : Importe.de(cuotaRecargo));
        }
    }
}
