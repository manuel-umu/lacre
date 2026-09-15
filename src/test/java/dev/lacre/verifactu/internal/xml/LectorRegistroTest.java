package dev.lacre.verifactu.internal.xml;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.verifactu.huella.EncadenadorRegistros;
import dev.lacre.verifactu.internal.CanonicalizadorAeat;
import dev.lacre.verifactu.registro.CamposDeHuella;
import dev.lacre.verifactu.registro.ClaveTipoRectificativa;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.Registros;
import dev.lacre.verifactu.registro.TipoFactura;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El lector contra el escritor: lo que se escribe se lee igual, y la huella recalculada desde el
 * XML es la de los tres ejemplos oficiales del documento de la huella.
 */
class LectorRegistroTest {

    private static final CanonicalizadorAeat CANONICALIZADOR = new CanonicalizadorAeat();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final Huella HUELLA_CASO_1 = new Huella(
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60");
    private static final Huella HUELLA_CASO_2 = new Huella(
            "F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97");
    private static final Huella HUELLA_CASO_3 = new Huella(
            "177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68");

    @Test
    void desdeElXmlSeRecalculaLaHuellaDelCaso1() {
        RegistroEncadenado registro = encadenadoA("2024-01-01T18:20:30Z")
                .encadenar(alta("12345678/G33"), Optional.empty(), MADRID);

        assertThat(recalculada(registro)).isEqualTo(HUELLA_CASO_1);
    }

    @Test
    void desdeElXmlSeRecalculaLaHuellaDelCaso2() {
        RegistroEncadenado registro = encadenadoA("2024-01-01T18:20:35Z")
                .encadenar(alta("12345679/G34"), Optional.of(Registros.anterior(HUELLA_CASO_1)),
                        MADRID);

        assertThat(recalculada(registro)).isEqualTo(HUELLA_CASO_2);
    }

    @Test
    void desdeElXmlSeRecalculaLaHuellaDelCaso3DeAnulacion() {
        RegistroEncadenado registro = encadenadoA("2024-01-01T18:20:40Z")
                .encadenar(Registros.anulacion(), Optional.of(new RegistroAnterior(
                        Registros.idFactura("12345679/G34"), HUELLA_CASO_2)), MADRID);

        RegistroLeido leido = LectorRegistro.leer(EscritorRegistro.escribir(registro));

        assertThat(leido.campos()).isInstanceOf(CamposDeHuella.Anulacion.class);
        assertThat(leido.huellaRecalculada(CANONICALIZADOR)).isEqualTo(HUELLA_CASO_3);
    }

    /**
     * Una rectificativa que no abre la cadena lleva tres {@code NumSerieFactura} y dos
     * {@code Huella}. Buscar por nombre de elemento elegiría mal sin avisar.
     */
    @Test
    void enUnaRectificativaLeeCadaNumeroDeSerieYCadaHuellaDeSuSitio() {
        Huella delAnterior = new Huella("A".repeat(64));
        RegistroEncadenado registro = encadenadoA("2024-01-01T18:20:30Z").encadenar(
                Registros.alta()
                        .idFactura(Registros.idFactura("R/1"))
                        .tipoFactura(TipoFactura.R1)
                        .tipoRectificativa(ClaveTipoRectificativa.I)
                        .facturasRectificadas(List.of(Registros.idFactura("FA/RECTIFICADA")))
                        .build(),
                Optional.of(new RegistroAnterior(Registros.idFactura("FA/ANTERIOR"), delAnterior)),
                MADRID);

        RegistroLeido leido = LectorRegistro.leer(EscritorRegistro.escribir(registro));

        assertThat(leido.campos().idFactura().numSerieFactura()).isEqualTo("R/1");
        assertThat(leido.anterior()).hasValueSatisfying(anterior -> {
            assertThat(anterior.idFactura().numSerieFactura()).isEqualTo("FA/ANTERIOR");
            assertThat(anterior.huella()).isEqualTo(delAnterior);
        });
        assertThat(leido.huella()).isEqualTo(registro.huella());
        assertThat(leido.huellaRecalculada(CANONICALIZADOR)).isEqualTo(registro.huella());
    }

    @Test
    void loQueSeLeeEsLoQueSeEscribio() {
        RegistroEncadenado registro = encadenadoA("2024-01-01T18:20:35Z")
                .encadenar(alta("FA/1 & G33"), Optional.of(Registros.anterior(HUELLA_CASO_1)),
                        MADRID);

        RegistroLeido leido = LectorRegistro.leer(EscritorRegistro.escribir(registro));

        assertThat(leido.campos()).isEqualTo(registro.datos().camposDeHuella());
        assertThat(leido.anterior()).isEqualTo(registro.registroAnterior());
        assertThat(leido.fechaHoraHusoGenRegistro()).isEqualTo(registro.fechaHoraHusoGenRegistro());
        assertThat(leido.huella()).isEqualTo(registro.huella());
    }

    @Test
    void loQueNoEsUnRegistroNoSeLee() {
        assertThatThrownBy(() -> LectorRegistro.leer("<x/>"))
                .isInstanceOf(RegistroIlegibleException.class);
        assertThatThrownBy(() -> LectorRegistro.leer("no es XML"))
                .isInstanceOf(RegistroIlegibleException.class);
    }

    @Test
    void aUnRegistroQueLeFaltaUnCampoDeLaHuellaNoSeLeInventa() {
        String xml = EscritorRegistro.escribir(encadenadoA("2024-01-01T18:20:30Z")
                .encadenar(alta("FA/1"), Optional.empty(), MADRID));
        String sinCuota = xml.replaceFirst("<([a-z]+:)?CuotaTotal>[^<]*</([a-z]+:)?CuotaTotal>", "");

        assertThat(sinCuota).isNotEqualTo(xml);
        assertThatThrownBy(() -> LectorRegistro.leer(sinCuota))
                .isInstanceOf(RegistroIlegibleException.class)
                .hasMessageContaining("CuotaTotal");
    }

    @Test
    void noResuelveEntidadesExternas() {
        String conEntidad = """
                <!DOCTYPE r [<!ENTITY fuera SYSTEM "file:///etc/passwd">]>
                <RegistroAlta>&fuera;</RegistroAlta>
                """;

        assertThatThrownBy(() -> LectorRegistro.leer(conEntidad))
                .isInstanceOf(RegistroIlegibleException.class);
    }

    private static Huella recalculada(RegistroEncadenado registro) {
        return LectorRegistro.leer(EscritorRegistro.escribir(registro))
                .huellaRecalculada(CANONICALIZADOR);
    }

    private static EncadenadorRegistros encadenadoA(String instante) {
        return new EncadenadorRegistros(
                Clock.fixed(Instant.parse(instante), ZoneOffset.UTC), CANONICALIZADOR);
    }

    private static DatosRegistroAlta alta(String numSerie) {
        return Registros.alta()
                .idFactura(Registros.idFactura(numSerie))
                .cuotaTotal(Importe.de("12.35"))
                .importeTotal(Importe.de("123.45"))
                .build();
    }
}
