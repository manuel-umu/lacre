package dev.lacre.verifactu.consulta;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Un registro guardado con lo que hace falta para cotejarlo con la AEAT.
 *
 * @param fechaOperacion la del XML de un alta; nula si no la declara o es una anulación
 */
public record RegistroDeFactura(
        UUID id, long posicion, TipoRegistro tipo, IdFactura idFactura, Huella huella, LocalDate fechaOperacion) {

    /** Periodo de imputación: el mes de la fecha de operación o, si no la hay, el de la de expedición. */
    public YearMonth periodoDeImputacion() {
        return YearMonth.from(fechaOperacion != null ? fechaOperacion : idFactura.fechaExpedicion());
    }
}
