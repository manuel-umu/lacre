package dev.lacre.api.internal.emision.anulacion;

import dev.lacre.api.internal.emision.IdFacturaDto;
import dev.lacre.api.internal.emision.PersonaDto;
import dev.lacre.api.internal.emision.PeticionRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.GeneradoPor;
import dev.lacre.verifactu.registro.SistemaInformatico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Anulación de una factura ya expedida, tal y como la pide el ERP. Anular añade un eslabón
 * nuevo a la cadena; no borra el del alta.
 *
 * @param rechazoPrevio booleano: el esquema solo admite {@code S} o {@code N} en anulaciones
 */
public record PeticionAnulacion(
        @NotNull @Valid IdFacturaDto idFacturaAnulada,
        String refExterna,
        Boolean sinRegistroPrevio,
        Boolean rechazoPrevio,
        GeneradoPor generadoPor,
        @Valid PersonaDto generador) implements PeticionRegistro {

    @Override
    public IdFacturaDto factura() {
        return idFacturaAnulada;
    }

    @Override
    public DatosRegistroAnulacion aDatos(SistemaInformatico sistemaInformatico) {
        return new DatosRegistroAnulacion(
                idFacturaAnulada.aDominio(),
                refExterna,
                PeticionRegistro.si(sinRegistroPrevio),
                PeticionRegistro.si(rechazoPrevio),
                generadoPor,
                PersonaDto.opcional(generador),
                sistemaInformatico);
    }

    /** Basta distinguir la anulación de un alta sobre la misma factura. */
    @Override
    public String discriminante() {
        return "ANULACION";
    }
}
