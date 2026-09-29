package dev.lacre.verifactu.internal;

import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.registro.CamposDeHuella;
import dev.lacre.verifactu.registro.RegistroAnterior;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Canonicalización de la huella conforme al artículo 13 de la Orden HAC/1177/2024: campos
 * {@code nombre=valor} separados por {@code &}, en el orden que fija la AEAT, distinto para el
 * alta (ocho campos) y la anulación (cinco). Un campo sin valor aporta solo su nombre y el
 * {@code =}.
 */
public final class CanonicalizadorAeat implements Canonicalizador {

    @Override
    public String canonicalizar(
            CamposDeHuella campos,
            Optional<RegistroAnterior> registroAnterior,
            OffsetDateTime fechaHoraHusoGenRegistro) {

        return switch (campos) {
            case CamposDeHuella.Alta alta -> alta(alta, registroAnterior, fechaHoraHusoGenRegistro);
            case CamposDeHuella.Anulacion anulacion -> anulacion(anulacion, registroAnterior, fechaHoraHusoGenRegistro);
        };
    }

    /** Alta: ocho campos. */
    private static String alta(
            CamposDeHuella.Alta datos, Optional<RegistroAnterior> anterior, OffsetDateTime fechaHoraHusoGenRegistro) {
        StringJoiner cadena = new StringJoiner("&");
        cadena.add(campo("IDEmisorFactura", datos.idFactura().emisor().valor()));
        cadena.add(campo("NumSerieFactura", datos.idFactura().numSerieFactura()));
        cadena.add(campo(
                "FechaExpedicionFactura", FormatosAeat.fecha(datos.idFactura().fechaExpedicion())));
        cadena.add(campo("TipoFactura", datos.tipoFactura().codigo()));
        cadena.add(campo("CuotaTotal", FormatosAeat.importe(datos.cuotaTotal())));
        cadena.add(campo("ImporteTotal", FormatosAeat.importe(datos.importeTotal())));
        cadena.add(campo("Huella", huellaAnterior(anterior)));
        cadena.add(campo("FechaHoraHusoGenRegistro", FormatosAeat.fechaHoraHuso(fechaHoraHusoGenRegistro)));
        return cadena.toString();
    }

    /** Anulación: cinco campos; los tres primeros llevan el sufijo {@code Anulada}. */
    private static String anulacion(
            CamposDeHuella.Anulacion datos,
            Optional<RegistroAnterior> anterior,
            OffsetDateTime fechaHoraHusoGenRegistro) {
        StringJoiner cadena = new StringJoiner("&");
        cadena.add(campo("IDEmisorFacturaAnulada", datos.idFactura().emisor().valor()));
        cadena.add(campo("NumSerieFacturaAnulada", datos.idFactura().numSerieFactura()));
        cadena.add(campo(
                "FechaExpedicionFacturaAnulada",
                FormatosAeat.fecha(datos.idFactura().fechaExpedicion())));
        cadena.add(campo("Huella", huellaAnterior(anterior)));
        cadena.add(campo("FechaHoraHusoGenRegistro", FormatosAeat.fechaHoraHuso(fechaHoraHusoGenRegistro)));
        return cadena.toString();
    }

    private static String huellaAnterior(Optional<RegistroAnterior> anterior) {
        return anterior.map(enlace -> enlace.huella().valor()).orElse("");
    }

    private static String campo(String nombre, String valor) {
        return nombre + "=" + valor.strip();
    }
}
