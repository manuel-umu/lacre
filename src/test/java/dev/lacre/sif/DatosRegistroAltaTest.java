package dev.lacre.sif;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatosRegistroAltaTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 1, 15);

    @Test
    void unaFacturaSimplificadaNoLlevaDestinatarios() {
        assertThat(datos(List.of()).destinatarios()).isEmpty();
    }

    @Test
    void laListaDeDestinatariosQuedaInmutable() {
        List<DatosRegistroAlta.Destinatario> mutable = new ArrayList<>();
        mutable.add(new DatosRegistroAlta.Destinatario(new Nif("12345678Z"), "Cliente"));

        DatosRegistroAlta registro = datos(mutable);
        mutable.clear();

        assertThat(registro.destinatarios()).hasSize(1);
        assertThatThrownBy(() -> registro.destinatarios().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void exigeLosCamposObligatorios() {
        assertThatThrownBy(() -> new DatosRegistroAlta(null, "FA/1", FECHA, "F1", "d",
                List.of(), Importe.CERO, Importe.CERO)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new DatosRegistroAlta(new Nif("12345678Z"), "  ", FECHA, "F1", "d",
                List.of(), Importe.CERO, Importe.CERO)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new DatosRegistroAlta(new Nif("12345678Z"), "FA/1", null, "F1", "d",
                List.of(), Importe.CERO, Importe.CERO)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new DatosRegistroAlta(new Nif("12345678Z"), "FA/1", FECHA, "F1", "",
                List.of(), Importe.CERO, Importe.CERO)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new DatosRegistroAlta(new Nif("12345678Z"), "FA/1", FECHA, "F1", "d",
                List.of(), null, Importe.CERO)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void elDestinatarioExigeNifYNombre() {
        assertThatThrownBy(() -> new DatosRegistroAlta.Destinatario(null, "Cliente"))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new DatosRegistroAlta.Destinatario(new Nif("12345678Z"), " "))
                .isInstanceOf(ValorInvalidoException.class);
    }

    private static DatosRegistroAlta datos(List<DatosRegistroAlta.Destinatario> destinatarios) {
        return new DatosRegistroAlta(new Nif("A28015865"), "FA/2026/0001", FECHA, "F1",
                "Servicios de consultoría", destinatarios, Importe.de("21.00"), Importe.de("121.00"));
    }
}
