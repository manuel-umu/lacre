package dev.lacre.verifactu.registro;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El ejemplo oficial de anulación de extremo a extremo, y las reglas del registro de
 * anulación.
 */
class DatosRegistroAnulacionTest {

    private static final Huella HUELLA_CASO_2 = new Huella(
            "F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97");
    private static final Huella HUELLA_CASO_3 = new Huella(
            "177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68");

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final EncadenadorRegistros ENCADENADOR = new EncadenadorRegistros(
            Clock.fixed(Instant.parse("2024-01-01T18:20:40Z"), ZoneOffset.UTC),
            new CanonicalizadorAeat());

    @Test
    void reproduceLaHuellaDelEjemploOficialDeAnulacion() {
        RegistroEncadenado registro = ENCADENADOR.encadenar(
                Registros.anulacion(),
                Optional.of(new RegistroAnterior(Registros.idFactura("12345679/G34"), HUELLA_CASO_2)), MADRID);

        assertThat(registro.huella()).isEqualTo(HUELLA_CASO_3);
    }

    @Test
    void unaAnulacionEncadenaConElAltaAnterior() {
        RegistroEncadenado anulacion = ENCADENADOR.encadenar(
                Registros.anulacion(),
                Optional.of(new RegistroAnterior(Registros.idFactura("12345679/G34"), HUELLA_CASO_2)), MADRID);

        assertThat(anulacion.datos().tipo()).isEqualTo(TipoRegistro.ANULACION);
        assertThat(anulacion.registroAnterior()).map(RegistroAnterior::huella).contains(HUELLA_CASO_2);
    }

    @Test
    void laCadenaNoDistingueElTipoDeRegistro() {
        RegistroEncadenado alta = ENCADENADOR.encadenar(Registros.alta().build(), Optional.empty(), MADRID);
        RegistroEncadenado anulacion = ENCADENADOR.encadenar(
                Registros.anulacion(),
                Optional.of(new RegistroAnterior(alta.datos().idFactura(), alta.huella())), MADRID);

        assertThat(anulacion.registroAnterior()).map(RegistroAnterior::huella).contains(alta.huella());
    }

    @Test
    void unAltaYUnaAnulacionConLosMismosDatosDanHuellasDistintas() {
        RegistroEncadenado alta = ENCADENADOR.encadenar(Registros.alta().build(), Optional.empty(), MADRID);
        RegistroEncadenado anulacion = ENCADENADOR.encadenar(Registros.anulacion(), Optional.empty(), MADRID);

        assertThat(anulacion.huella()).isNotEqualTo(alta.huella());
    }

    @Test
    void exigeLaFacturaAnuladaYElSistemaInformatico() {
        assertThatThrownBy(() -> new DatosRegistroAnulacion(null, null, false, false,
                GeneradoPor.E, null, Registros.sistemaInformatico()))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new DatosRegistroAnulacion(Registros.idFactura("FA/1"), null,
                false, false, GeneradoPor.E, null, null))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void respetaElLimiteDeLaReferenciaExterna() {
        assertThatThrownBy(() -> new DatosRegistroAnulacion(Registros.idFactura("FA/1"),
                "X".repeat(61), false, false, GeneradoPor.E, null, Registros.sistemaInformatico()))
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("60");
    }

    @Test
    void unaAnulacionGeneradaPorUnTerceroLoIdentifica() {
        DatosRegistroAnulacion datos = new DatosRegistroAnulacion(
                Registros.idFactura("FA/1"), null, false, false, GeneradoPor.T,
                new PersonaFisicaJuridica("Asesoría SL", new Nif("B12345674")),
                Registros.sistemaInformatico());

        assertThat(datos.generadoPor()).isEqualTo(GeneradoPor.T);
        assertThat(datos.generador().nombreRazon()).isEqualTo("Asesoría SL");
    }

    @Test
    void soloHayDosTiposDeRegistro() {
        assertThat(TipoRegistro.values()).containsExactly(TipoRegistro.ALTA, TipoRegistro.ANULACION);
        assertThat(DatosRegistro.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(DatosRegistroAlta.class, DatosRegistroAnulacion.class);
    }
}
