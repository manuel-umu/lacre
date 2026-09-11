package dev.lacre.api.internal.emision;

import dev.lacre.shared.Nif;
import dev.lacre.verifactu.registro.IdFactura;
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
 * <p>
 * Lo usan el alta y la anulación, así que vive en la raíz de {@code emision} y no en ninguno de
 * los dos subpaquetes.
 */
public record IdFacturaDto(
        @NotBlank String idEmisorFactura,
        @NotBlank String numSerieFactura,
        @NotNull LocalDate fechaExpedicionFactura) {

    /**
     * La validación de verdad —longitud, caracteres admitidos, NIF con su carácter de control—
     * la hacen {@link IdFactura} y {@link Nif}. Aquí no se repite: dos versiones de la misma
     * regla acaban discrepando.
     */
    public IdFactura aDominio() {
        return new IdFactura(new Nif(idEmisorFactura), numSerieFactura, fechaExpedicionFactura);
    }
}
