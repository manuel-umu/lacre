package dev.lacre.verifactu.desglose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Las sumas del desglose son consistentes para cualquier desglose válido. Los importes
 * generados se acotan a seis dígitos enteros para que la suma de doce líneas no roce el límite
 * de {@link Importe}.
 */
class DesgloseProperties {

    @Provide
    Arbitrary<Desglose> desgloses() {
        return detalles()
                .list()
                .ofMinSize(1)
                .ofMaxSize(Desglose.MAXIMO_DETALLES)
                .map(Desglose::new);
    }

    @Provide
    Arbitrary<DetalleDesglose> detalles() {
        return Combinators.combine(importes(), importes(), importes())
                .as((base, cuota, recargo) -> new DetalleDesglose(
                        Impuesto.IVA,
                        new ClaveRegimen("01"),
                        CalificacionOperacion.S1,
                        Porcentaje.de("21"),
                        base,
                        null,
                        cuota,
                        null,
                        recargo));
    }

    @Provide
    Arbitrary<Importe> importes() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-999999"), new BigDecimal("999999"))
                .ofScale(2)
                .map(Importe::new);
    }

    /**
     * Una línea S1 con cualquier tipo de IVA y una cuota a menos de diez euros de la de su base.
     * La cuota redondeada se aparta hasta medio céntimo del producto exacto con el que se compara.
     */
    @Property
    void unaCuotaDentroDelMargenDeSuBaseYSuTipoSiempreSeAdmite(
            @ForAll("importes") Importe base,
            @ForAll("tiposDeIva") String tipo,
            @ForAll @IntRange(min = -999, max = 999) int desvioEnCentimos) {
        Porcentaje porcentaje = Porcentaje.de(tipo);
        Importe cuota =
                porcentaje.aplicarA(base).sumar(new Importe(BigDecimal.valueOf(desvioEnCentimos, Importe.ESCALA)));
        Assume.that(base.valor().signum() * cuota.valor().signum() >= 0);

        DetalleDesglose linea = new DetalleDesglose(
                Impuesto.IVA,
                new ClaveRegimen("01"),
                CalificacionOperacion.S1,
                porcentaje,
                base,
                null,
                cuota,
                null,
                null);

        assertThatCode(linea::exigirCuotaCoherenteConLaBase).doesNotThrowAnyException();
    }

    @Provide
    Arbitrary<String> tiposDeIva() {
        return Arbitraries.of("0", "2", "4", "5", "7.5", "10", "21");
    }

    @Property
    void elTotalConImpuestosEsSiempreBasesMasCuotas(@ForAll("desgloses") Desglose desglose) {
        assertThat(desglose.totalConImpuestos()).isEqualTo(desglose.totalBases().sumar(desglose.totalCuotas()));
    }

    @Property
    void lasSumasNoDependenDelOrdenDeLasLineas(@ForAll("desgloses") Desglose desglose) {
        List<DetalleDesglose> alReves = new ArrayList<>(desglose.detalles());
        Collections.reverse(alReves);

        assertThat(new Desglose(alReves).totalConImpuestos()).isEqualTo(desglose.totalConImpuestos());
    }

    @Property
    void lasSumasMantienenLaEscalaDeDosDecimales(@ForAll("desgloses") Desglose desglose) {
        assertThat(desglose.totalBases().valor().scale()).isEqualTo(Importe.ESCALA);
        assertThat(desglose.totalCuotas().valor().scale()).isEqualTo(Importe.ESCALA);
        assertThat(desglose.totalConImpuestos().valor().scale()).isEqualTo(Importe.ESCALA);
    }

    @Property
    void anadirUnaLineaSumaExactamenteSuAportacion(
            @ForAll("desgloses") Desglose desglose, @ForAll("detalles") DetalleDesglose nueva) {
        List<DetalleDesglose> conUnaMas = new ArrayList<>(desglose.detalles());
        conUnaMas.add(nueva);
        Assume.that(conUnaMas.size() <= Desglose.MAXIMO_DETALLES);

        assertThat(new Desglose(conUnaMas).totalConImpuestos())
                .isEqualTo(desglose.totalConImpuestos()
                        .sumar(nueva.baseImponibleOimporteNoSujeto())
                        .sumar(nueva.cuotas()));
    }
}
