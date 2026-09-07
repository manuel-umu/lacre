package dev.lacre.sif.internal;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.sif.DatosRegistroAlta;
import dev.lacre.sif.IdFactura;
import dev.lacre.sif.PersonaFisicaJuridica;
import dev.lacre.sif.Registros;
import dev.lacre.sif.TipoFactura;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cadenas canónicas de los ejemplos del apartado 6 de «Detalle de las especificaciones
 * técnicas para generación de la huella o hash de los registros de facturación» (AEAT,
 * v0.1.2, 27/08/2024). Son literales del documento oficial: si un cambio los rompe, es el
 * cambio el que está mal.
 */
class CanonicalizadorAeatTest {

    private static final CanonicalizadorAeat CANONICALIZADOR = new CanonicalizadorAeat();

    private static final Huella HUELLA_CASO_1 = new Huella(
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60");

    @Test
    void caso1PrimerRegistroDeLaCadena() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datos("12345678/G33", "12.35", "123.45"),
                Optional.empty(),
                OffsetDateTime.parse("2024-01-01T19:20:30+01:00"));

        assertThat(cadena).isEqualTo("IDEmisorFactura=89890001K"
                + "&NumSerieFactura=12345678/G33"
                + "&FechaExpedicionFactura=01-01-2024"
                + "&TipoFactura=F1"
                + "&CuotaTotal=12.35"
                + "&ImporteTotal=123.45"
                + "&Huella="
                + "&FechaHoraHusoGenRegistro=2024-01-01T19:20:30+01:00");
    }

    @Test
    void caso2ConRegistroAnterior() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datos("12345679/G34", "12.35", "123.45"),
                Optional.of(HUELLA_CASO_1),
                OffsetDateTime.parse("2024-01-01T19:20:35+01:00"));

        assertThat(cadena).isEqualTo("IDEmisorFactura=89890001K"
                + "&NumSerieFactura=12345679/G34"
                + "&FechaExpedicionFactura=01-01-2024"
                + "&TipoFactura=F1"
                + "&CuotaTotal=12.35"
                + "&ImporteTotal=123.45"
                + "&Huella=3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60"
                + "&FechaHoraHusoGenRegistro=2024-01-01T19:20:35+01:00");
    }

    @Test
    void recortaLosEspaciosDeLosExtremosPeroNoLosInteriores() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datos("  12345678 / G33  ", "12.35", "123.45"),
                Optional.empty(),
                OffsetDateTime.parse("2024-01-01T19:20:30+01:00"));

        assertThat(cadena).contains("&NumSerieFactura=12345678 / G33&");
    }

    @Test
    void laFechaDeExpedicionVaEnFormatoDiaMesAnio() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datosConFecha(LocalDate.of(2024, 12, 3)),
                Optional.empty(),
                OffsetDateTime.parse("2024-01-01T19:20:30+01:00"));

        assertThat(cadena).contains("&FechaExpedicionFactura=03-12-2024&");
    }

    /**
     * {@code ISO_OFFSET_DATE_TIME} omitiría los segundos aquí y produciría
     * {@code 2024-01-01T19:20+01:00}, con una huella distinta a la que calcula la AEAT.
     */
    @Test
    void laFechaHoraConservaLosSegundosAunqueSeanCero() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datos("FA/1", "12.35", "123.45"),
                Optional.empty(),
                OffsetDateTime.parse("2024-01-01T19:20:00+01:00"));

        assertThat(cadena).endsWith("&FechaHoraHusoGenRegistro=2024-01-01T19:20:00+01:00");
    }

    @Test
    void losImportesVanConDosDecimalesYSinNotacionCientifica() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datos("FA/1", "0", "1000000.5"),
                Optional.empty(),
                OffsetDateTime.parse("2024-01-01T19:20:30+01:00"));

        assertThat(cadena).contains("&CuotaTotal=0.00&ImporteTotal=1000000.50&");
    }

    @Test
    void losCamposQueNoEntranEnLaHuellaNoAparecenEnLaCadena() {
        String cadena = CANONICALIZADOR.canonicalizar(
                datos("FA/1", "12.35", "123.45"),
                Optional.empty(),
                OffsetDateTime.parse("2024-01-01T19:20:30+01:00"));

        assertThat(cadena)
                .doesNotContain("DescripcionOperacion")
                .doesNotContain("Servicios de consultoría")
                .doesNotContain("Destinatario");
    }

    private static DatosRegistroAlta datos(String numSerie, String cuota, String importe) {
        return Registros.alta()
                .idFactura(Registros.idFactura(numSerie))
                .cuotaTotal(Importe.de(cuota))
                .importeTotal(Importe.de(importe))
                .build();
    }

    private static DatosRegistroAlta datosConFecha(LocalDate fecha) {
        return Registros.alta()
                .idFactura(new IdFactura(Registros.EMISOR, "FA/1", fecha))
                .build();
    }
}
