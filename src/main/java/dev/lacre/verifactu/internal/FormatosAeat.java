package dev.lacre.verifactu.internal;

import dev.lacre.shared.Importe;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formatos con los que se escriben fechas, importes e indicadores para la AEAT, compartidos
 * por la huella y el XML.
 */
public final class FormatosAeat {

    /** {@code sf:fecha}: {@code dd-MM-yyyy}. */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Patrón explícito: {@code ISO_OFFSET_DATE_TIME} omite los segundos cuando valen cero. */
    private static final DateTimeFormatter FECHA_HORA_HUSO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private FormatosAeat() {}

    public static String fecha(LocalDate fecha) {
        return fecha.format(FECHA);
    }

    public static String fechaHoraHuso(OffsetDateTime fechaHora) {
        return fechaHora.format(FECHA_HORA_HUSO);
    }

    public static LocalDate leerFecha(String texto) {
        return LocalDate.parse(texto, FECHA);
    }

    public static OffsetDateTime leerFechaHoraHuso(String texto) {
        return OffsetDateTime.parse(texto, FECHA_HORA_HUSO);
    }

    public static String importe(Importe importe) {
        return importe.valor().toPlainString();
    }

    /** Los indicadores se escriben como {@code S} o {@code N}. */
    public static String indicador(boolean valor) {
        return valor ? "S" : "N";
    }
}
