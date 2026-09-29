package dev.lacre.verifactu.desglose;

import static dev.lacre.shared.ReglaAeatIncumplidaException.exigir;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.N1;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.N2;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.S1;
import static dev.lacre.verifactu.desglose.CalificacionOperacion.S2;
import static dev.lacre.verifactu.desglose.OperacionExenta.E2;
import static dev.lacre.verifactu.desglose.OperacionExenta.E3;
import static dev.lacre.verifactu.desglose.OperacionExenta.E4;
import static dev.lacre.verifactu.desglose.OperacionExenta.E5;
import static dev.lacre.verifactu.desglose.OperacionExenta.E7;
import static dev.lacre.verifactu.desglose.OperacionExenta.E8;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ValorInvalidoException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Línea del desglose de impuestos, {@code DetalleType} del XSD. Solo {@link #calificacion()} y
 * {@link #baseImponibleOimporteNoSujeto()} son obligatorios; el resto admite nulo. Un impuesto
 * nulo es IVA. Al construirse aplica las validaciones de la AEAT que dependen solo de la línea.
 */
public record DetalleDesglose(
        Impuesto impuesto,
        ClaveRegimen claveRegimen,
        Calificacion calificacion,
        Porcentaje tipoImpositivo,
        Importe baseImponibleOimporteNoSujeto,
        Importe baseImponibleACoste,
        Importe cuotaRepercutida,
        Porcentaje tipoRecargoEquivalencia,
        Importe cuotaRecargoEquivalencia) {

    /** Margen que admite la AEAT entre la cuota repercutida y la que sale de la base y el tipo. */
    public static final Importe MARGEN_CUOTA = Importe.de("10.00");

    private static final Set<Porcentaje> TIPOS_IVA = porcentajes("0", "2", "4", "5", "7.5", "10", "21");

    private static final Set<Porcentaje> TIPOS_RECARGO_IVA =
            porcentajes("0", "0.26", "0.5", "0.62", "1", "1.4", "1.75", "5.2");

    private static final Set<Calificacion> PROHIBIDAS_EN_CRITERIO_DE_CAJA = Set.of(S2, N1, N2, E2, E3, E4, E5);

    /** Recargos de equivalencia que admite cada tipo de IVA, con el código de su validación. */
    private static final Map<Porcentaje, RecargosAdmitidos> RECARGOS_POR_TIPO = Map.of(
            Porcentaje.de("21"), new RecargosAdmitidos("1162", porcentajes("5.2", "1.75")),
            Porcentaje.de("10"), new RecargosAdmitidos("1163", porcentajes("1.4")),
            Porcentaje.de("7.5"), new RecargosAdmitidos("1169", porcentajes("1")),
            Porcentaje.de("5"), new RecargosAdmitidos("1160", porcentajes("0.5", "0.62")),
            Porcentaje.de("4"), new RecargosAdmitidos("1164", porcentajes("0.5")),
            Porcentaje.de("2"), new RecargosAdmitidos("1166", porcentajes("0.26")));

    public DetalleDesglose {
        if (calificacion == null) {
            throw new ValorInvalidoException("Toda línea de desglose debe estar calificada o exenta");
        }
        if (baseImponibleOimporteNoSujeto == null) {
            throw new ValorInvalidoException(
                    "La base imponible o importe no sujeto es obligatoria en cada línea de desglose");
        }

        Impuesto efectivo = impuesto == null ? Impuesto.IVA : impuesto;
        boolean iva = efectivo == Impuesto.IVA;
        boolean ivaOIgic = iva || efectivo == Impuesto.IGIC;
        String clave = claveRegimen == null ? "" : claveRegimen.codigo();
        boolean exenta = calificacion instanceof OperacionExenta;
        boolean conImpuestos = tipoImpositivo != null
                || cuotaRepercutida != null
                || tipoRecargoEquivalencia != null
                || cuotaRecargoEquivalencia != null;

        if (claveRegimen != null && efectivo == Impuesto.OTROS) {
            throw new ValorInvalidoException(
                    "La clave de régimen solo se informa con IVA, IPSI o IGIC, y el impuesto es " + efectivo.codigo());
        }
        exigir(claveRegimen != null || !ivaOIgic, "1245", "Con IVA o IGIC la clave de régimen es obligatoria");
        exigir(!(iva && clave.equals("21")), "1246", "La clave de régimen 21 no está en la lista L8A, la del IVA");
        if (baseImponibleACoste != null
                && !clave.equals("06")
                && efectivo != Impuesto.IPSI
                && efectivo != Impuesto.OTROS) {
            throw new ValorInvalidoException("La base imponible a coste solo se informa con clave "
                    + "de régimen 06, con IPSI o con otros impuestos");
        }

        if (ivaOIgic) {
            exigir(
                    !clave.equals("01") || (calificacion != E2 && calificacion != E3),
                    "1199",
                    "Con clave de régimen 01 la exención no puede ser E2 ni E3");
            exigir(
                    !clave.equals("03") || exenta || calificacion == S1,
                    "1200",
                    "Con clave de régimen 03 la operación solo puede calificarse S1, y es " + calificacion.codigo());
            exigir(
                    !clave.equals("04") || exenta || calificacion == S2,
                    "1201",
                    "Con clave de régimen 04 la operación solo puede ser S2 o exenta, y es " + calificacion.codigo());
            exigir(
                    !clave.equals("06") || baseImponibleACoste != null,
                    "1202",
                    "Con clave de régimen 06 la base imponible a coste es obligatoria");
            exigir(
                    !clave.equals("07") || !PROHIBIDAS_EN_CRITERIO_DE_CAJA.contains(calificacion),
                    "1203",
                    "Con clave de régimen 07 la operación no puede ser S2, N1, N2, E2, " + "E3, E4 ni E5, y es "
                            + calificacion.codigo());
            exigir(
                    !clave.equals("08") || calificacion == N2,
                    "1252",
                    "Con clave de régimen 08 la operación tiene que calificarse N2, y es " + calificacion.codigo());
            exigir(
                    !clave.equals("10") || calificacion == N1,
                    "1205",
                    "Con clave de régimen 10 la operación tiene que calificarse N1, y es " + calificacion.codigo());
        }
        if (ivaOIgic && clave.equals("02") && !exenta) {
            throw new ValorInvalidoException("Con clave de régimen 02, exportación, la "
                    + "operación solo puede ser exenta, y es " + calificacion.codigo());
        }
        if (efectivo == Impuesto.IGIC && clave.equals("20") && calificacion != N2) {
            throw new ValorInvalidoException("Con IGIC y clave de régimen 20 la operación tiene "
                    + "que calificarse N2, y es " + calificacion.codigo());
        }
        exigir(
                !(iva && (calificacion == E7 || calificacion == E8)),
                "1182",
                "Las exenciones E7 y E8 solo existen para el IGIC");

        exigir(
                !exenta || !conImpuestos,
                "1238",
                "Una operación exenta no lleva tipo impositivo, " + "cuota repercutida ni recargo de equivalencia");
        exigir(
                !(iva && (calificacion == N1 || calificacion == N2)) || !conImpuestos,
                "1237",
                "Una operación no sujeta al IVA no lleva tipo impositivo, cuota repercutida ni "
                        + "recargo de equivalencia");
        exigir(
                cuotaRepercutida == null || cuotaRepercutida.esCero() || calificacion == S1,
                "1207",
                "La cuota repercutida solo puede ser distinta de cero en una operación S1, y " + "esta es "
                        + calificacion.codigo());

        if (calificacion == S2) {
            exigir(
                    tipoImpositivo != null
                            && tipoImpositivo.equals(Porcentaje.CERO)
                            && cuotaRepercutida != null
                            && cuotaRepercutida.esCero(),
                    "1198",
                    "Con inversión del sujeto pasivo (S2) el tipo impositivo y la cuota "
                            + "repercutida se informan, y a cero");
        }
        if (calificacion == S1) {
            exigir(
                    tipoImpositivo != null && cuotaRepercutida != null,
                    baseImponibleACoste == null ? "1208" : "1209",
                    "En una operación S1 el tipo impositivo y la cuota repercutida son " + "obligatorios");
            if (iva) {
                exigirTiposDeIva(tipoImpositivo, tipoRecargoEquivalencia);
            }
        }
        exigir(
                !(iva && clave.equals("11") && tipoImpositivo != null && !tipoImpositivo.equals(Porcentaje.de("21"))),
                "1206",
                "Con clave de régimen 11 el tipo impositivo ha de ser el 21 %");
    }

    /** Cuota repercutida más recargo de equivalencia, tratando la ausencia como cero. */
    public Importe cuotas() {
        return oCero(cuotaRepercutida).sumar(oCero(cuotaRecargoEquivalencia));
    }

    /**
     * En una operación S1, la cuota repercutida tiene el signo de la base y es base × tipo / 100
     * con {@link #MARGEN_CUOTA} de margen. La base es la de a coste cuando se informa.
     */
    public void exigirCuotaCoherenteConLaBase() {
        if (calificacion != S1) {
            return;
        }
        boolean aCoste = baseImponibleACoste != null;
        Importe base = aCoste ? baseImponibleACoste : baseImponibleOimporteNoSujeto;
        String nombreBase = aCoste ? "la base imponible a coste" : "la base imponible";

        // Una cuota cero, la del tipo 0 %, no contradice el signo de ninguna base.
        exigir(
                base.valor().signum() * cuotaRepercutida.valor().signum() >= 0,
                aCoste ? "1140" : "1143",
                "La cuota repercutida " + cuotaRepercutida.valor().toPlainString() + " y "
                        + nombreBase + " " + base.valor().toPlainString()
                        + " deben tener el mismo signo");

        BigDecimal esperada = base.valor().multiply(tipoImpositivo.valor()).movePointLeft(2);
        BigDecimal desvio = cuotaRepercutida.valor().subtract(esperada).abs();
        exigir(
                desvio.compareTo(MARGEN_CUOTA.valor()) <= 0,
                aCoste ? "1144" : "1142",
                "La cuota repercutida " + cuotaRepercutida.valor().toPlainString() + " no es "
                        + nombreBase + " por el tipo impositivo ("
                        + base.valor().toPlainString()
                        + " al " + tipoImpositivo.valor().stripTrailingZeros().toPlainString()
                        + " %) con un margen de " + MARGEN_CUOTA.valor().toPlainString() + " €");
    }

    private static void exigirTiposDeIva(Porcentaje tipo, Porcentaje recargo) {
        exigir(
                TIPOS_IVA.contains(tipo),
                "1124",
                "El tipo impositivo "
                        + tipo.valor().stripTrailingZeros().toPlainString()
                        + " % no es uno de los del IVA: 0, 2, 4, 5, 7,5, 10 o 21");
        if (recargo == null) {
            return;
        }
        exigir(
                TIPOS_RECARGO_IVA.contains(recargo),
                "1127",
                "El tipo de recargo de equivalencia "
                        + recargo.valor().stripTrailingZeros().toPlainString()
                        + " % no es uno de los admitidos: 0, 0,26, 0,5, 0,62, 1, 1,4, 1,75 o 5,2");
        RecargosAdmitidos admitidos = RECARGOS_POR_TIPO.get(tipo);
        if (admitidos != null) {
            exigir(
                    admitidos.recargos().contains(recargo),
                    admitidos.codigoAeat(),
                    "El tipo de recargo de equivalencia "
                            + recargo.valor().stripTrailingZeros().toPlainString()
                            + " % no corresponde al tipo impositivo del "
                            + tipo.valor().stripTrailingZeros().toPlainString() + " %");
        }
    }

    private static Set<Porcentaje> porcentajes(String... valores) {
        return Stream.of(valores).map(Porcentaje::de).collect(Collectors.toUnmodifiableSet());
    }

    private static Importe oCero(Importe importe) {
        return importe == null ? Importe.CERO : importe;
    }

    private record RecargosAdmitidos(String codigoAeat, Set<Porcentaje> recargos) {}
}
