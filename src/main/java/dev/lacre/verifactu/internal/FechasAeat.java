package dev.lacre.verifactu.internal;

import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import java.time.LocalDate;

/** Validaciones de la AEAT sobre las fechas de un alta que dependen del día de emisión. */
public final class FechasAeat {

    /** Entrada en vigor de la Orden HAC/1177/2024. */
    public static final LocalDate ENTRADA_EN_VIGOR = LocalDate.of(2024, 10, 28);

    private FechasAeat() {}

    public static void exigir(DatosRegistroAlta datos, LocalDate hoy) {
        LocalDate expedicion = datos.idFactura().fechaExpedicion();
        ReglaAeatIncumplidaException.exigir(
                !expedicion.isBefore(ENTRADA_EN_VIGOR),
                "1152",
                "La fecha de expedición no puede ser anterior a la entrada en vigor de la Orden " + "HAC/1177/2024, el "
                        + ENTRADA_EN_VIGOR + ", y es " + expedicion);
        ReglaAeatIncumplidaException.exigir(
                !expedicion.isAfter(hoy),
                "1112",
                "La fecha de expedición no puede ser posterior a hoy, y es " + expedicion);

        LocalDate operacion = datos.fechaOperacion();
        if (operacion == null) {
            return;
        }
        LocalDate limiteAntiguedad = hoy.minusYears(20);
        ReglaAeatIncumplidaException.exigir(
                !operacion.isBefore(limiteAntiguedad),
                "1134",
                "La fecha de operación no puede ser anterior a " + limiteAntiguedad
                        + ", veinte años antes de hoy, y es " + operacion);
        ReglaAeatIncumplidaException.exigir(
                operacion.getYear() <= hoy.getYear() + 1,
                "1125",
                "El año de la fecha de operación no puede ser posterior al siguiente a hoy, y es " + operacion);
    }
}
