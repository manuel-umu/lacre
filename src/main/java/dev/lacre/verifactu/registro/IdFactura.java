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
 * <p>
 * El número de serie tiene una restricción de caracteres que el XSD <strong>no</strong> expresa
 * y que sí está en {@code Validaciones_Errores_Veri-Factu.pdf}, §3.1.3. Incumplirla provoca el
 * rechazo del registro por la AEAT, así que se valida al construir: un registro que la AEAT va a
 * rechazar no debe llegar a entrar en la cadena, porque la cadena es de solo inserción y lo
 * único que quedaría es subsanarlo.
 */
public record IdFactura(Nif emisor, String numSerieFactura, LocalDate fechaExpedicion) {

    public static final int MAXIMO_LONGITUD_NUM_SERIE = 60;

    /**
     * Caracteres prohibidos en el número de serie, por su código ASCII y no por su glifo: el
     * documento los lista como 34, 39, 60, 61 y 62, y el 39 es la comilla simple aunque en el
     * PDF se imprima como un acento grave.
     * <p>
     * No es capricho de la AEAT: el {@code =} es el separador de la cadena canónica de la
     * huella, y {@code <} y {@code >} romperían el XML.
     */
    private static final String PROHIBIDOS = "\"'<=>";

    public IdFactura {
        if (emisor == null) {
            throw new ValorInvalidoException("El emisor de la factura es obligatorio");
        }
        numSerieFactura = Textos.obligatorio(
                numSerieFactura, MAXIMO_LONGITUD_NUM_SERIE, "El número de serie de la factura");
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
                throw new ValorInvalidoException(
                        "El número de serie de la factura no admite el carácter '%c' (ASCII %d): "
                                .formatted(caracter, (int) caracter)
                                + "solo ASCII imprimible salvo \", ', <, = y >");
            }
        }
    }
}
