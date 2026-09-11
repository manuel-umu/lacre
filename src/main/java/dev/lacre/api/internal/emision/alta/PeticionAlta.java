package dev.lacre.api.internal.emision.alta;

import dev.lacre.api.internal.emision.IdFacturaDto;
import dev.lacre.api.internal.emision.PersonaDto;
import dev.lacre.api.internal.emision.PeticionRegistro;
import dev.lacre.verifactu.registro.ClaveTipoRectificativa;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.EmitidaPor;
import dev.lacre.verifactu.registro.RechazoPrevio;
import dev.lacre.verifactu.registro.SistemaInformatico;
import dev.lacre.verifactu.registro.TipoFactura;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Alta de un registro de facturación, tal y como la pide el ERP. Es el contrato versionado,
 * independiente de {@code DatosRegistroAlta}. No se piden el obligado, que es el emisor de la
 * factura, ni el sistema informático, que lo aporta la configuración. Los indicadores son
 * {@code Boolean}: omitidos valen «N».
 */
public record PeticionAlta(
        @NotNull @Valid IdFacturaDto idFactura,
        String refExterna,
        @NotBlank String nombreRazonEmisor,
        Boolean subsanacion,
        RechazoPrevio rechazoPrevio,
        @NotNull TipoFactura tipoFactura,
        ClaveTipoRectificativa tipoRectificativa,
        List<@Valid IdFacturaDto> facturasRectificadas,
        List<@Valid IdFacturaDto> facturasSustituidas,
        @Valid Rectificacion importeRectificacion,
        LocalDate fechaOperacion,
        @NotBlank String descripcionOperacion,
        Boolean facturaSimplificadaArt7273,
        Boolean facturaSinIdentifDestinatarioArt61d,
        Boolean macrodato,
        EmitidaPor emitidaPorTerceroODestinatario,
        @Valid PersonaDto tercero,
        List<@Valid PersonaDto> destinatarios,
        Boolean cupon,
        @NotEmpty @Size(max = 12) List<@Valid Detalle> desglose,
        @NotNull BigDecimal cuotaTotal,
        @NotNull BigDecimal importeTotal,
        String numRegistroAcuerdoFacturacion,
        String idAcuerdoSistemaInformatico) implements PeticionRegistro {

    @Override
    public IdFacturaDto factura() {
        return idFactura;
    }

    @Override
    public DatosRegistroAlta aDatos(SistemaInformatico sistemaInformatico) {
        return MapeadorDeAlta.aDatos(this, sistemaInformatico);
    }

    /** El importe total entra normalizado a dos decimales. */
    @Override
    public String discriminante() {
        return "ALTA:" + (importeTotal == null
                ? ""
                : importeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    /**
     * Línea del desglose.
     *
     * @param impuesto     código de la lista L1: 01 IVA, 02 IPSI, 03 IGIC, 05 otros
     * @param calificacion código de la L9 ({@code S1}, {@code S2}, {@code N1}, {@code N2}) o de
     *                     la L10 de exenciones ({@code E1}…{@code E8})
     */
    public record Detalle(
            String impuesto,
            String claveRegimen,
            @NotBlank String calificacion,
            BigDecimal tipoImpositivo,
            @NotNull BigDecimal baseImponibleOimporteNoSujeto,
            BigDecimal baseImponibleACoste,
            BigDecimal cuotaRepercutida,
            BigDecimal tipoRecargoEquivalencia,
            BigDecimal cuotaRecargoEquivalencia) {
    }

    /** Base y cuota sustituidas en una rectificativa por sustitución. */
    public record Rectificacion(
            @NotNull BigDecimal baseRectificada,
            @NotNull BigDecimal cuotaRectificada,
            BigDecimal cuotaRecargoRectificado) {
    }
}
