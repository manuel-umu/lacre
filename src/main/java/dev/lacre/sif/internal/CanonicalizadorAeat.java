package dev.lacre.sif.internal;

import dev.lacre.shared.Huella;
import dev.lacre.sif.huella.Canonicalizador;
import dev.lacre.sif.registro.DatosRegistroAlta;
import dev.lacre.sif.registro.RegistroAnterior;
import dev.lacre.sif.registro.TipoFactura;

import java.time.OffsetDateTime;
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

    @Override
    public String canonicalizar(DatosRegistroAlta datos, Optional<RegistroAnterior> registroAnterior,
                                OffsetDateTime fechaHoraHusoGenRegistro) {

        StringJoiner cadena = new StringJoiner("&");
        cadena.add(campo("IDEmisorFactura", datos.idFactura().emisor().valor()));
        cadena.add(campo("NumSerieFactura", datos.idFactura().numSerieFactura()));
        cadena.add(campo("FechaExpedicionFactura", FormatosAeat.fecha(datos.idFactura().fechaExpedicion())));
        cadena.add(campo("TipoFactura", datos.tipoFactura().codigo()));
        cadena.add(campo("CuotaTotal", FormatosAeat.importe(datos.cuotaTotal())));
        cadena.add(campo("ImporteTotal", FormatosAeat.importe(datos.importeTotal())));
        cadena.add(campo("Huella", registroAnterior.map(anterior -> anterior.huella().valor()).orElse("")));
        cadena.add(campo("FechaHoraHusoGenRegistro", FormatosAeat.fechaHoraHuso(fechaHoraHusoGenRegistro)));
        return cadena.toString();
    }

    private static String campo(String nombre, String valor) {
        return nombre + "=" + valor.strip();
    }
}
