package dev.lacre.verifactu.registro;

import dev.lacre.shared.Nif;
import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;

import java.time.LocalDate;

/**
 * Identificación de una factura, {@code IDFacturaExpedidaType} del XSD.
 * <p>
 * El mismo trío identifica también las facturas rectificadas y sustituidas
 * ({@code IDFacturaARType}), que tienen idéntica estructura.
 */
public record IdFactura(Nif emisor, String numSerieFactura, LocalDate fechaExpedicion) {

    public static final int MAXIMO_LONGITUD_NUM_SERIE = 60;

    public IdFactura {
        if (emisor == null) {
            throw new ValorInvalidoException("El emisor de la factura es obligatorio");
        }
        numSerieFactura = Textos.obligatorio(
                numSerieFactura, MAXIMO_LONGITUD_NUM_SERIE, "El número de serie de la factura");
        if (fechaExpedicion == null) {
            throw new ValorInvalidoException("La fecha de expedición es obligatoria");
        }
    }
}
