package dev.lacre.sif.internal;

import dev.lacre.shared.Huella;
import dev.lacre.sif.Canonicalizador;
import dev.lacre.sif.DatosRegistroAlta;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Canonicalización conforme a «Detalle de las especificaciones técnicas para generación de la
 * huella o hash de los registros de facturación», AEAT, versión 0.1.2 de 27/08/2024, que
 * desarrolla el artículo 13 de la Orden HAC/1177/2024.
 * <p>
 * Los ocho campos del registro de alta se concatenan <strong>en el orden fijado por el
 * apartado 3.a</strong> del documento, con la forma {@code nombre=valor} separada por
 * {@code &}. Ese orden no es negociable y no coincide con el orden de declaración de
 * {@link DatosRegistroAlta} ni con el de los elementos del XSD.
 * <p>
 * Un campo sin valor aporta solo su nombre y el {@code =}, sin nada detrás: es el caso de la
 * huella anterior en el primer registro de la cadena.
 */
public final class CanonicalizadorAeat implements Canonicalizador {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Patrón explícito en lugar de {@code ISO_OFFSET_DATE_TIME}: ese omite los segundos cuando
     * valen cero, lo que produciría una cadena distinta y una huella que la AEAT rechazaría.
     */
    private static final DateTimeFormatter FECHA_HORA_HUSO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Override
    public String canonicalizar(DatosRegistroAlta datos, Optional<Huella> huellaAnterior,
                                OffsetDateTime fechaHoraHusoGenRegistro) {

        StringJoiner cadena = new StringJoiner("&");
        cadena.add(campo("IDEmisorFactura", datos.emisor().valor()));
        cadena.add(campo("NumSerieFactura", datos.numSerieFactura()));
        cadena.add(campo("FechaExpedicionFactura", datos.fechaExpedicion().format(FECHA)));
        cadena.add(campo("TipoFactura", datos.tipoFactura()));
        cadena.add(campo("CuotaTotal", datos.cuotaTotal().valor().toPlainString()));
        cadena.add(campo("ImporteTotal", datos.importeTotal().valor().toPlainString()));
        cadena.add(campo("Huella", huellaAnterior.map(Huella::valor).orElse("")));
        cadena.add(campo("FechaHoraHusoGenRegistro", fechaHoraHusoGenRegistro.format(FECHA_HORA_HUSO)));
        return cadena.toString();
    }

    private static String campo(String nombre, String valor) {
        return nombre + "=" + valor.strip();
    }
}
