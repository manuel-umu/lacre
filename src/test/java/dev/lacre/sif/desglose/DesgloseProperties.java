package dev.lacre.sif.desglose;

import dev.lacre.shared.Importe;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cuadre del desglose con los totales es lo que la AEAT recalcula y contrasta. Estas
 * propiedades fijan que las sumas son consistentes para cualquier desglose válido, no solo
 * para los ejemplos escritos a mano.
 * <p>
 * Los importes generados se acotan a seis dígitos enteros para que la suma de doce líneas no
 * roce el límite de doce dígitos de {@link Importe} y falle por el generador y no por el
 * código.
 */
class DesgloseProperties {

    @Provide
    Arbitrary<Desglose> desgloses() {
        return detalles().list().ofMinSize(1).ofMaxSize(Desglose.MAXIMO_DETALLES).map(Desglose::new);
    }

    @Provide
    Arbitrary<DetalleDesglose> detalles() {
        return Combinators.combine(importes(), importes(), importes())
                .as((base, cuota, recargo) -> new DetalleDesglose(
                        Impuesto.IVA, new ClaveRegimen("01"), CalificacionOperacion.S1,
                        null, base, null, cuota, null, recargo));
    }

    private Arbitrary<Importe> importes() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-999999"), new BigDecimal("999999"))
                .ofScale(2)
                .map(Importe::new);
    }

    @Property
    void elTotalConImpuestosEsSiempreBasesMasCuotas(@ForAll("desgloses") Desglose desglose) {
        assertThat(desglose.totalConImpuestos())
                .isEqualTo(desglose.totalBases().sumar(desglose.totalCuotas()));
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
    void anadirUnaLineaSumaExactamenteSuAportacion(@ForAll("desgloses") Desglose desglose,
                                                   @ForAll("detalles") DetalleDesglose nueva) {
        List<DetalleDesglose> conUnaMas = new ArrayList<>(desglose.detalles());
        conUnaMas.add(nueva);
        Assume.that(conUnaMas.size() <= Desglose.MAXIMO_DETALLES);

        assertThat(new Desglose(conUnaMas).totalConImpuestos()).isEqualTo(
                desglose.totalConImpuestos()
                        .sumar(nueva.baseImponibleOimporteNoSujeto())
                        .sumar(nueva.cuotas()));
    }
}
