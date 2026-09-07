package dev.lacre.sif;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.sif.internal.CanonicalizadorAeat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncadenadorRegistrosTest {

    private static final ZoneOffset MADRID_INVIERNO = ZoneOffset.ofHours(1);
    private static final Instant CASO_1 = Instant.parse("2024-01-01T18:20:30Z");
    private static final Instant CASO_2 = Instant.parse("2024-01-01T18:20:35Z");

    private static final Huella HUELLA_CASO_1 = new Huella(
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60");
    private static final Huella HUELLA_CASO_2 = new Huella(
            "F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97");

    // --- Conformidad con los ejemplos oficiales de la AEAT ---

    @Test
    void reproduceLaHuellaDelPrimerRegistroDelEjemploOficial() {
        RegistroEncadenado registro = encadenador(CASO_1)
                .encadenar(datos("12345678/G33"), Optional.empty());

        assertThat(registro.huella()).isEqualTo(HUELLA_CASO_1);
    }

    @Test
    void reproduceLaHuellaDelSegundoRegistroDelEjemploOficial() {
        RegistroEncadenado registro = encadenador(CASO_2)
                .encadenar(datos("12345679/G34"), Optional.of(HUELLA_CASO_1));

        assertThat(registro.huella()).isEqualTo(HUELLA_CASO_2);
    }

    @Test
    void dosRegistrosSeguidosFormanLaCadenaDelDocumento() {
        RegistroEncadenado primero = encadenador(CASO_1)
                .encadenar(datos("12345678/G33"), Optional.empty());
        RegistroEncadenado segundo = encadenador(CASO_2)
                .encadenar(datos("12345679/G34"), Optional.of(primero.huella()));

        assertThat(primero.huella()).isEqualTo(HUELLA_CASO_1);
        assertThat(segundo.huella()).isEqualTo(HUELLA_CASO_2);
        assertThat(segundo.huellaAnterior()).contains(primero.huella());
    }

    // --- Sensibilidad de la huella ---

    enum CampoDeLaHuella {
        EMISOR, NUM_SERIE, FECHA_EXPEDICION, TIPO_FACTURA,
        CUOTA_TOTAL, IMPORTE_TOTAL, HUELLA_ANTERIOR, FECHA_HORA_HUSO
    }

    @ParameterizedTest
    @EnumSource(CampoDeLaHuella.class)
    void cambiarCualquieraDeLosOchoCamposCambiaLaHuella(CampoDeLaHuella campo) {
        assertThat(variando(campo).huella()).isNotEqualTo(base().huella());
    }

    @Test
    void losCamposQueNoEntranEnLaHuellaNoLaCambian() {
        DatosRegistroAlta otraDescripcion = Registros.alta()
                .descripcionOperacion("Otra cosa completamente distinta")
                .refExterna("REF-9999")
                .macrodato(true)
                .build();

        RegistroEncadenado registro = encadenador(CASO_1).encadenar(otraDescripcion, Optional.empty());

        assertThat(registro.huella()).isEqualTo(base().huella());
    }

    @Test
    void laZonaDelRelojCambiaLaHuellaAunqueElInstanteSeaElMismo() {
        RegistroEncadenado enMadrid = encadenador(CASO_1)
                .encadenar(datos("12345678/G33"), Optional.empty());
        RegistroEncadenado enUtc = new EncadenadorRegistros(
                Clock.fixed(CASO_1, ZoneOffset.UTC), new CanonicalizadorAeat())
                .encadenar(datos("12345678/G33"), Optional.empty());

        assertThat(enMadrid.huella()).isNotEqualTo(enUtc.huella());
    }

    // --- Contrato del servicio ---

    @Test
    void laCadenaEsReproducible() {
        assertThat(base()).isEqualTo(base());
    }

    @Test
    void fechaElRegistroConElRelojInyectadoYAlSegundo() {
        RegistroEncadenado registro = new EncadenadorRegistros(
                Clock.fixed(CASO_1.plusMillis(750), MADRID_INVIERNO), new CanonicalizadorAeat())
                .encadenar(datos("12345678/G33"), Optional.empty());

        assertThat(registro.fechaHoraHusoGenRegistro().toString()).isEqualTo("2024-01-01T19:20:30+01:00");
        assertThat(registro.huella()).isEqualTo(HUELLA_CASO_1);
    }

    @Test
    void elCanonicalizadorRecibeLosDatosElEnlaceYLaFechaHora() {
        List<Object[]> invocaciones = new ArrayList<>();
        Canonicalizador espia = (datos, huellaAnterior, fechaHora) -> {
            invocaciones.add(new Object[]{datos, huellaAnterior, fechaHora});
            return "irrelevante";
        };
        DatosRegistroAlta datos = datos("12345678/G33");

        new EncadenadorRegistros(Clock.fixed(CASO_1, MADRID_INVIERNO), espia)
                .encadenar(datos, Optional.empty());

        assertThat(invocaciones).singleElement().satisfies(argumentos -> {
            assertThat(argumentos[0]).isSameAs(datos);
            assertThat(argumentos[1]).isEqualTo(Optional.empty());
            assertThat(argumentos[2].toString()).isEqualTo("2024-01-01T19:20:30+01:00");
        });
    }

    @Test
    void exigeSusColaboradoresYSusArgumentos() {
        Canonicalizador canonicalizador = new CanonicalizadorAeat();
        assertThatThrownBy(() -> new EncadenadorRegistros(null, canonicalizador))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EncadenadorRegistros(Clock.systemUTC(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> encadenador(CASO_1).encadenar(null, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> encadenador(CASO_1).encadenar(datos("FA/1"), null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Apoyo ---

    private static EncadenadorRegistros encadenador(Instant momento) {
        return new EncadenadorRegistros(Clock.fixed(momento, MADRID_INVIERNO), new CanonicalizadorAeat());
    }

    private static DatosRegistroAlta datos(String numSerie) {
        return Registros.alta().idFactura(Registros.idFactura(numSerie)).build();
    }

    private static RegistroEncadenado base() {
        return encadenador(CASO_1).encadenar(datos("12345678/G33"), Optional.empty());
    }

    private static RegistroEncadenado variando(CampoDeLaHuella campo) {
        Nif emisor = new Nif("89890001K");
        String numSerie = "12345678/G33";
        LocalDate fecha = LocalDate.of(2024, 1, 1);
        TipoFactura tipo = TipoFactura.F1;
        Importe cuota = Importe.de("12.35");
        Importe total = Importe.de("123.45");
        Optional<Huella> anterior = Optional.empty();
        Instant momento = CASO_1;

        switch (campo) {
            case EMISOR -> emisor = new Nif("12345678Z");
            case NUM_SERIE -> numSerie = "12345678/G34";
            case FECHA_EXPEDICION -> fecha = LocalDate.of(2024, 1, 2);
            case TIPO_FACTURA -> tipo = TipoFactura.F2;
            case CUOTA_TOTAL -> cuota = Importe.de("12.36");
            case IMPORTE_TOTAL -> total = Importe.de("123.46");
            case HUELLA_ANTERIOR -> anterior = Optional.of(HUELLA_CASO_1);
            case FECHA_HORA_HUSO -> momento = CASO_1.plusSeconds(1);
        }

        DatosRegistroAlta datos = Registros.alta()
                .idFactura(new IdFactura(emisor, numSerie, fecha))
                .tipoFactura(tipo)
                .cuotaTotal(cuota)
                .importeTotal(total)
                .build();
        return encadenador(momento).encadenar(datos, anterior);
    }
}
