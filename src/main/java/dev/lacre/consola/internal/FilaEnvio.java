package dev.lacre.consola.internal;

import dev.lacre.remision.EnvioRegistro;
import dev.lacre.verifactu.consulta.RegistroGuardado;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Un envío en el detalle de un obligado, con la factura de su registro y las fechas en la zona
 * horaria del obligado.
 *
 * @param registro nulo si el registro no se encontró
 */
public record FilaEnvio(EnvioRegistro envio, RegistroGuardado registro, ZoneId zona) {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String creado() {
        return formatear(envio.creadoEn());
    }

    public String respondido() {
        return formatear(envio.enviadoEn());
    }

    public String factura() {
        return registro == null ? "—" : registro.idFactura().numSerieFactura();
    }

    public String fechaFactura() {
        return registro == null ? "—" : registro.idFactura().fechaExpedicion().format(FECHA);
    }

    public String tipo() {
        if (registro == null) {
            return "—";
        }
        return registro.tipo() == TipoRegistro.ALTA ? "Alta" : "Anulación";
    }

    private String formatear(OffsetDateTime momento) {
        return momento == null ? "—" : momento.atZoneSameInstant(zona).format(FECHA_Y_HORA);
    }
}
