package dev.lacre.verifactu.internal;

import dev.lacre.shared.Importe;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formatos con los que un valor del dominio se escribe para la AEAT.
 * <p>
 * Viven en un único sitio porque los usan tanto la cadena canónica de la huella como el XML, y
 * la AEAT recalcula la huella sobre el XML que le llega: si los dos formatearan distinto, el
 * hash no cuadraría y el registro se aceptaría con errores sin que nada fallase antes.
 */
public final class FormatosAeat {

    /** {@code sf:fecha}: {@code dd-MM-yyyy}, no ISO. */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Patrón explícito en lugar de {@code ISO_OFFSET_DATE_TIME}: ese omite los segundos cuando
     * valen cero, lo que produciría una huella distinta a la que calcula la AEAT.
     */
    private static final DateTimeFormatter FECHA_HORA_HUSO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private FormatosAeat() {
    }

    public static String fecha(LocalDate fecha) {
        return fecha.format(FECHA);
    }

    public static String fechaHoraHuso(OffsetDateTime fechaHora) {
        return fechaHora.format(FECHA_HORA_HUSO);
    }

    public static String importe(Importe importe) {
        return importe.valor().toPlainString();
    }

    /** Los indicadores del anexo son {@code S} o {@code N}, nunca {@code true}/{@code false}. */
    public static String indicador(boolean valor) {
        return valor ? "S" : "N";
    }
}
