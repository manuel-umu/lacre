package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.ObligadoTributario;
import java.io.StringWriter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Envuelve los registros ya serializados en el mensaje SOAP {@code RegFactuSistemaFacturacion}.
 * Los fragmentos se insertan tal cual; la cabecera, que lleva datos variables, se escribe con
 * StAX.
 */
final class EscritorLote {

    static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String NS_LR = "https://www2.agenciatributaria.gob.es/static_files/common/"
            + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroLR.xsd";
    static final String NS_SF = "https://www2.agenciatributaria.gob.es/static_files/common/"
            + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";

    /** Tope que impone la AEAT por envío. */
    static final int MAXIMO_REGISTROS_POR_ENVIO = 1000;

    private EscritorLote() {}

    static String envolver(ObligadoTributario obligado, List<String> registros) {
        if (registros.isEmpty()) {
            throw new IllegalArgumentException("Un envío sin registros no tiene nada que remitir");
        }
        if (registros.size() > MAXIMO_REGISTROS_POR_ENVIO) {
            throw new IllegalArgumentException("La AEAT admite " + MAXIMO_REGISTROS_POR_ENVIO
                    + " registros por envío y se han pasado " + registros.size());
        }

        StringBuilder mensaje = new StringBuilder(1024 + registros.size() * 4096);
        mensaje.append("<soapenv:Envelope xmlns:soapenv=\"")
                .append(NS_SOAP)
                .append("\" xmlns:sfLR=\"")
                .append(NS_LR)
                .append("\">")
                .append("<soapenv:Header/><soapenv:Body>")
                .append("<sfLR:RegFactuSistemaFacturacion>")
                .append(cabecera(obligado));
        for (String registro : registros) {
            mensaje.append("<sfLR:RegistroFactura>").append(registro).append("</sfLR:RegistroFactura>");
        }
        return mensaje.append("</sfLR:RegFactuSistemaFacturacion>")
                .append("</soapenv:Body></soapenv:Envelope>")
                .toString();
    }

    /** Cabecera con el obligado de emisión. Omite {@code RemisionVoluntaria}, que es opcional. */
    private static String cabecera(ObligadoTributario obligado) {
        StringWriter destino = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);
            xml.setPrefix("sfLR", NS_LR);
            xml.setPrefix("sf", NS_SF);

            xml.writeStartElement(NS_LR, "Cabecera");
            xml.writeNamespace("sfLR", NS_LR);
            xml.writeNamespace("sf", NS_SF);

            xml.writeStartElement(NS_SF, "ObligadoEmision");
            escribir(xml, "NombreRazon", obligado.nombreRazon());
            escribir(xml, "NIF", obligado.nif().valor());
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("No se pudo escribir la cabecera del envío", e);
        }
        return destino.toString();
    }

    private static void escribir(XMLStreamWriter xml, String elemento, String valor) throws XMLStreamException {
        xml.writeStartElement(NS_SF, elemento);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}
