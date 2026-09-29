package dev.lacre.verifactu.registro;

import dev.lacre.shared.Nif;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;
import java.time.LocalDate;

/**
 * Identificación de una factura, {@code IDFacturaExpedidaType} del XSD; también identifica las
 * facturas rectificadas y sustituidas. Los caracteres del número de serie se validan al
 * construir, porque la AEAT rechaza los no admitidos.
 */
public record IdFactura(Nif emisor, String numSerieFactura, LocalDate fechaExpedicion) {

    public static final int MAXIMO_LONGITUD_NUM_SERIE = 60;

    /** Caracteres prohibidos en el número de serie: ASCII 34, 39, 60, 61 y 62. */
    private static final String PROHIBIDOS = "\"'<=>";

    public IdFactura {
        if (emisor == null) {
            throw new ValorInvalidoException("El emisor de la factura es obligatorio");
        }
        numSerieFactura =
                Textos.obligatorio(numSerieFactura, MAXIMO_LONGITUD_NUM_SERIE, "El número de serie de la factura");
        exigirCaracteresAdmitidos(numSerieFactura);
        if (fechaExpedicion == null) {
            throw new ValorInvalidoException("La fecha de expedición es obligatoria");
        }
    }

    /** Solo ASCII imprimible (32 a 126), y de ahí fuera los cinco de {@link #PROHIBIDOS}. */
    private static void exigirCaracteresAdmitidos(String numSerieFactura) {
        for (int i = 0; i < numSerieFactura.length(); i++) {
            char caracter = numSerieFactura.charAt(i);
            if (caracter < 32 || caracter > 126 || PROHIBIDOS.indexOf(caracter) >= 0) {
                throw new ReglaAeatIncumplidaException(
                        "1130",
                        "El número de serie de la factura no admite el carácter '%c' (ASCII %d): "
                                        .formatted(caracter, (int) caracter)
                                + "solo ASCII imprimible salvo \", ', <, = y >");
            }
        }
    }
}
