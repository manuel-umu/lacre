package dev.lacre.verifactu.desglose;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesgloseTest {

    @Test
    void sumaLasBasesLasCuotasYElTotal() {
        Desglose desglose = Desglose.de(
                linea("100.00", "21.00", null),
                linea("50.00", "10.50", "2.60"));

        assertThat(desglose.totalBases()).isEqualTo(Importe.de("150.00"));
        assertThat(desglose.totalCuotas()).isEqualTo(Importe.de("34.10"));
        assertThat(desglose.totalConImpuestos()).isEqualTo(Importe.de("184.10"));
    }

    @Test
    void unaCuotaAusenteCuentaComoCero() {
        Desglose desglose = Desglose.de(new DetalleDesglose(Impuesto.IVA, new ClaveRegimen("01"),
                CalificacionOperacion.N1, null, Importe.de("100.00"), null, null, null, null));

        assertThat(desglose.totalCuotas()).isEqualTo(Importe.CERO);
        assertThat(desglose.totalConImpuestos()).isEqualTo(Importe.de("100.00"));
    }

    @Test
    void unaLineaExentaEsUnDesgloseValido() {
        DetalleDesglose exenta = new DetalleDesglose(Impuesto.IVA, new ClaveRegimen("01"),
                OperacionExenta.E1, null, Importe.de("100.00"), null, null, null, null);

        assertThat(Desglose.de(exenta).totalConImpuestos()).isEqualTo(Importe.de("100.00"));
    }

    @Test
    void aceptaEntreUnaYDoceLineas() {
        assertThat(Desglose.de(linea("1.00", "0.21", null)).detalles()).hasSize(1);
        assertThat(desgloseCon(12).detalles()).hasSize(12);
    }

    @Test
    void rechazaCeroLineasYMasDeDoce() {
        assertThatThrownBy(() -> new Desglose(List.of()))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("al menos una");
        assertThatThrownBy(() -> desgloseCon(13))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("12");
    }

    @Test
    void rechazaListaNulaOConNulos() {
        assertThatThrownBy(() -> new Desglose(null)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new Desglose(Collections.singletonList(null)))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void laListaDeDetallesQuedaInmutable() {
        List<DetalleDesglose> mutable = new ArrayList<>(List.of(linea("100.00", "21.00", null)));

        Desglose desglose = new Desglose(mutable);
        mutable.clear();

        assertThat(desglose.detalles()).hasSize(1);
        assertThatThrownBy(() -> desglose.detalles().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void todaLineaDebeEstarCalificadaOExenta() {
        assertThatThrownBy(() -> new DetalleDesglose(Impuesto.IVA, null, null,
                Porcentaje.de("21"), Importe.de("100.00"), null, null, null, null))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("calificada o exenta");
    }

    @Test
    void laBaseImponibleEsObligatoriaEnCadaLinea() {
        assertThatThrownBy(() -> new DetalleDesglose(Impuesto.IVA, null, CalificacionOperacion.S1,
                Porcentaje.de("21"), null, null, null, null, null))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("base imponible");
    }

    private static Desglose desgloseCon(int lineas) {
        return new Desglose(IntStream.range(0, lineas)
                .mapToObj(i -> linea("1.00", "0.21", null))
                .toList());
    }

    private static DetalleDesglose linea(String base, String cuota, String recargo) {
        return new DetalleDesglose(
                Impuesto.IVA,
                new ClaveRegimen("01"),
                CalificacionOperacion.S1,
                Porcentaje.de("21"),
                Importe.de(base),
                null,
                cuota == null ? null : Importe.de(cuota),
                recargo == null ? null : Porcentaje.de("5.2"),
                recargo == null ? null : Importe.de(recargo));
    }
}
