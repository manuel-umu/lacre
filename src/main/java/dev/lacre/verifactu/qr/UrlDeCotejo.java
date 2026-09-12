package dev.lacre.verifactu.qr;

import dev.lacre.verifactu.internal.FormatosAeat;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL del servicio de cotejo que va dentro del código QR de la factura, según los arts. 20 y 21
 * de la OM HAC/1177/2024. Lleva cuatro parámetros en orden fijo, cada uno codificado en UTF-8.
 */
public final class UrlDeCotejo {

    private UrlDeCotejo() {
    }

    /**
     * @param urlBase del servicio de cotejo, terminada en {@code ?}; distingue producción de
     *                pruebas y es configuración del despliegue
     */
    public static String de(String urlBase, DatosRegistroAlta alta) {
        IdFactura factura = alta.idFactura();
        return urlBase
                + "nif=" + codificar(factura.emisor().valor())
                + "&numserie=" + codificar(factura.numSerieFactura())
                + "&fecha=" + codificar(FormatosAeat.fecha(factura.fechaExpedicion()))
                + "&importe=" + codificar(FormatosAeat.importe(alta.importeTotal()));
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
