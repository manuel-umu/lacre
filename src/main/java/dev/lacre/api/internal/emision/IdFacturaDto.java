package dev.lacre.api.internal.emision;

import dev.lacre.shared.Nif;
import dev.lacre.verifactu.registro.IdFactura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Identificación de una factura en la API, con los nombres de la AEAT en camelCase. */
public record IdFacturaDto(
        @NotBlank String idEmisorFactura,
        @NotBlank String numSerieFactura,
        @NotNull LocalDate fechaExpedicionFactura) {

    /** La validación la hacen {@link IdFactura} y {@link Nif}. */
    public IdFactura aDominio() {
        return new IdFactura(new Nif(idEmisorFactura), numSerieFactura, fechaExpedicionFactura);
    }
}
