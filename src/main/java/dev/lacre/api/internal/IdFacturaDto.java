package dev.lacre.api.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Identificación de una factura tal y como viaja por la API.
 * <p>
 * Los nombres son los del diseño de registro de la AEAT —{@code IDEmisorFactura},
 * {@code NumSerieFactura}, {@code FechaExpedicionFactura}— en camelCase. Quien integra tiene el
 * documento oficial abierto al lado, y que los campos se llamen igual ahorra la traducción
 * mental y permite que un error nuestro cite el campo con el nombre que la AEAT usa.
 */
public record IdFacturaDto(
        @NotBlank String idEmisorFactura,
        @NotBlank String numSerieFactura,
        @NotNull LocalDate fechaExpedicionFactura) {
}
