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
 * Alta de un registro de facturación, tal y como la pide el ERP.
 * <p>
 * <strong>No es {@code DatosRegistroAlta}.</strong> Este es el contrato que se versiona y por
 * el que se cobra; aquel es el modelo fiscal, que cambia cuando cambia la norma. Atarlos sería
 * romper a los clientes cada vez que se refactoriza el dominio.
 * <p>
 * Dos ausencias deliberadas:
 * <ul>
 * <li><strong>No se pide el obligado.</strong> Es {@code idFactura.idEmisorFactura}: en
 *     Veri*Factu el emisor de la factura <em>es</em> el obligado por cuenta de quien se expide,
 *     y pedirlo dos veces solo abre la puerta a que no coincidan.</li>
 * <li><strong>No se pide el sistema informático.</strong> Lo declaramos nosotros, desde la
 *     configuración del despliegue, porque es el dato que respalda nuestra declaración
 *     responsable. Que lo pusiera el integrador sería dejarle firmar en nuestro nombre.</li>
 * </ul>
 * Los tipos enumerados se envían con el código de la AEAT ({@code F1}, {@code S}, {@code T}…),
 * que es el nombre de la constante. Los dos catálogos cuyo código es numérico —impuesto y tipo
 * de identificación— viajan como texto y los traducen {@link MapeadorDeAlta} y {@link PersonaDto}.
 * <p>
 * Los indicadores son {@code Boolean} y no {@code boolean} para que se puedan omitir, que es lo
 * que hará casi siempre el integrador. El diseño de registro oficial lo dice para todos ellos:
 * «si no se informa este campo se entenderá que tiene valor "N"».
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

    /**
     * El importe total entra en la huella de la petición normalizado a dos decimales: {@code 100}
     * y {@code 100.00} son la misma factura y no deben dar un 409.
     */
    @Override
    public String discriminante() {
        return "ALTA:" + (importeTotal == null
                ? ""
                : importeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    /**
     * Una línea del desglose. Va aplanado —una lista, no un objeto {@code desglose} con una
     * lista dentro— porque el envoltorio del XSD no aporta nada en JSON.
     *
     * @param impuesto código de la lista L1: 01 IVA, 02 IPSI, 03 IGIC, 05 otros
     * @param calificacion código de la L9 ({@code S1}, {@code S2}, {@code N1}, {@code N2}) o de
     *                     la L10 de exenciones ({@code E1}…{@code E8}). Van en el mismo campo
     *                     porque el XSD los declara excluyentes y los códigos no se solapan.
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
