package dev.lacre.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ImporteProperties {

    @Provide
    Arbitrary<Importe> importes() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000"), new BigDecimal("1000000"))
                .ofScale(4)
                .map(Importe::new);
    }

    @Property
    void laEscalaSiempreEsDos(@ForAll("importes") Importe a, @ForAll("importes") Importe b) {
        assertThat(a.sumar(b).valor().scale()).isEqualTo(Importe.ESCALA);
        assertThat(a.restar(b).valor().scale()).isEqualTo(Importe.ESCALA);
        assertThat(a.negado().valor().scale()).isEqualTo(Importe.ESCALA);
    }

    @Property
    void laSumaEsConmutativa(@ForAll("importes") Importe a, @ForAll("importes") Importe b) {
        assertThat(a.sumar(b)).isEqualTo(b.sumar(a));
    }

    @Property
    void laSumaEsAsociativa(
            @ForAll("importes") Importe a, @ForAll("importes") Importe b, @ForAll("importes") Importe c) {
        assertThat(a.sumar(b).sumar(c)).isEqualTo(a.sumar(b.sumar(c)));
    }

    @Property
    void restarDeshaceSumar(@ForAll("importes") Importe a, @ForAll("importes") Importe b) {
        assertThat(a.sumar(b).restar(b)).isEqualTo(a);
    }
}
