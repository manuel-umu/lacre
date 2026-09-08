package dev.lacre.verifactu.registro;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatosRegistroAltaTest {

    @Test
    void elBuilderRellenaLoOpcionalConValoresVacios() {
        DatosRegistroAlta datos = Registros.alta().build();

        assertThat(datos.refExterna()).isNull();
        assertThat(datos.rechazoPrevio()).isNull();
        assertThat(datos.facturasRectificadas()).isEmpty();
        assertThat(datos.facturasSustituidas()).isEmpty();
        assertThat(datos.subsanacion()).isFalse();
        assertThat(datos.macrodato()).isFalse();
        assertThat(datos.cupon()).isFalse();
    }

    @Test
    void exigeLosCamposObligatorios() {
        assertThatThrownBy(() -> Registros.alta().idFactura(null).build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().nombreRazonEmisor("  ").build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().tipoFactura(null).build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().descripcionOperacion(null).build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().desglose(null).build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().cuotaTotal(null).build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().importeTotal(null).build())
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Registros.alta().sistemaInformatico(null).build())
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void respetaLosLimitesDeLongitudDelXsd() {
        assertThatThrownBy(() -> Registros.alta().nombreRazonEmisor("X".repeat(121)).build())
                .isInstanceOf(ValorInvalidoException.class).hasMessageContaining("120");
        assertThatThrownBy(() -> Registros.alta().descripcionOperacion("X".repeat(501)).build())
                .isInstanceOf(ValorInvalidoException.class).hasMessageContaining("500");
        assertThatThrownBy(() -> Registros.alta().refExterna("X".repeat(61)).build())
                .isInstanceOf(ValorInvalidoException.class).hasMessageContaining("60");
    }

    @Test
    void unaReferenciaExternaEnBlancoEsLoMismoQueNoInformarla() {
        assertThat(Registros.alta().refExterna("   ").build().refExterna()).isNull();
    }

    // --- Relaciones que el propio XSD documenta ---

    @ParameterizedTest
    @ValueSource(strings = {"R1", "R2", "R3", "R4", "R5"})
    void soloUnaRectificativaPuedeReferenciarFacturasRectificadas(String tipo) {
        DatosRegistroAlta datos = Registros.alta()
                .tipoFactura(TipoFactura.valueOf(tipo))
                .tipoRectificativa(ClaveTipoRectificativa.I)
                .facturasRectificadas(List.of(Registros.idFactura("FA/ORIGINAL")))
                .build();

        assertThat(datos.facturasRectificadas()).hasSize(1);
        assertThat(datos.tipoFactura().esRectificativa()).isTrue();
    }

    /**
     * Las tres reglas de {@code Validaciones_Errores_Veri-Factu.pdf} §3.1.3 que faltaban.
     * <p>
     * Estas <strong>sí rechazan</strong>, al contrario que el cuadre de totales o la
     * comprobación del art. 7.i: no figuran en la lista cerrada de errores admisibles del §4.3,
     * así que la AEAT rechaza el registro. Y un registro rechazado que ya está en la cadena solo
     * se puede subsanar, porque la cadena es de solo inserción.
     */
    @ParameterizedTest
    @ValueSource(strings = {"R1", "R2", "R3", "R4", "R5"})
    void unaRectificativaSinTipoDeRectificativaNoVale(String tipo) {
        assertThatThrownBy(() -> Registros.alta()
                .tipoFactura(TipoFactura.valueOf(tipo))
                .facturasRectificadas(List.of(Registros.idFactura("FA/ORIGINAL")))
                .build())
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("sustitución");
    }

    @Test
    void unaFacturaQueNoEsRectificativaNoPuedeLlevarTipoDeRectificativa() {
        assertThatThrownBy(() -> Registros.alta()
                .tipoFactura(TipoFactura.F1)
                .tipoRectificativa(ClaveTipoRectificativa.S)
                .build())
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("rectificativa");
    }

    @ParameterizedTest
    @EnumSource(value = RechazoPrevio.class, names = {"S", "X"})
    void elRechazoPrevioSoloCabeEnUnaSubsanacion(RechazoPrevio rechazo) {
        assertThatThrownBy(() -> Registros.alta().subsanacion(false).rechazoPrevio(rechazo).build())
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("subsanación");

        assertThat(Registros.alta().subsanacion(true).rechazoPrevio(rechazo).build().rechazoPrevio())
                .isEqualTo(rechazo);
    }

    @Test
    void unaFacturaQueNoEsSubsanacionSiPuedeDecirQueNoHuboRechazoPrevio() {
        assertThat(Registros.alta().subsanacion(false).rechazoPrevio(RechazoPrevio.N).build()
                .rechazoPrevio()).isEqualTo(RechazoPrevio.N);
        assertThat(Registros.alta().subsanacion(false).build().rechazoPrevio()).isNull();
    }

    @Test
    void unaFacturaNoRectificativaNoPuedeReferenciarFacturasRectificadas() {
        assertThatThrownBy(() -> Registros.alta()
                .tipoFactura(TipoFactura.F1)
                .facturasRectificadas(List.of(Registros.idFactura("FA/ORIGINAL")))
                .build())
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("rectificativa");
    }

    @Test
    void soloUnaF3PuedeReferenciarFacturasSustituidas() {
        assertThat(Registros.alta()
                .tipoFactura(TipoFactura.F3)
                .facturasSustituidas(List.of(Registros.idFactura("FA/SIMPLIFICADA")))
                .build().facturasSustituidas()).hasSize(1);

        assertThatThrownBy(() -> Registros.alta()
                .tipoFactura(TipoFactura.F2)
                .facturasSustituidas(List.of(Registros.idFactura("FA/SIMPLIFICADA")))
                .build())
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("F3");
    }

    @Test
    void lasListasSeCopianYQuedanInmutables() {
        List<PersonaFisicaJuridica> mutable = new ArrayList<>();
        mutable.add(new PersonaFisicaJuridica("Cliente", new Nif("12345678Z")));

        DatosRegistroAlta datos = Registros.alta().destinatarios(mutable).build();
        mutable.clear();

        assertThat(datos.destinatarios()).hasSize(1);
        assertThatThrownBy(() -> datos.destinatarios().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void admiteHastaMilDestinatariosYNiUnoMas() {
        assertThat(Registros.alta().destinatarios(destinatarios(1000)).build().destinatarios()).hasSize(1000);

        assertThatThrownBy(() -> Registros.alta().destinatarios(destinatarios(1001)).build())
                .isInstanceOf(ValorInvalidoException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void unaFacturaSimplificadaPuedeNoLlevarDestinatarios() {
        assertThat(Registros.alta()
                .tipoFactura(TipoFactura.F2)
                .destinatarios(List.of())
                .build().destinatarios()).isEmpty();
    }

    // --- Cuadre de totales con el desglose ---

    @Test
    void cuadraCuandoLosTotalesCoincidenConElDesglose() {
        DatosRegistroAlta datos = Registros.alta().build();

        assertThat(datos.seContrastanLosTotales()).isTrue();
        assertThat(datos.cuadraLaCuotaTotal()).isTrue();
        assertThat(datos.cuadraElImporteTotal()).isTrue();
    }

    @Test
    void admiteUnaDesviacionDeHastaDiezEurosEnCadaSentido() {
        assertThat(Registros.alta().importeTotal(Importe.de("133.45")).build().cuadraElImporteTotal()).isTrue();
        assertThat(Registros.alta().importeTotal(Importe.de("113.45")).build().cuadraElImporteTotal()).isTrue();
        assertThat(Registros.alta().cuotaTotal(Importe.de("22.35")).build().cuadraLaCuotaTotal()).isTrue();
    }

    @Test
    void noCuadraCuandoLaDesviacionSuperaElMargen() {
        assertThat(Registros.alta().importeTotal(Importe.de("133.46")).build().cuadraElImporteTotal()).isFalse();
        assertThat(Registros.alta().importeTotal(Importe.de("113.44")).build().cuadraElImporteTotal()).isFalse();
        assertThat(Registros.alta().cuotaTotal(Importe.de("22.36")).build().cuadraLaCuotaTotal()).isFalse();
    }

    /**
     * Un descuadre no impide construir el registro: la AEAT lo acepta con errores y la norma
     * prohíbe interrumpir la facturación por esto.
     */
    @Test
    void unDescuadreNoImpideConstruirElRegistro() {
        DatosRegistroAlta descuadrado = Registros.alta().importeTotal(Importe.de("999.99")).build();

        assertThat(descuadrado.cuadraElImporteTotal()).isFalse();
        assertThat(descuadrado.importeTotal()).isEqualTo(Importe.de("999.99"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"03", "05", "06", "08", "09"})
    void lasClavesDeRegimenExentasDesactivanElContrasteDeTotales(String clave) {
        DatosRegistroAlta datos = Registros.alta()
                .desglose(Desglose.de(detalleCon(clave)))
                .importeTotal(Importe.de("999999.99"))
                .build();

        assertThat(datos.seContrastanLosTotales()).isFalse();
        assertThat(datos.cuadraElImporteTotal()).isTrue();
        assertThat(datos.cuadraLaCuotaTotal()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "04", "07", "10"})
    void lasDemasClavesDeRegimenSiContrastan(String clave) {
        DatosRegistroAlta datos = Registros.alta()
                .desglose(Desglose.de(detalleCon(clave)))
                .importeTotal(Importe.de("999999.99"))
                .build();

        assertThat(datos.seContrastanLosTotales()).isTrue();
        assertThat(datos.cuadraElImporteTotal()).isFalse();
    }

    private static DetalleDesglose detalleCon(String claveRegimen) {
        return new DetalleDesglose(Impuesto.IVA, new ClaveRegimen(claveRegimen),
                CalificacionOperacion.S1, Porcentaje.de("21"), Importe.de("111.10"),
                null, Importe.de("12.35"), null, null);
    }

    private static List<PersonaFisicaJuridica> destinatarios(int cuantos) {
        return IntStream.range(0, cuantos)
                .mapToObj(i -> new PersonaFisicaJuridica("Cliente " + i, new Nif("12345678Z")))
                .toList();
    }
}
