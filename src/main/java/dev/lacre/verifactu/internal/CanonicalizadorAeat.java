package dev.lacre.verifactu.internal;

import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.RegistroAnterior;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Canonicalización conforme a «Detalle de las especificaciones técnicas para generación de la
 * huella o hash de los registros de facturación», AEAT, versión 0.1.2 de 27/08/2024, que
 * desarrolla el artículo 13 de la Orden HAC/1177/2024.
 * <p>
 * Los campos se concatenan <strong>en el orden fijado por el apartado 3</strong>, con la forma
 * {@code nombre=valor} separada por {@code &}. Ese orden no es negociable, no coincide con el
 * de declaración de los records ni con el de los elementos del XSD, y <strong>es distinto para
 * el alta (apartado 3.a, ocho campos) y para la anulación (apartado 3.b, cinco)</strong>.
 * <p>
 * Un campo sin valor aporta solo su nombre y el {@code =}, sin nada detrás: es el caso de la
 * huella anterior en el primer registro de la cadena.
 */
public final class CanonicalizadorAeat implements Canonicalizador {

    @Override
    public String canonicalizar(DatosRegistro datos, Optional<RegistroAnterior> registroAnterior,
                                OffsetDateTime fechaHoraHusoGenRegistro) {

        return switch (datos) {
            case DatosRegistroAlta alta -> alta(alta, registroAnterior, fechaHoraHusoGenRegistro);
            case DatosRegistroAnulacion anulacion ->
                    anulacion(anulacion, registroAnterior, fechaHoraHusoGenRegistro);
        };
    }

    /** Apartado 3.a: ocho campos. */
    private static String alta(DatosRegistroAlta datos, Optional<RegistroAnterior> anterior,
                               OffsetDateTime fechaHoraHusoGenRegistro) {
        StringJoiner cadena = new StringJoiner("&");
        cadena.add(campo("IDEmisorFactura", datos.idFactura().emisor().valor()));
        cadena.add(campo("NumSerieFactura", datos.idFactura().numSerieFactura()));
        cadena.add(campo("FechaExpedicionFactura", FormatosAeat.fecha(datos.idFactura().fechaExpedicion())));
        cadena.add(campo("TipoFactura", datos.tipoFactura().codigo()));
        cadena.add(campo("CuotaTotal", FormatosAeat.importe(datos.cuotaTotal())));
        cadena.add(campo("ImporteTotal", FormatosAeat.importe(datos.importeTotal())));
        cadena.add(campo("Huella", huellaAnterior(anterior)));
        cadena.add(campo("FechaHoraHusoGenRegistro", FormatosAeat.fechaHoraHuso(fechaHoraHusoGenRegistro)));
        return cadena.toString();
    }

    /**
     * Apartado 3.b: cinco campos, y los tres primeros cambian de nombre respecto del alta
     * —llevan el sufijo {@code Anulada}—, aunque el valor salga del mismo sitio.
     */
    private static String anulacion(DatosRegistroAnulacion datos, Optional<RegistroAnterior> anterior,
                                    OffsetDateTime fechaHoraHusoGenRegistro) {
        StringJoiner cadena = new StringJoiner("&");
        cadena.add(campo("IDEmisorFacturaAnulada", datos.idFactura().emisor().valor()));
        cadena.add(campo("NumSerieFacturaAnulada", datos.idFactura().numSerieFactura()));
        cadena.add(campo("FechaExpedicionFacturaAnulada",
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
